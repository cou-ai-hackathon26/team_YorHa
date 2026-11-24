package com.medidiary.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {
    @Insert
    suspend fun insertSession(session: DiarySession): Long

    @Insert
    suspend fun insertRecords(records: List<DiaryRecord>)

    @Transaction
    suspend fun insertVerifiedSession(session: DiarySession, records: List<DiaryRecord>): Long {
        val id = insertSession(session)
        insertRecords(records.map { it.copy(sessionId = id) })
        return id
    }

    @Update
    suspend fun updateRecord(record: DiaryRecord)

    @Delete
    suspend fun deleteRecord(record: DiaryRecord)

    @Query("SELECT * FROM diary_records WHERE deleted = 0 AND loggedDate BETWEEN :from AND :to ORDER BY loggedDate DESC")
    fun observeRecords(from: Long, to: Long): Flow<List<DiaryRecord>>

    @Query("SELECT * FROM diary_records WHERE deleted = 0 AND loggedDate BETWEEN :from AND :to ORDER BY loggedDate ASC")
    suspend fun recordsBetween(from: Long, to: Long): List<DiaryRecord>

    /** The user's own medicine vocabulary (Task 2: a name saved before is never rejected). */
    @Query("SELECT DISTINCT originalName FROM diary_records WHERE type = 'medicine' AND deleted = 0")
    suspend fun medicineNames(): List<String>

    @Query("DELETE FROM diary_records WHERE loggedDate < :cutoff")
    suspend fun deleteRecordsOlderThan(cutoff: Long): Int

    /** Sessions left with no records (all deleted by the user or retention) are removed too. */
    @Query("DELETE FROM diary_sessions WHERE sessionId NOT IN (SELECT DISTINCT sessionId FROM diary_records)")
    suspend fun deleteEmptySessions(): Int

    @Query("DELETE FROM diary_sessions")
    suspend fun deleteAll()

    // ---- Family sharing --------------------------------------------------------------------

    @Insert
    suspend fun insertRecord(record: DiaryRecord): Long

    @Query("SELECT * FROM diary_records WHERE uuid = :uuid LIMIT 1")
    suspend fun recordByUuid(uuid: String): DiaryRecord?

    @Query("SELECT * FROM diary_sessions WHERE sessionId = :id LIMIT 1")
    suspend fun sessionById(id: Long): DiarySession?

    @Query("SELECT * FROM diary_sessions WHERE uuid = :uuid LIMIT 1")
    suspend fun sessionByUuid(uuid: String): DiarySession?

    @Query("SELECT * FROM diary_records WHERE syncState = 'PENDING' ORDER BY updatedAt")
    suspend fun pendingRecords(): List<DiaryRecord>

    /** Only if nothing changed the row while it was being uploaded. */
    @Query("UPDATE diary_records SET syncState = 'SYNCED' WHERE uuid = :uuid AND updatedAt = :updatedAt AND syncState = 'PENDING'")
    suspend fun markSynced(uuid: String, updatedAt: Long)

    @Query("UPDATE diary_records SET syncState = 'PENDING' WHERE syncState = 'LOCAL_ONLY'")
    suspend fun markAllPending()

    /** Entries made on this phone before it had a family name get one (and an author device). */
    @Query(
        "UPDATE diary_records SET authorName = :name, authorDevice = :device " +
            "WHERE authorName IS NULL AND (authorDevice IS NULL OR authorDevice = :device)",
    )
    suspend fun stampAuthor(name: String, device: String)

    /** Same, for entries already shared without a name: re-upload them with it (newer stamp wins). */
    @Query(
        "UPDATE diary_records SET authorName = :name, authorDevice = :device, updatedAt = updatedAt + 1, " +
            "updatedBy = :device, syncState = 'PENDING' " +
            "WHERE syncState = 'SYNCED' AND authorName IS NULL AND (authorDevice IS NULL OR authorDevice = :device)",
    )
    suspend fun stampAuthorAndResend(name: String, device: String): Int

    @Query("UPDATE diary_records SET syncState = 'LOCAL_ONLY'")
    suspend fun markAllLocalOnly()

    @Query("DELETE FROM diary_records WHERE deleted = 1")
    suspend fun purgeTombstones()
}

@Dao
interface DgdaDao {
    @Query("SELECT * FROM dgda_medicines WHERE lower(brand_name) = :normalized LIMIT 1")
    suspend fun findByBrandExact(normalized: String): DgdaMedicine?

    @Query("SELECT * FROM dgda_medicines WHERE lower(brand_name) LIKE :pattern ORDER BY length(brand_name) LIMIT 5")
    suspend fun findByBrandLike(pattern: String): List<DgdaMedicine>

    @Query("SELECT * FROM dgda_medicines")
    suspend fun all(): List<DgdaMedicine>

    @Query("SELECT * FROM dgda_aliases")
    suspend fun allAliases(): List<DgdaAlias>

    @Query("SELECT COUNT(*) FROM dgda_medicines")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicines(items: List<DgdaMedicine>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAliases(items: List<DgdaAlias>)

    @Query("DELETE FROM dgda_medicines")
    suspend fun clearMedicines()

    @Query("DELETE FROM dgda_aliases")
    suspend fun clearAliases()
}
