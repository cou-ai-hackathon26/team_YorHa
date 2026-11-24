package com.medidiary.app.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.medidiary.app.data.db.DiaryRecord
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Export is always an explicit user action; files go to cache/exports and are shared via FileProvider. */
class ExportManager(private val context: Context) {
    private val dir get() = File(context.cacheDir, "exports").apply { mkdirs() }

    fun exportPdf(records: List<DiaryRecord>, patientName: String?, from: Long, to: Long): File =
        PdfReportGenerator.generate(File(dir, "MediDiary_${stamp()}.pdf"), records.map { it.toRow() }, patientName, from, to)

    fun exportCsv(records: List<DiaryRecord>, patientName: String?): File =
        File(dir, "MediDiary_${stamp()}.csv").apply {
            writeText(CsvExporter.build(records.map { it.toRow() }, patientName), Charsets.UTF_8)
        }

    fun shareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mime = if (file.extension == "pdf") "application/pdf" else "text/csv"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share ${file.name}")
    }

    /** Remove exports older than a day so health data does not linger in cache. */
    fun cleanup(maxAgeMs: Long = 24L * 60 * 60 * 1000) {
        val now = System.currentTimeMillis()
        dir.listFiles()?.filter { now - it.lastModified() > maxAgeMs }?.forEach { it.delete() }
    }

    private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
}

fun DiaryRecord.toRow() = ExportRow(
    loggedDate = loggedDate, type = type, name = originalName,
    genericSuggestion = genericSuggestion, genericMatchStatus = genericMatchStatus,
    timePhrase = timePhrase, note = note,
)
