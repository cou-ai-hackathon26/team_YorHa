package com.medidiary.app.sync

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.ListenerRegistration
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.medidiary.app.BuildConfig
import com.medidiary.app.MediDiaryApp
import com.medidiary.app.data.DiaryRepository
import com.medidiary.app.data.SettingsStore
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** `family` flavor: encrypted Firestore sync. */
object FamilySyncFactory {
    fun create(app: Application, repo: DiaryRepository, prefs: FamilyPrefs, settings: SettingsStore): FamilySync =
        FirestoreFamilySync(app, repo, prefs, settings)
}

/**
 * Firestore layout (all content is AES-GCM ciphertext, see [Envelope]):
 *
 *   diaries/{diaryId}/members/{firebaseUid}  { j: joinedAt }
 *   diaries/{diaryId}/records/{recordUuid}   { v: 1, u: updatedAt, dev: deviceId, c: ciphertext }
 *
 * Security rules (firebase/firestore.rules) only let members read/write a diary. Each phone signs
 * in anonymously; knowing the random diary id + key (the invite) is what lets you join.
 *
 * Push: local writes mark rows PENDING → uploaded here. Pull: a realtime listener while the app
 * runs, plus [FamilySyncWorker] every 15 min in the background. Firestore's offline cache queues
 * writes made without network.
 */
class FirestoreFamilySync(
    private val app: Application,
    private val repo: DiaryRepository,
    private val prefs: FamilyPrefs,
    private val settings: SettingsStore,
) : FamilySync {
    // Sync runs in the background: any failure is shown on the Settings card, never crashes the app.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            Log.w(TAG, "sync", e)
            refresh("Sync error: ${explain(e)}")
        },
    )
    private val secrets = SecretStore(app)
    private val pushLock = Mutex()
    private val firebase: FirebaseApp? = FirebaseBootstrap.init(app)
    private var listener: ListenerRegistration? = null
    private var retryJob: Job? = null

    override val supported = true
    override val pendingInvite = MutableStateFlow<String?>(null)

    private val _state = MutableStateFlow(snapshot())
    override val state: StateFlow<FamilyState> = _state.asStateFlow()

    private fun snapshot(error: String? = null, syncing: Boolean = false) = FamilyState(
        configured = firebase != null,
        enabled = prefs.enabled && prefs.diaryId != null && secrets.key() != null,
        diaryLabel = prefs.diaryLabel,
        myName = prefs.myName,
        deviceId = prefs.deviceId,
        lastSyncAt = prefs.lastSyncAt.takeIf { it > 0 },
        syncing = syncing,
        error = error,
        projectId = BuildConfig.FIREBASE_PROJECT_ID,
    )

    private fun refresh(error: String? = null, syncing: Boolean = false) {
        _state.value = snapshot(error, syncing)
    }

    private val firestore get() = FirebaseFirestore.getInstance(requireNotNull(firebase) { NOT_CONFIGURED })
    private val auth get() = FirebaseAuth.getInstance(requireNotNull(firebase) { NOT_CONFIGURED })

    private fun diaryRef(diaryId: String) = firestore.collection("diaries").document(diaryId)

    private fun active(): Pair<String, ByteArray>? {
        if (!prefs.enabled || firebase == null) return null
        val id = prefs.diaryId ?: return null
        val key = secrets.key() ?: return null
        return id to key
    }

    // ---- lifecycle -------------------------------------------------------------------------

    override fun start() {
        refresh()
        if (active() == null) return
        FamilySyncWorker.schedule(app)
        scope.launch {
            runCatching { ensureSignedIn() }.onFailure { refresh("Can't reach the sync service yet: ${it.message}") }
            repo.fillMissingAuthor()
            listen()
            push()
        }
    }

    override fun requestSync() {
        if (active() == null) return
        scope.launch { push() }
        FamilySyncWorker.pushSoon(app) // finishes the upload even if the app is closed / offline now
    }

    override fun syncNow() {
        if (active() == null) return
        scope.launch {
            refresh(syncing = true)
            push()
            pullOnce()
            listen() // re-attach the live listener in case it stopped
        }
    }

    private suspend fun ensureSignedIn() {
        if (auth.currentUser != null) return
        val result = withTimeoutOrNull(20_000) { runCatching { auth.signInAnonymously().await() } }
            ?: error("Couldn't reach Firebase sign-in in 20 s. Check this phone's internet connection.")
        result.exceptionOrNull()?.let { e ->
            Log.w(TAG, "anonymous sign-in", e)
            error(
                "Firebase sign-in failed: ${e.message}. In the Firebase console, open Authentication → " +
                    "Sign-in method and enable Anonymous.",
            )
        }
    }

    private suspend fun registerMember(diaryId: String) {
        ensureSignedIn()
        val uid = requireNotNull(auth.currentUser).uid
        val member = diaryRef(diaryId).collection("members").document(uid)
        // A server READ fails fast with the real reason (missing database, wrong project id, API
        // blocked, rules not published), whereas a write just waits in the offline queue.
        val probe = withTimeoutOrNull(20_000) { runCatching { member.get(Source.SERVER).await() } }
            ?: error("Can't reach Firestore for project '${BuildConfig.FIREBASE_PROJECT_ID}' in 20 s. Check the internet connection.")
        probe.exceptionOrNull()?.let { e ->
            Log.w(TAG, "firestore probe", e)
            error(explain(e))
        }
        // Membership must exist before rules let us read the records. Firestore keeps unconfirmed
        // writes in its local queue and delivers them later, so a slow confirmation is NOT fatal:
        // wait briefly, then carry on (the records listener retries until membership lands).
        // A real rejection (rules, project) still fails here, or later shows on the card.
        val write = member.set(mapOf("j" to System.currentTimeMillis()))
        write.addOnFailureListener { e ->
            Log.w(TAG, "membership write", e)
            refresh("The sync server rejected joining: ${explain(e)}")
        }
        val outcome = withTimeoutOrNull(10_000) { runCatching { write.await() } }
        outcome?.exceptionOrNull()?.let { error(explain(it)) }
        if (outcome == null) Log.i(TAG, "membership write not confirmed yet; continuing (queued by Firestore)")
    }

    /** Turns a Firestore failure into something the team can act on in the Firebase console. */
    private fun explain(e: Throwable): String {
        val project = BuildConfig.FIREBASE_PROJECT_ID
        val code = (e as? FirebaseFirestoreException)?.code
        return when (code) {
            FirebaseFirestoreException.Code.NOT_FOUND ->
                "No Firestore database in project '$project'. Firebase console → Build → Firestore Database → Create database " +
                    "(default database, production mode), then publish firebase/firestore.rules."
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "Firestore refused access in project '$project'. Publish firebase/firestore.rules (Firestore → Rules) and enable " +
                    "Anonymous sign-in. If the API key is restricted in Google Cloud, allow the Cloud Firestore API. (${e.message})"
            FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
                "Firestore isn't ready in project '$project': ${e.message}"
            FirebaseFirestoreException.Code.UNAVAILABLE ->
                "Firestore is unreachable from this network: ${e.message}"
            else -> "Firestore error in project '$project' (${code ?: e.javaClass.simpleName}): ${e.message}"
        }
    }

    // ---- create / join / leave ---------------------------------------------------------------

    override suspend fun create(myName: String, diaryLabel: String): String {
        check(firebase != null) { NOT_CONFIGURED }
        val diaryId = UUID.randomUUID().toString()
        val key = SyncCrypto.newKey()
        registerMember(diaryId)
        save(diaryId, key, diaryLabel.ifBlank { "Family diary" }, myName)
        return requireNotNull(inviteLink())
    }

    override suspend fun join(inviteText: String, myName: String) {
        check(firebase != null) { NOT_CONFIGURED }
        val invite = Invite.parse(inviteText) ?: error("That isn't a MediDiary invite. Ask for a new QR code or link.")
        registerMember(invite.diaryId)
        save(invite.diaryId, invite.key, invite.label.ifBlank { "Family diary" }, myName)
        pendingInvite.value = null
    }

    private suspend fun save(diaryId: String, key: ByteArray, label: String, myName: String) {
        secrets.saveKey(key)
        prefs.diaryId = diaryId
        prefs.diaryLabel = label
        prefs.myName = myName.trim()
        prefs.enabled = true
        repo.markAllPending() // share what's already on this phone
        start()
    }

    override suspend fun leave() {
        val diaryId = prefs.diaryId
        listener?.remove()
        listener = null
        retryJob?.cancel()
        FamilySyncWorker.cancel(app)
        if (diaryId != null && firebase != null) {
            runCatching {
                auth.currentUser?.uid?.let { uid ->
                    withTimeoutOrNull(5_000) { diaryRef(diaryId).collection("members").document(uid).delete().await() }
                }
            }
        }
        prefs.clearDiary()
        secrets.clear()
        repo.onLeftSharing()
        refresh()
    }

    override fun inviteLink(): String? {
        val (id, key) = active() ?: return null
        return Invite(id, key, prefs.diaryLabel).toLink()
    }

    override fun inviteQr(sizePx: Int): Bitmap? {
        val link = inviteLink() ?: return null
        val m = QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, sizePx, sizePx, mapOf(EncodeHintType.MARGIN to 1))
        val px = IntArray(m.width * m.height) { if (m.get(it % m.width, it / m.width)) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
    }

    override val scanContract: ActivityResultContract<Unit, String?> = object : ActivityResultContract<Unit, String?>() {
        private val scan = ScanContract()
        override fun createIntent(context: Context, input: Unit) = scan.createIntent(
            context,
            ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Scan the MediDiary invite QR")
                .setBeepEnabled(false).setOrientationLocked(false),
        )
        override fun parseResult(resultCode: Int, intent: android.content.Intent?): String? =
            scan.parseResult(resultCode, intent).contents
    }

    // ---- push / pull -----------------------------------------------------------------------

    /** Uploads PENDING rows. Each is marked SYNCED only once the server has it. */
    suspend fun push() = pushLock.withLock {
        val (diaryId, key) = active() ?: return@withLock
        val pending = repo.pendingShared()
        if (pending.isEmpty()) return@withLock
        refresh(syncing = true)
        var waiting = 0
        var rejected: Throwable? = null
        for (r in pending) {
            val e = Envelope.seal(key, diaryId, r)
            // Never let a server rejection escape this coroutine (it would crash the app).
            val outcome = withTimeoutOrNull(15_000) {
                runCatching {
                    diaryRef(diaryId).collection("records").document(r.uuid)
                        .set(mapOf("v" to 1L, "u" to e.updatedAt, "dev" to e.updatedBy, "c" to e.cipher)).await()
                }
            }
            when {
                outcome == null -> waiting++ // still in Firestore's queue; retried by the worker
                outcome.isSuccess -> repo.markSynced(r.uuid, r.updatedAt)
                else -> { rejected = outcome.exceptionOrNull(); Log.w(TAG, "record write", rejected) }
            }
        }
        if (waiting == 0 && rejected == null) prefs.lastSyncAt = System.currentTimeMillis()
        refresh(
            when {
                rejected != null -> "Upload rejected: ${explain(rejected!!)}"
                waiting > 0 -> "$waiting change(s) not confirmed by the server yet. They'll keep retrying automatically."
                else -> null
            },
        )
    }

    /** One-off full pull (background worker). */
    suspend fun pullOnce() {
        val (diaryId, key) = active() ?: return
        ensureSignedIn()
        val snap = runCatching { diaryRef(diaryId).collection("records").get().await() }
            .getOrElse { refresh("Download failed: ${explain(it)}"); throw it }
        merge(diaryId, key, snap.documents)
    }

    private fun listen() {
        val (diaryId, key) = active() ?: return
        listener?.remove()
        listener = diaryRef(diaryId).collection("records").addSnapshotListener { snap, err ->
            if (err != null) {
                Log.w(TAG, "listener", err)
                refresh("Sync paused: ${err.message}")
                // e.g. membership not on the server yet (joined offline): try again shortly.
                retryJob?.cancel()
                retryJob = scope.launch {
                    delay(30_000)
                    runCatching { active()?.let { registerMember(it.first) } }
                    listen()
                }
                return@addSnapshotListener
            }
            val docs = snap?.documentChanges.orEmpty().filter { it.type != DocumentChange.Type.REMOVED }.map { it.document }
            scope.launch { merge(diaryId, key, docs) }
        }
    }

    private suspend fun merge(diaryId: String, key: ByteArray, docs: List<DocumentSnapshot>) {
        var unreadable = 0
        val retention = settings.settings.value.retentionDays
        for (d in docs) {
            val e = Envelope(
                updatedAt = d.getLong("u") ?: continue,
                updatedBy = d.getString("dev").orEmpty(),
                cipher = d.getString("c") ?: continue,
            )
            val r = runCatching { Envelope.open(key, diaryId, d.id, e) }.getOrNull()
            if (r == null) { unreadable++; continue }
            repo.applyRemote(r, retention)
        }
        prefs.lastSyncAt = System.currentTimeMillis()
        refresh(if (unreadable > 0) "$unreadable shared entr(ies) couldn't be decrypted (made with a different invite)." else null)
    }

    private companion object {
        const val TAG = "FamilySync"
        const val NOT_CONFIGURED = "Family sharing isn't set up in this build (Firebase keys missing). See docs/FAMILY_SYNC_SETUP.md."
    }
}

/** Builds Firebase from BuildConfig (filled from google-services.json or env at build time). */
private object FirebaseBootstrap {
    fun init(context: Context): FirebaseApp? {
        if (BuildConfig.FIREBASE_API_KEY.isBlank() || BuildConfig.FIREBASE_APP_ID.isBlank() || BuildConfig.FIREBASE_PROJECT_ID.isBlank()) {
            return null
        }
        return runCatching {
            FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .build(),
            )
        }.onFailure { Log.e("FamilySync", "Firebase init failed", it) }.getOrNull()
    }
}

/** The diary key, encrypted at rest with a non-exportable Android Keystore key. */
private class SecretStore(context: Context) {
    private val prefs = context.getSharedPreferences("family_secret", Context.MODE_PRIVATE)
    @Volatile private var cached: ByteArray? = null

    fun key(): ByteArray? = cached ?: runCatching {
        val stored = prefs.getString("k", null) ?: return null
        val all = Base64.decode(stored, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, all, 0, 12))
        c.doFinal(all, 12, all.size - 12)
    }.getOrNull()?.also { cached = it }

    fun saveKey(key: ByteArray) {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, wrappingKey())
        prefs.edit().putString("k", Base64.encodeToString(c.iv + c.doFinal(key), Base64.NO_WRAP)).apply()
        cached = key
    }

    fun clear() {
        prefs.edit().clear().apply()
        cached = null
    }

    private fun wrappingKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val ALIAS = "medidiary_family_key"
    }
}

/** Background sync: uploads pending changes and pulls the other phone's, when there is network. */
class FamilySyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val sync = (applicationContext as MediDiaryApp).container.familySync as? FirestoreFamilySync ?: return Result.success()
        return runCatching {
            sync.push()
            sync.pullOnce()
        }.fold({ Result.success() }, { Result.retry() })
    }

    companion object {
        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<FamilySyncWorker>(15, TimeUnit.MINUTES).setConstraints(network).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("family-sync", ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun pushSoon(context: Context) {
            val req = OneTimeWorkRequestBuilder<FamilySyncWorker>().setConstraints(network).build()
            WorkManager.getInstance(context).enqueueUniqueWork("family-sync-now", ExistingWorkPolicy.REPLACE, req)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork("family-sync")
            WorkManager.getInstance(context).cancelUniqueWork("family-sync-now")
        }
    }
}
