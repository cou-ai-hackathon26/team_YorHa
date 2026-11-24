package com.medidiary.app.data

import androidx.room.withTransaction
import com.medidiary.app.data.db.AppDatabase
import com.medidiary.app.data.db.DiaryRecord
import com.medidiary.app.data.db.DiarySession
import com.medidiary.app.domain.ProposedRecord
import com.medidiary.app.sync.MergePolicy
import com.medidiary.app.sync.SharedRecord
import com.medidiary.app.sync.SyncIdentity
import com.medidiary.app.sync.SyncState
import kotlinx.coroutines.flow.Flow

/**
 * @param identity who this phone is in the shared diary (family sharing, Task 1).
 * @param onLocalChange called after every local write so sync can upload it.
 */
class DiaryRepository(
    private val db: AppDatabase,
    private val identity: () -> SyncIdentity = { SyncIdentity("local", "", sharing = false) },
    private val onLocalChange: () -> Unit = {},
) {
    private val dao = db.diaryDao()

    private fun stateForWrite(id: SyncIdentity) = if (id.sharing) SyncState.PENDING else SyncState.LOCAL_ONLY

    /**
     * The ONLY path from AI proposals to storage: called from the Review screen's
     * "Confirm & Save" button with the records the user left checked (core rule #2).
     * Only these verified records are ever shared; the transcript stays on this phone.
     */
    suspend fun saveVerified(transcript: String, records: List<ProposedRecord>, now: Long = System.currentTimeMillis()): Long {
        val confirmed = records.filter { it.checked && it.name.isNotBlank() }
        require(confirmed.isNotEmpty()) { "Nothing to save" }
        val id = identity()
        val session = DiarySession(timestamp = now, rawTranscript = transcript, isVerified = true)
        val rows = confirmed.map {
            DiaryRecord(
                sessionId = 0,
                type = it.type,
                originalName = it.name.trim(),
                genericSuggestion = it.genericSuggestion,
                genericMatchStatus = it.genericMatchStatus,
                timePhrase = it.timePhrase?.trim()?.ifEmpty { null },
                note = it.note?.trim()?.ifEmpty { null },
                userConfirmed = true,
                loggedDate = now,
                updatedAt = now,
                updatedBy = id.deviceId,
                authorName = id.myName.ifBlank { null },
                authorDevice = id.deviceId,
                syncState = stateForWrite(id),
            )
        }
        return dao.insertVerifiedSession(session, rows).also { onLocalChange() }
    }

    fun observe(from: Long, to: Long): Flow<List<DiaryRecord>> = dao.observeRecords(from, to)

    suspend fun between(from: Long, to: Long): List<DiaryRecord> = dao.recordsBetween(from, to)

    suspend fun medicineNames(): List<String> = dao.medicineNames()

    suspend fun update(record: DiaryRecord) {
        val id = identity()
        dao.updateRecord(
            record.copy(updatedAt = maxOf(System.currentTimeMillis(), record.updatedAt + 1), updatedBy = id.deviceId, syncState = stateForWrite(id)),
        )
        onLocalChange()
    }

    suspend fun delete(record: DiaryRecord) {
        val id = identity()
        if (id.sharing) {
            // Tombstone, so the delete reaches the other phone too.
            dao.updateRecord(
                record.copy(
                    deleted = true, updatedAt = maxOf(System.currentTimeMillis(), record.updatedAt + 1),
                    updatedBy = id.deviceId, syncState = SyncState.PENDING,
                ),
            )
            onLocalChange()
        } else {
            dao.deleteRecord(record)
            dao.deleteEmptySessions()
        }
    }

    /** Returns number of records removed. Local to this phone: retention deletes are not shared. */
    suspend fun applyRetention(days: Int, now: Long = System.currentTimeMillis()): Int {
        if (days <= 0) return 0
        val removed = dao.deleteRecordsOlderThan(now - days * 24L * 60 * 60 * 1000)
        dao.deleteEmptySessions()
        return removed
    }

    suspend fun deleteAll() = dao.deleteAll()

    // ---- Family sharing --------------------------------------------------------------------

    /** Records waiting to be uploaded, in their shared form. */
    suspend fun pendingShared(): List<SharedRecord> = dao.pendingRecords().map { r ->
        val session = dao.sessionById(r.sessionId)
        SharedRecord(
            uuid = r.uuid,
            sessionUuid = session?.uuid ?: "session-${r.uuid}",
            sessionTimestamp = session?.timestamp ?: r.loggedDate,
            type = r.type,
            originalName = r.originalName,
            genericSuggestion = r.genericSuggestion,
            genericMatchStatus = r.genericMatchStatus,
            timePhrase = r.timePhrase,
            note = r.note,
            loggedDate = r.loggedDate,
            authorName = r.authorName,
            authorDevice = r.authorDevice,
            updatedAt = r.updatedAt,
            updatedBy = r.updatedBy.orEmpty(),
            deleted = r.deleted,
        )
    }

    suspend fun markSynced(uuid: String, updatedAt: Long) = dao.markSynced(uuid, updatedAt)

    /** Starting or joining a shared diary: upload everything already on this phone. */
    /** Called when sync starts: give this phone's unnamed, already-shared entries its name. */
    suspend fun fillMissingAuthor() {
        val id = identity()
        if (id.sharing && id.myName.isNotBlank()) dao.stampAuthorAndResend(id.myName, id.deviceId)
    }

    suspend fun markAllPending() = db.withTransaction {
        val id = identity()
        if (id.myName.isNotBlank()) dao.stampAuthor(id.myName, id.deviceId)
        dao.markAllPending()
    }

    /** Leaving: keep the entries on this phone, forget the sync bookkeeping. */
    suspend fun onLeftSharing() = db.withTransaction {
        dao.purgeTombstones()
        dao.deleteEmptySessions()
        dao.markAllLocalOnly()
    }

    /**
     * Merges one record from the other phone (last writer wins). Records older than this
     * phone's retention window are ignored so retention isn't undone by sync.
     * @return true if the local diary changed.
     */
    suspend fun applyRemote(remote: SharedRecord, retentionDays: Int, now: Long = System.currentTimeMillis()): Boolean =
        db.withTransaction {
            val local = dao.recordByUuid(remote.uuid)
            val localStamp = local?.let { MergePolicy.Stamp(it.updatedAt, it.updatedBy.orEmpty()) }
            if (!MergePolicy.remoteWins(localStamp, MergePolicy.Stamp(remote.updatedAt, remote.updatedBy))) return@withTransaction false
            if (local == null && remote.deleted) return@withTransaction false
            if (local == null && retentionDays > 0 && remote.loggedDate < now - retentionDays * 24L * 60 * 60 * 1000) {
                return@withTransaction false
            }
            val sessionId = local?.sessionId
                ?: dao.sessionByUuid(remote.sessionUuid)?.sessionId
                ?: dao.insertSession(
                    DiarySession(timestamp = remote.sessionTimestamp, rawTranscript = "", isVerified = true, uuid = remote.sessionUuid),
                )
            val row = DiaryRecord(
                id = local?.id ?: 0,
                sessionId = sessionId,
                type = remote.type,
                originalName = remote.originalName,
                genericSuggestion = remote.genericSuggestion,
                genericMatchStatus = remote.genericMatchStatus,
                timePhrase = remote.timePhrase,
                note = remote.note,
                userConfirmed = true,
                loggedDate = remote.loggedDate,
                uuid = remote.uuid,
                updatedAt = remote.updatedAt,
                updatedBy = remote.updatedBy,
                deleted = remote.deleted,
                authorName = remote.authorName,
                authorDevice = remote.authorDevice,
                syncState = SyncState.SYNCED,
            )
            if (local == null) dao.insertRecord(row) else dao.updateRecord(row)
            true
        }
}
