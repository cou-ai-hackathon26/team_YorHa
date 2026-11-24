package com.medidiary.app.data.dgda

import com.medidiary.app.data.db.AppDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * CLAUDE.md §6. The reference table is small (hundreds of rows), so it is loaded once into a
 * [DgdaMatcher] that adds Bangla-alias, strength-stripping and edit-distance matching on top of
 * the spec's exact + prefix (LIKE) lookup.
 */
class DgdaLookupRepository(private val db: AppDatabase) {
    private val mutex = Mutex()
    @Volatile private var matcher: DgdaMatcher? = null

    /** Spec signature: (generic or null, "EXACT" | "FUZZY" | "UNMATCHED"). */
    suspend fun resolveGeneric(brandSpoken: String): Pair<String?, String> {
        val m = match(brandSpoken)
        return m.genericName to m.status
    }

    suspend fun match(brandSpoken: String): GenericMatch = loadMatcher().match(brandSpoken)

    private suspend fun loadMatcher(): DgdaMatcher = matcher ?: mutex.withLock {
        matcher ?: run {
            val dao = db.dgdaDao()
            val entries = dao.all().map { DgdaEntry(it.id, it.brandName, it.genericName, it.dosageForm) }
            val aliases = dao.allAliases().associate { it.alias to it.medicineId }
            DgdaMatcher(entries, aliases).also { matcher = it }
        }
    }

    /** Task 2: checks spoken names against DGDA + the names already saved in this diary. */
    suspend fun nameGate(history: Collection<String>): MedicineNameGate =
        MedicineNameGate(loadMatcher().vocabulary(), history)

    /** Call after re-seeding. */
    fun invalidate() {
        matcher = null
    }
}
