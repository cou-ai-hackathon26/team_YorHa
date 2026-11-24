package com.medidiary.app.export

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Plain row for exports so formatting is independent of Room and JVM-testable. */
data class ExportRow(
    val loggedDate: Long,
    val type: String,
    val name: String,
    val genericSuggestion: String?,
    val genericMatchStatus: String,
    val timePhrase: String?,
    val note: String?,
)

object CsvExporter {
    const val DISCLAIMER = "Patient-Reported & AI-Assisted, Not Medically Verified."
    private val HEADER = listOf(
        "Date", "Time Logged", "Time Reported", "Entry Type", "Reported Name",
        "Generic Match (DGDA)", "Match Status", "Notes",
    )

    /** UTF-8 with BOM so Excel renders Bangla correctly. */
    fun build(rows: List<ExportRow>, patientName: String?): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val time = SimpleDateFormat("HH:mm", Locale.US)
        val sb = StringBuilder("﻿")
        sb.append(csvLine(listOf("# MediDiary — Patient Reported Health Log"))).append("\r\n")
        sb.append(csvLine(listOf("# $DISCLAIMER"))).append("\r\n")
        if (!patientName.isNullOrBlank()) sb.append(csvLine(listOf("# Patient: $patientName"))).append("\r\n")
        sb.append(csvLine(HEADER)).append("\r\n")
        rows.sortedBy { it.loggedDate }.forEach { r ->
            val d = Date(r.loggedDate)
            sb.append(
                csvLine(
                    listOf(
                        date.format(d), time.format(d), r.timePhrase.orEmpty(),
                        r.type.replaceFirstChar { it.uppercase() }, r.name,
                        r.genericSuggestion.orEmpty(), r.genericMatchStatus, r.note.orEmpty(),
                    ),
                ),
            ).append("\r\n")
        }
        return sb.toString()
    }

    internal fun csvLine(fields: List<String>): String = fields.joinToString(",") { escape(it) }

    internal fun escape(field: String): String {
        // Neutralise spreadsheet formula injection from free text.
        val safe = if (field.isNotEmpty() && field[0] in "=+-@\t\r") "'$field" else field
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else safe
    }
}
