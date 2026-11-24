package com.medidiary.app.data.dgda

import com.medidiary.app.domain.MatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class DgdaMatcherTest {
    /** Uses the real bundled reference CSVs so the shipped data is exercised too. */
    private val matcher: DgdaMatcher by lazy {
        val dir = listOf("src/main/assets/dgda", "app/src/main/assets/dgda").map(::File).first { it.isDirectory }
        val meds = File(dir, "dgda_medicines.csv").readLines().drop(1).filter { it.isNotBlank() }.map {
            val p = it.split(','); DgdaEntry(p[0].toInt(), p[1], p[2], p.getOrNull(3))
        }
        val aliases = File(dir, "dgda_aliases.csv").readLines().drop(1).filter { it.isNotBlank() }
            .associate { val p = it.split(','); p[0] to p[1].toInt() }
        DgdaMatcher(meds, aliases)
    }

    @Test fun exactBrand() {
        val m = matcher.match("Napa")
        assertEquals("Paracetamol", m.genericName)
        assertEquals(MatchStatus.EXACT, m.status)
    }

    @Test fun caseAndWhitespaceInsensitive() = assertEquals(MatchStatus.EXACT, matcher.match("  SECLO ").status)

    @Test fun banglaAlias() {
        val m = matcher.match("নাপা")
        assertEquals("Paracetamol", m.genericName)
        assertEquals(MatchStatus.EXACT, m.status)
        assertEquals("Esomeprazole", matcher.match("এসোনিক্স").genericName)
    }

    @Test fun stripsStrengthAndForm() {
        assertEquals(MatchStatus.EXACT, matcher.match("Esonix 20").status)
        assertEquals("Esomeprazole", matcher.match("Esonix 20mg").genericName)
        assertEquals("Omeprazole", matcher.match("Seclo capsule 20").genericName)
        assertEquals("Paracetamol", matcher.match("নাপা ৫০০").genericName)
    }

    @Test fun multiWordBrandPreferredOverPrefix() {
        assertEquals("Paracetamol + Caffeine", matcher.match("Napa Extra").genericName)
    }

    @Test fun genericNameMatchesItself() {
        val m = matcher.match("paracetamol")
        assertEquals("Paracetamol", m.genericName)
        assertEquals(MatchStatus.EXACT, m.status)
    }

    @Test fun typoIsFuzzy() {
        val m = matcher.match("Sergal")
        assertEquals("Esomeprazole", m.genericName)
        assertEquals(MatchStatus.FUZZY, m.status)
    }

    @Test fun unknownIsUnmatched() {
        val m = matcher.match("Xyzzyplex")
        assertNull(m.genericName)
        assertEquals(MatchStatus.UNMATCHED, m.status)
        assertEquals(MatchStatus.UNMATCHED, matcher.match("").status)
    }

    @Test fun shortWordsDoNotFuzzyMatch() {
        // "gas" must not fuzzy-match a 3–4 letter brand.
        assertEquals(MatchStatus.UNMATCHED, matcher.match("gas").status)
    }

    @Test fun referenceDataIsWellFormed() {
        val dir = listOf("src/main/assets/dgda", "app/src/main/assets/dgda").map(::File).first { it.isDirectory }
        val ids = File(dir, "dgda_medicines.csv").readLines().drop(1).filter { it.isNotBlank() }.map {
            val p = it.split(',')
            assertEquals("bad column count: $it", 4, p.size)
            p[0].toInt()
        }
        assertEquals("duplicate ids", ids.size, ids.toSet().size)
        File(dir, "dgda_aliases.csv").readLines().drop(1).filter { it.isNotBlank() }.forEach {
            val id = it.split(',')[1].toInt()
            assert(id in ids) { "alias points at missing id: $it" }
        }
    }
}
