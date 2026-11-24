package com.medidiary.app.data.dgda

import android.content.Context
import androidx.room.withTransaction
import com.medidiary.app.data.db.AppDatabase
import com.medidiary.app.data.db.DgdaAlias
import com.medidiary.app.data.db.DgdaMedicine

/**
 * Loads the DGDA reference subset from `assets/dgda/ CSV files` into Room. Re-seeds whenever
 * [SEED_VERSION] is bumped, so Dev 2 can edit the CSVs without writing a Room migration.
 * The CSV files are plain text on purpose: easy to review against the DGDA registry.
 */
object DgdaSeeder {
    /** Bump after editing assets/dgda/ CSV files */
    const val SEED_VERSION = 1
    private const val PREFS = "dgda_seed"

    suspend fun seedIfNeeded(context: Context, db: AppDatabase) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val dao = db.dgdaDao()
        if (prefs.getInt("version", 0) == SEED_VERSION && dao.count() > 0) return

        val meds = readCsv(context, "dgda/dgda_medicines.csv").mapNotNull { row ->
            val id = row.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            DgdaMedicine(id, row[1].trim(), row[2].trim(), row.getOrNull(3)?.trim()?.ifEmpty { null })
        }
        val aliases = readCsv(context, "dgda/dgda_aliases.csv").mapNotNull { row ->
            val id = row.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            DgdaAlias(row[0].trim(), id)
        }
        db.withTransaction {
            dao.clearAliases()
            dao.clearMedicines()
            dao.insertMedicines(meds)
            dao.insertAliases(aliases)
        }
        prefs.edit().putInt("version", SEED_VERSION).apply()
    }

    /** Minimal CSV reader (no quoted commas in the reference files); skips header + blank lines. */
    private fun readCsv(context: Context, path: String): List<List<String>> =
        context.assets.open(path).bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.drop(1).map { it.removePrefix("﻿") }
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .map { it.split(',') }
                .toList()
        }
}
