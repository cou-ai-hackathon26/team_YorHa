package com.medidiary.app.data.dgda

/**
 * Rough sound-alike helpers for medicine names (hackathon Task 2). Pure Kotlin, JVM-testable.
 *
 * [transliterate] turns Bangla script into approximate Latin letters (সেকলো → seklo) so a
 * Bangla-script Whisper output can be compared against Latin brand names. [key] reduces a
 * name to a consonant skeleton (Seclo, seklow, সেকলো → "skl") so vowel slop and c/k, ph/f,
 * z/j style mispronunciations still line up.
 */
object Phonetic {
    private val CONSONANTS = mapOf(
        'ক' to "k", 'খ' to "kh", 'গ' to "g", 'ঘ' to "gh", 'ঙ' to "ng",
        'চ' to "c", 'ছ' to "ch", 'জ' to "j", 'ঝ' to "jh", 'ঞ' to "n",
        'ট' to "t", 'ঠ' to "th", 'ড' to "d", 'ঢ' to "dh", 'ণ' to "n",
        'ত' to "t", 'থ' to "th", 'দ' to "d", 'ধ' to "dh", 'ন' to "n",
        'প' to "p", 'ফ' to "f", 'ব' to "b", 'ভ' to "bh", 'ম' to "m",
        'য' to "j", 'র' to "r", 'ল' to "l", 'শ' to "sh", 'ষ' to "sh",
        'স' to "s", 'হ' to "h", '\u09DC' to "r", '\u09DD' to "r", '\u09DF' to "y", 'ৎ' to "t",
        'ং' to "ng", 'ঃ' to "h",
    )
    private val VOWELS = mapOf(
        'অ' to "o", 'আ' to "a", 'ই' to "i", 'ঈ' to "i", 'উ' to "u", 'ঊ' to "u", 'ঋ' to "ri",
        'এ' to "e", 'ঐ' to "oi", 'ও' to "o", 'ঔ' to "ou",
        'া' to "a", 'ি' to "i", 'ী' to "i", 'ু' to "u", 'ূ' to "u", 'ৃ' to "ri",
        'ে' to "e", 'ৈ' to "oi", 'ো' to "o", 'ৌ' to "ou",
    )
    private const val HASANTA = '্'
    private const val NUKTA = '\u09BC'
    /** Decomposed ড + ় / ঢ + ় / য + ় → the precomposed letters used in [CONSONANTS]. */
    private fun compose(s: String) = s
        .replace("ড$NUKTA", "\u09DC").replace("ঢ$NUKTA", "\u09DD").replace("য$NUKTA", "\u09DF")

    fun transliterate(s: String): String {
        val out = StringBuilder(s.length * 2)
        var prevHasanta = false
        for (ch in compose(s)) {
            when {
                ch == HASANTA -> { prevHasanta = true; continue }
                ch == '\u200C' || ch == '\u200D' || ch == 'ঁ' || ch == NUKTA -> continue
                // য-ফলা (্য) only changes the vowel sound ("অ্যা" ≈ English "a"): drop it.
                ch == 'য' && prevHasanta -> Unit
                ch in CONSONANTS -> out.append(CONSONANTS.getValue(ch))
                ch in VOWELS -> out.append(VOWELS.getValue(ch))
                ch in '০'..'৯' -> out.append('0' + (ch - '০'))
                else -> out.append(ch.lowercaseChar())
            }
            prevHasanta = false
        }
        return out.toString()
    }

    /** Consonant skeleton of a (Latin or Bangla) name. May be empty for very short names. */
    fun key(s: String): String {
        val t = transliterate(s).lowercase().filter { it in 'a'..'z' }
        val out = StringBuilder()
        var i = 0
        fun next(k: Int) = t.getOrNull(i + k)
        while (i < t.length) {
            val c = t[i]
            val n = next(1)
            val code: Char? = when (c) {
                'a', 'e', 'i', 'o', 'u', 'y', 'w', 'h' -> null
                'c' -> when {
                    n == 'h' -> { i++; 'c' }
                    n == 'k' -> { i++; 'k' }
                    n == 'e' || n == 'i' || n == 'y' -> 's'
                    else -> 'k'
                }
                'p' -> if (n == 'h') { i++; 'f' } else 'p'
                's' -> if (n == 'h') { i++; 's' } else 's'
                'k', 'q' -> 'k'
                'g' -> if (n == 'e' || n == 'i') 'j' else 'g'
                'z', 'j' -> 'j'
                'v', 'b' -> 'b'
                'x' -> { out.appendDistinct('k'); 's' }
                else -> c
            }
            if (code != null) out.appendDistinct(code)
            i++
        }
        return out.toString()
    }

    private fun StringBuilder.appendDistinct(c: Char) {
        if (isEmpty() || this[length - 1] != c) append(c)
    }
}
