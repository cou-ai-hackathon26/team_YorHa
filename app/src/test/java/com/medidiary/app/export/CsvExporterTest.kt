package com.medidiary.app.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExporterTest {
    @Test fun escapesQuotesCommasNewlines() {
        assertEquals("\"a, b\"", CsvExporter.escape("a, b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvExporter.escape("say \"hi\""))
        assertEquals("\"x\ny\"", CsvExporter.escape("x\ny"))
        assertEquals("plain", CsvExporter.escape("plain"))
    }

    @Test fun neutralisesFormulaInjection() {
        assertEquals("'=HYPERLINK(1)", CsvExporter.escape("=HYPERLINK(1)"))
    }

    @Test fun buildsUtf8WithBomHeaderAndDisclaimer() {
        val csv = CsvExporter.build(
            listOf(ExportRow(0L, "medicine", "নাপা", "Paracetamol", "EXACT", "সকালে", null)),
            patientName = "Test",
        )
        assertTrue(csv.startsWith("﻿"))
        assertTrue(csv.contains(CsvExporter.DISCLAIMER))
        assertTrue(csv.contains("Date,Time Logged,Time Reported,Entry Type,Reported Name,Generic Match (DGDA),Match Status,Notes"))
        assertTrue(csv.contains("নাপা,Paracetamol,EXACT"))
    }
}
