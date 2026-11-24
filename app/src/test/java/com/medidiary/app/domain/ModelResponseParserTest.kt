package com.medidiary.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelResponseParserTest {

    @Test fun parsesSpecSchema() {
        val raw = """{"records": [{"type": "medicine", "name": "Esonix", "generic_suggestion": null, "time_phrase": "সকালে নাস্তার আগে", "note": "20"}, {"type": "symptom", "name": "বুক জ্বালাপোড়া", "generic_suggestion": null, "time_phrase": "এখনো", "note": null}]}"""
        val r = ModelResponseParser.parse(raw)
        assertNull(r.error)
        assertEquals(2, r.records.size)
        assertEquals(RecordType.MEDICINE, r.records[0].type)
        assertEquals("Esonix", r.records[0].name)
        assertEquals("সকালে নাস্তার আগে", r.records[0].timePhrase)
        assertEquals("20", r.records[0].note)
        assertNull(r.records[1].note)
    }

    @Test fun neverTrustsModelGeneric() {
        val r = ModelResponseParser.parse("""{"records":[{"type":"medicine","name":"Napa","generic_suggestion":"Ibuprofen"}]}""")
        assertNull(r.records.single().genericSuggestion)
        assertEquals(MatchStatus.UNMATCHED, r.records.single().genericMatchStatus)
    }

    @Test fun stripsMarkdownFencesAndChatter() {
        val raw = "Sure! Here is the JSON:\n```json\n{\"records\":[{\"type\":\"symptom\",\"name\":\"জ্বর\",\"time_phrase\":null,\"note\":null}]}\n```\nHope this helps {not json}"
        val r = ModelResponseParser.parse(raw)
        assertNull(r.error)
        assertEquals("জ্বর", r.records.single().name)
    }

    @Test fun acceptsBareArray() {
        val r = ModelResponseParser.parse("""[{"type":"medicine","name":"Seclo"}]""")
        assertEquals("Seclo", r.records.single().name)
    }

    @Test fun bracesInsideStringsDoNotBreakExtraction() {
        val r = ModelResponseParser.parse("""{"records":[{"type":"medicine","name":"Napa","note":"took {1} tab ]"}]} trailing""")
        assertEquals("took {1} tab ]", r.records.single().note)
    }

    @Test fun repairsTruncatedOutput() {
        val raw = """{"records": [{"type": "medicine", "name": "Napa", "time_phrase": null, "note": null}, {"type": "symptom", "name": "মাথা ব"""
        val r = ModelResponseParser.parse(raw)
        assertNull(r.error)
        assertEquals(listOf("Napa"), r.records.map { it.name })
    }

    @Test fun rejectsOutOfSchemaItems() {
        val r = ModelResponseParser.parse("""{"records":[{"type":"dose","name":"500mg"},{"type":"medicine"},{"type":"MEDICINE","name":"Ace"},"junk"]}""")
        assertEquals(listOf("Ace"), r.records.map { it.name })
        assertEquals(RecordType.MEDICINE, r.records.single().type)
        assertEquals(3, r.rejectedCount)
    }

    @Test fun stringNullsBecomeNull() {
        val r = ModelResponseParser.parse("""{"records":[{"type":"medicine","name":"Napa","time_phrase":"null","note":"N/A"}]}""")
        assertNull(r.records.single().timePhrase)
        assertNull(r.records.single().note)
    }

    @Test fun reportsGarbage() {
        assertNotNull(ModelResponseParser.parse("I cannot help with that.").error)
        assertTrue(ModelResponseParser.parse("{\"records\": [}").records.isEmpty())
    }

    @Test fun emptyRecordsIsValid() {
        val r = ModelResponseParser.parse("""{"records": []}""")
        assertNull(r.error)
        assertTrue(r.records.isEmpty())
    }
}
