package com.medidiary.app.data.dgda

import com.medidiary.app.domain.MatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MedicineNameGateTest {
    private val matcher: DgdaMatcher by lazy {
        val dir = listOf("src/main/assets/dgda", "app/src/main/assets/dgda").map(::File).first { it.isDirectory }
        val meds = File(dir, "dgda_medicines.csv").readLines().drop(1).filter { it.isNotBlank() }.map {
            val p = it.split(','); DgdaEntry(p[0].toInt(), p[1], p[2], p.getOrNull(3))
        }
        val aliases = File(dir, "dgda_aliases.csv").readLines().drop(1).filter { it.isNotBlank() }
            .associate { val p = it.split(','); p[0] to p[1].toInt() }
        DgdaMatcher(meds, aliases)
    }

    private fun gate(history: List<String> = emptyList()) = MedicineNameGate(matcher.vocabulary(), history)

    private fun verdict(name: String, history: List<String> = emptyList()): NameVerdict =
        gate(history).check(name, matcher.match(name).status)

    @Test fun phoneticKeysLineUpAcrossScripts() {
        assertEquals(Phonetic.key("Seclo"), Phonetic.key("seklow"))
        assertEquals(Phonetic.key("Seclo"), Phonetic.key("সেকলো"))
        assertEquals(Phonetic.key("Napa"), Phonetic.key("নাপা"))
        assertEquals(Phonetic.key("Concor"), Phonetic.key("কনকর"))
        assertEquals("napa", Phonetic.transliterate("নাপা"))
    }

    @Test fun knownNamesPass() {
        // Includes small typos the existing DgdaMatcher already treats as FUZZY ("monaas").
        listOf("Napa", "নাপা", "seclo", "Esonix 20", "Paracetamol", "Napa Extra", "sergel", "monaas").forEach {
            assertEquals(it, NameVerdict.Known, verdict(it))
        }
    }

    @Test fun mildMispronunciationIsClose() {
        mapOf(
            "seklow" to "Seclo",
            "phenadeen" to "Fenadin",
            "umiprajol" to "Omeprazole",
            "bhentolene" to "Ventolin",
            "রাবোট্রোল" to "Rivotril",
            "sipprowsheen" to "Ciprocin",
        ).forEach { (spoken, expected) ->
            val v = verdict(spoken)
            assertTrue("$spoken → $v", v is NameVerdict.Close && v.suggestion.equals(expected, ignoreCase = true))
        }
    }

    @Test fun garbageIsRejected() {
        listOf("blorvatrex", "banana", "xyzzor", "kumbhakarna", "telephone", "হাবিজাবি").forEach {
            val v = verdict(it)
            assertTrue("$it → $v", v is NameVerdict.Reject)
        }
    }

    @Test fun historyMakesAnUnknownBrandKnown() {
        // Not in the 80-brand DGDA subset, but the user saved it before (or chose "Keep as typed").
        assertTrue(verdict("Xinlomax") is NameVerdict.Reject)
        assertEquals(NameVerdict.Known, verdict("xinlomax", history = listOf("Xinlomax")))
        assertEquals(NameVerdict.Known, verdict("Xinlomax 10mg", history = listOf("Xinlomax")))
        val v = verdict("xinlomaks", history = listOf("Xinlomax"))
        assertTrue("$v", v is NameVerdict.Close && v.suggestion == "Xinlomax")
    }

    @Test fun manualStatusIsAlwaysKnown() {
        assertEquals(NameVerdict.Known, gate().check("whatever", MatchStatus.MANUAL))
    }

    @Test fun emptyNameIsRejected_emptyVocabularyDoesNotCrash() {
        assertTrue(gate().check("  ", MatchStatus.UNMATCHED) is NameVerdict.Reject)
        assertTrue(MedicineNameGate(emptyList()).check("napa", MatchStatus.UNMATCHED) is NameVerdict.Reject)
    }
}
