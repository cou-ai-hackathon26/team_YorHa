package com.medidiary.app.export

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CLAUDE.md Screen 5. Clinical one-page report (continues onto extra pages only if needed).
 * Bangla text renders through the system Noto Sans Bengali fallback font — no font bundling.
 */
object PdfReportGenerator {
    private const val PAGE_W = 595 // A4 @ 72 dpi
    private const val PAGE_H = 842
    private const val MARGIN = 36f

    private val COLUMNS = listOf(
        "Date" to 0.12f, "Time Reported" to 0.15f, "Entry Type" to 0.11f,
        "Reported Name" to 0.20f, "Generic Match (DGDA)" to 0.22f, "Notes" to 0.20f,
    )

    fun generate(out: File, rows: List<ExportRow>, patientName: String?, from: Long, to: Long): File {
        val doc = PdfDocument()
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        val notice = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.rgb(176, 0, 32) }
        val meta = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f; color = Color.DKGRAY }
        val head = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8.5f; typeface = Typeface.DEFAULT_BOLD; color = Color.WHITE }
        val cell = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8.5f; color = Color.BLACK }
        val line = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.5f }
        val headBg = Paint().apply { color = Color.rgb(21, 101, 192) }
        val zebra = Paint().apply { color = Color.rgb(242, 246, 252) }

        val tableW = PAGE_W - 2 * MARGIN
        val colW = COLUMNS.map { it.second * tableW }
        val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.US)
        val dayFmt = SimpleDateFormat("dd MMM", Locale.US)
        val timeFmt = SimpleDateFormat("HH:mm", Locale.US)

        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        fun drawTableHeader() {
            val c = page!!.canvas
            c.drawRect(MARGIN, y, MARGIN + tableW, y + 18f, headBg)
            var x = MARGIN
            COLUMNS.forEachIndexed { i, (h, _) -> c.drawText(h, x + 3f, y + 12f, head); x += colW[i] }
            y += 18f
        }

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            val c = page!!.canvas
            y = MARGIN
            if (pageNo == 1) {
                c.drawText("MediDiary — Patient Reported Health Log", MARGIN, y + 14f, title); y += 24f
                y += drawWrapped(c, "Notice: Patient-entered, AI-assisted timeline. Not verified clinical diagnoses. " +
                    CsvExporter.DISCLAIMER, MARGIN, y, tableW, notice) + 6f
                val who = if (patientName.isNullOrBlank()) "Patient: —" else "Patient: $patientName"
                c.drawText("$who     Period: ${dateFmt.format(Date(from))} – ${dateFmt.format(Date(to))}     " +
                    "Entries: ${rows.size}     Generated: ${dateFmt.format(Date())}", MARGIN, y + 10f, meta)
                y += 22f
            } else {
                c.drawText("MediDiary — continued (page $pageNo)", MARGIN, y + 10f, meta); y += 20f
            }
            drawTableHeader()
        }

        newPage()
        if (rows.isEmpty()) {
            page!!.canvas.drawText("No verified entries in this period.", MARGIN + 3f, y + 14f, cell)
        }
        rows.sortedBy { it.loggedDate }.forEachIndexed { idx, r ->
            val d = Date(r.loggedDate)
            val generic = when {
                r.genericSuggestion == null -> "Unmatched brand"
                r.genericMatchStatus == "EXACT" || r.genericMatchStatus == "MANUAL" -> r.genericSuggestion
                else -> "${r.genericSuggestion} (suggested)"
            }
            val values = listOf(
                "${dayFmt.format(d)}\n${timeFmt.format(d)}",
                r.timePhrase ?: "—",
                r.type.replaceFirstChar { it.uppercase() },
                r.name,
                if (r.type == "medicine") generic else "—",
                r.note ?: "",
            )
            val layouts = values.mapIndexed { i, v -> layout(v, cell, (colW[i] - 6f).toInt()) }
            val rowH = (layouts.maxOf { it.height } + 8).toFloat()
            if (y + rowH > PAGE_H - MARGIN - 16f) newPage()
            val c = page!!.canvas
            if (idx % 2 == 1) c.drawRect(MARGIN, y, MARGIN + tableW, y + rowH, zebra)
            var x = MARGIN
            layouts.forEachIndexed { i, l ->
                c.save(); c.translate(x + 3f, y + 4f); l.draw(c); c.restore()
                x += colW[i]
            }
            y += rowH
            c.drawLine(MARGIN, y, MARGIN + tableW, y, line)
        }
        page!!.canvas.drawText(
            "Generated offline by MediDiary. Generic names are suggestions from a local DGDA reference list.",
            MARGIN, PAGE_H - MARGIN + 6f, meta,
        )
        doc.finishPage(page!!)
        out.parentFile?.mkdirs()
        out.outputStream().use { doc.writeTo(it) }
        doc.close()
        return out
    }

    private fun layout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(10))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(6)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()

    private fun drawWrapped(c: android.graphics.Canvas, text: String, x: Float, y: Float, w: Float, p: TextPaint): Float {
        val l = layout(text, p, w.toInt())
        c.save(); c.translate(x, y); l.draw(c); c.restore()
        return l.height.toFloat()
    }
}
