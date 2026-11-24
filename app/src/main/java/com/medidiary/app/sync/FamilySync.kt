package com.medidiary.app.sync

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.result.contract.ActivityResultContract
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the Settings "Family sharing" card shows. */
data class FamilyState(
    /** False when this build has no sync backend configured (Firebase keys missing). */
    val configured: Boolean = false,
    val enabled: Boolean = false,
    val diaryLabel: String = "",
    val myName: String = "",
    val deviceId: String = "",
    val lastSyncAt: Long? = null,
    val syncing: Boolean = false,
    val error: String? = null,
    /** Firebase project the build points at (shown so it can be compared with the console). */
    val projectId: String = "",
)

/**
 * Family sharing (hackathon Task 1). One shared diary for a parent and caregivers, each on their
 * own phone. The `offline` flavor ships a disabled implementation (no network at all); the
 * `family` flavor syncs end-to-end-encrypted, user-verified records through Firebase.
 */
interface FamilySync {
    /** False in the offline build: the Settings card is hidden. */
    val supported: Boolean
    val state: StateFlow<FamilyState>

    /** An invite opened from a link or QR code, waiting for the user to confirm joining. */
    val pendingInvite: MutableStateFlow<String?>

    /** Called once at app start. */
    fun start()

    /** Upload local changes soon. Cheap; safe to call often. */
    fun requestSync()

    /** "Sync now" button: upload AND download everything right away. */
    fun syncNow() = requestSync()

    /** Creates a new shared diary from this phone and returns its invite link. */
    suspend fun create(myName: String, diaryLabel: String): String

    /** Joins the diary in [inviteText] (a link, or a message containing one). */
    suspend fun join(inviteText: String, myName: String)

    /** Stops sharing on this phone. Entries already here stay here. */
    suspend fun leave()

    fun inviteLink(): String?

    /** QR code for [inviteLink], or null when unsupported. */
    fun inviteQr(sizePx: Int): Bitmap? = null

    /** Camera QR scanner returning the scanned text, or null when unsupported. */
    val scanContract: ActivityResultContract<Unit, String?>? get() = null
}

/** Non-secret family-sharing settings. The diary key lives in the family flavor's Keystore-backed store. */
class FamilyPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("family", Context.MODE_PRIVATE)

    val deviceId: String = prefs.getString("deviceId", null)
        ?: UUID.randomUUID().toString().also { prefs.edit().putString("deviceId", it).apply() }

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(v) = prefs.edit().putBoolean("enabled", v).apply()
    var diaryId: String?
        get() = prefs.getString("diaryId", null)
        set(v) = prefs.edit().putString("diaryId", v).apply()
    var diaryLabel: String
        get() = prefs.getString("diaryLabel", "") ?: ""
        set(v) = prefs.edit().putString("diaryLabel", v).apply()
    var myName: String
        get() = prefs.getString("myName", "") ?: ""
        set(v) = prefs.edit().putString("myName", v).apply()
    var lastSyncAt: Long
        get() = prefs.getLong("lastSyncAt", 0L)
        set(v) = prefs.edit().putLong("lastSyncAt", v).apply()

    fun identity() = SyncIdentity(deviceId, myName, sharing = enabled && diaryId != null)

    fun clearDiary() = prefs.edit().remove("enabled").remove("diaryId").remove("diaryLabel").remove("lastSyncAt").apply()
}

/** Offline build: no backend, nothing leaves the phone. */
class DisabledFamilySync(prefs: FamilyPrefs) : FamilySync {
    override val supported = false
    override val state: StateFlow<FamilyState> = MutableStateFlow(FamilyState(deviceId = prefs.deviceId))
    override val pendingInvite = MutableStateFlow<String?>(null)
    override fun start() = Unit
    override fun requestSync() = Unit
    override suspend fun create(myName: String, diaryLabel: String): String = error("Family sharing isn't in this build.")
    override suspend fun join(inviteText: String, myName: String) = error("Family sharing isn't in this build.")
    override suspend fun leave() = Unit
    override fun inviteLink(): String? = null
}
