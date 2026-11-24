package com.medidiary.app.sync

import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/*
 * Family sharing (hackathon Task 1): the parts that need no Android or Firebase, so they are
 * JVM-tested. The server only ever stores [SyncCrypto] ciphertext plus the merge stamp.
 */

/** Local sync bookkeeping for a diary row. */
object SyncState {
    const val LOCAL_ONLY = "LOCAL_ONLY"
    const val PENDING = "PENDING"
    const val SYNCED = "SYNCED"
}

/** Who this phone is in the shared diary; read by DiaryRepository on every write. */
data class SyncIdentity(val deviceId: String, val myName: String, val sharing: Boolean)

/** AES-256-GCM with a random 96-bit IV. Output = base64(iv ‖ ciphertext ‖ tag). */
object SyncCrypto {
    private const val IV_BYTES = 12
    private val random = SecureRandom()

    fun newKey(): ByteArray = ByteArray(32).also(random::nextBytes)

    fun encrypt(key: ByteArray, plaintext: String, aad: String): String {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        c.updateAAD(aad.toByteArray())
        return Base64.getEncoder().encodeToString(iv + c.doFinal(plaintext.toByteArray()))
    }

    /** Throws if the key is wrong or the data/[aad] was tampered with. */
    fun decrypt(key: ByteArray, encoded: String, aad: String): String {
        val all = Base64.getDecoder().decode(encoded)
        require(all.size > IV_BYTES + 16) { "Ciphertext too short" }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, all, 0, IV_BYTES))
        c.updateAAD(aad.toByteArray())
        return String(c.doFinal(all, IV_BYTES, all.size - IV_BYTES))
    }
}

/**
 * Invite = diary id + key + a label, as `medidiary://join?d=…&k=…&n=…`. Shown as a QR code
 * or shared as text. Anyone holding it can read and write the diary: treat it like a password.
 */
data class Invite(val diaryId: String, val key: ByteArray, val label: String) {
    fun toLink(): String =
        "$SCHEME://join?d=${enc(diaryId)}&k=${Base64.getUrlEncoder().withoutPadding().encodeToString(key)}&n=${enc(label)}"

    override fun equals(other: Any?) =
        other is Invite && other.diaryId == diaryId && other.key.contentEquals(key) && other.label == label

    override fun hashCode() = diaryId.hashCode()

    companion object {
        const val SCHEME = "medidiary"
        private val ID = Regex("^[A-Za-z0-9-]{8,64}$")

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

        /** Accepts the bare link or a shared message that contains it. Null if it isn't a valid invite. */
        fun parse(text: String): Invite? {
            val link = Regex("$SCHEME://join\\?\\S+").find(text.trim())?.value ?: return null
            val params = link.substringAfter('?').split('&').mapNotNull {
                val i = it.indexOf('=')
                if (i <= 0) null else it.substring(0, i) to URLDecoder.decode(it.substring(i + 1), "UTF-8")
            }.toMap()
            val id = params["d"]?.takeIf { ID.matches(it) } ?: return null
            val key = runCatching { Base64.getUrlDecoder().decode(params["k"].orEmpty()) }.getOrNull()
                ?.takeIf { it.size == 32 } ?: return null
            return Invite(id, key, params["n"].orEmpty().take(60))
        }
    }
}

/** One diary record as shared between phones (no local ids, no transcript, no audio). */
data class SharedRecord(
    val uuid: String,
    val sessionUuid: String,
    val sessionTimestamp: Long,
    val type: String,
    val originalName: String,
    val genericSuggestion: String?,
    val genericMatchStatus: String,
    val timePhrase: String?,
    val note: String?,
    val loggedDate: Long,
    val authorName: String?,
    val authorDevice: String?,
    val updatedAt: Long,
    val updatedBy: String,
    val deleted: Boolean,
) {
    fun toJson(): String = buildJsonObject {
        put("uuid", uuid); put("sessionUuid", sessionUuid); put("sessionTimestamp", sessionTimestamp)
        put("type", type); put("originalName", originalName); put("genericSuggestion", genericSuggestion)
        put("genericMatchStatus", genericMatchStatus); put("timePhrase", timePhrase); put("note", note)
        put("loggedDate", loggedDate); put("authorName", authorName); put("authorDevice", authorDevice)
        put("updatedAt", updatedAt); put("updatedBy", updatedBy); put("deleted", deleted)
    }.toString()

    companion object {
        fun fromJson(json: String): SharedRecord {
            val o: JsonObject = Json.parseToJsonElement(json).jsonObject
            fun str(k: String): String? = (o[k] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull
            fun req(k: String): String = str(k) ?: error("Missing $k")
            fun long(k: String): Long = o[k]?.jsonPrimitive?.longOrNull ?: error("Missing $k")
            return SharedRecord(
                uuid = req("uuid"), sessionUuid = req("sessionUuid"), sessionTimestamp = long("sessionTimestamp"),
                type = req("type"), originalName = req("originalName"), genericSuggestion = str("genericSuggestion"),
                genericMatchStatus = str("genericMatchStatus") ?: "UNMATCHED", timePhrase = str("timePhrase"),
                note = str("note"), loggedDate = long("loggedDate"), authorName = str("authorName"),
                authorDevice = str("authorDevice"), updatedAt = long("updatedAt"), updatedBy = str("updatedBy").orEmpty(),
                deleted = o["deleted"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
        }
    }
}

/** What is stored per record on the server: only the merge stamp is readable. */
data class Envelope(val updatedAt: Long, val updatedBy: String, val cipher: String) {
    companion object {
        fun seal(key: ByteArray, diaryId: String, r: SharedRecord) =
            Envelope(r.updatedAt, r.updatedBy, SyncCrypto.encrypt(key, r.toJson(), aad(diaryId, r.uuid)))

        /** The doc id is bound into the ciphertext, so a record can't be swapped into another slot. */
        fun open(key: ByteArray, diaryId: String, docId: String, e: Envelope): SharedRecord {
            val r = SharedRecord.fromJson(SyncCrypto.decrypt(key, e.cipher, aad(diaryId, docId)))
            require(r.uuid == docId) { "Record id mismatch" }
            return r
        }

        private fun aad(diaryId: String, uuid: String) = "medidiary/v1/$diaryId/$uuid"
    }
}

/** Last writer wins, per record. Equal times are broken by device id so both phones agree. */
object MergePolicy {
    data class Stamp(val updatedAt: Long, val deviceId: String)

    fun remoteWins(local: Stamp?, remote: Stamp): Boolean = when {
        local == null -> true
        remote.updatedAt != local.updatedAt -> remote.updatedAt > local.updatedAt
        else -> remote.deviceId > local.deviceId
    }
}
