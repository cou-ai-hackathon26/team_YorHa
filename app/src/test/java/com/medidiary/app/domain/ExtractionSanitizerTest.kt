package com.medidiary.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ExtractionSanitizerTest {
    private fun med(name: String, time: String? = null) = ProposedRecord(type = RecordType.MEDICINE, name = name, timePhrase = time)
    private fun sym(name: String) = ProposedRecord(type = RecordType.SYMPTOM, name = name)

    @Test fun dropsPlaceholders() {
        val out = ExtractionSanitizer.sanitize(listOf(med("কিছু না"), med("None"), med("ওষুধ"), med("Napa")))
        assertEquals(listOf("Napa"), out.map { it.name })
    }

    @Test fun rejectsForeignScript() {
        val out = ExtractionSanitizer.sanitize(listOf(med("药"), med("Napa药"), med("নাপা")))
        assertEquals(listOf("নাপা"), out.map { it.name })
    }

    @Test fun rejectsFoodAsSymptom() {
        val out = ExtractionSanitizer.sanitize(listOf(sym("ভাত খাওয়া"), sym("মাথা ব্যথা")))
        assertEquals(listOf("মাথা ব্যথা"), out.map { it.name })
    }

    @Test fun rejectsInvalidType() {
        val out = ExtractionSanitizer.sanitize(listOf(ProposedRecord(type = "dose", name = "Napa"), med("Napa")))
        assertEquals(1, out.size)
    }

    @Test fun allowsRealWorldBrandPunctuationAndBanglaJoiners() {
        val names = listOf("Calbo-D", "Napa Extra 500", "Entacyd (plus)", "র‍্যাপিড")
        assertEquals(names, ExtractionSanitizer.sanitize(names.map { med(it) }).map { it.name })
    }

    @Test fun dedupesAndTrims() {
        val out = ExtractionSanitizer.sanitize(listOf(med(" Napa ", "সকালে"), med("napa", "সকালে"), med("Napa", "রাতে")))
        assertEquals(listOf("Napa", "Napa"), out.map { it.name })
    }
}
