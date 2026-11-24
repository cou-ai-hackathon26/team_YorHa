package com.medidiary.app.data.dgda

import com.medidiary.app.domain.MatchStatus

/** Plain (non-Room) view of a DGDA reference row, so matching logic is JVM-testable. */
data class DgdaEntry(
    val id: Int,
    val brandName: String,
    val genericName: String,
    val dosageForm: String? = null,
)

data class GenericMatch(
    val genericName: String?,
    val status: String,
    val matchedBrand: String? = null,
)

/**
 * Deterministic brand → generic lookup (core rule #4). The LLM never produces generics;
 * this class does, from the bundled DGDA reference subset only.
 *
 * Order: exact (brand, Bangla alias, or generic name) → exact after stripping strength
 * ("Esonix 20" → "esonix") → prefix → small edit distance. Anything below EXACT is
 * shown to the user as a suggestion to confirm, never applied silently.
 */
class DgdaMatcher(entries: List<DgdaEntry>, aliases: Map<String, Int> = emptyMap()) {

    private val byId = entries.associateBy { it.id }
    private val exactIndex: Map<String, DgdaEntry>
    private val keys: List<Pair<String, DgdaEntry>>

    init {
        val idx = LinkedHashMap<String, DgdaEntry>()
        entries.forEach { e ->
            idx.putIfAbsent(normalize(e.brandName), e)
        }
        aliases.forEach { (alias, id) -> byId[id]?.let { idx.putIfAbsent(normalize(alias), it) } }
        // A spoken generic name ("Paracetamol", "প্যারাসিটামল" via alias) maps to itself.
        entries.forEach { e ->
            idx.putIfAbsent(normalize(e.genericName), e.copy(brandName = e.genericName))
        }
        exactIndex = idx
        keys = idx.entries.map { it.key to it.value }
    }

    /** Every matchable key (normalized brand, alias or generic) → the name to show for it. */
    fun vocabulary(): List<Pair<String, String>> = keys.map { (k, e) -> k to e.brandName }

    fun match(spoken: String): GenericMatch {
        val q = normalize(spoken)
        if (q.isEmpty()) return GenericMatch(null, MatchStatus.UNMATCHED)

        exactIndex[q]?.let { return GenericMatch(it.genericName, MatchStatus.EXACT, it.brandName) }

        val stripped = stripStrength(q)
        if (stripped != q && stripped.isNotEmpty()) {
            exactIndex[stripped]?.let { return GenericMatch(it.genericName, MatchStatus.EXACT, it.brandName) }
        }

        val base = stripped.ifEmpty { q }
        if (base.length >= 3) {
            // Spoken "napa ex" → "napa extra"; spoken "seclo capsule" → "seclo".
            val prefix = keys.filter { (k, _) -> k.startsWith(base) || base.startsWith("$k ") }
                .minByOrNull { (k, _) -> kotlin.math.abs(k.length - base.length) }
            if (prefix != null) return GenericMatch(prefix.second.genericName, MatchStatus.FUZZY, prefix.second.brandName)
        }

        if (base.length >= 4) {
            val maxDist = if (base.length >= 7) 2 else 1
            val best = keys.asSequence()
                .filter { (k, _) -> kotlin.math.abs(k.length - base.length) <= maxDist }
                .map { (k, e) -> Triple(k, e, levenshtein(k, base, maxDist)) }
                .filter { it.third <= maxDist }
                .minByOrNull { it.third }
            if (best != null) return GenericMatch(best.second.genericName, MatchStatus.FUZZY, best.second.brandName)
        }
        return GenericMatch(null, MatchStatus.UNMATCHED)
    }

    companion object {
        private val PUNCT = Regex("[\\p{Punct}&&[^+]]")
        private val SPACES = Regex("\\s+")
        // Bangla digits are mapped to ASCII before this runs.
        private val STRENGTH = Regex("(\\s*\\d+(\\.\\d+)?\\s*(mg|mcg|ml|gm|g|iu|%)?)+$")
        private val FORM_WORDS = setOf(
            "tablet", "tab", "tabs", "capsule", "cap", "caps", "syrup", "suspension", "injection",
            "inj", "drop", "drops", "cream", "ointment", "gel", "inhaler", "sachet", "ট্যাবলেট",
            "ক্যাপসুল", "সিরাপ", "ইনজেকশন", "ড্রপ", "টা", "টি", "খানা",
        )

        fun normalize(s: String): String {
            val ascii = buildString(s.length) {
                for (ch in s) append(if (ch in '০'..'৯') '0' + (ch - '০') else ch)
            }
            return ascii.lowercase()
                .replace('‌', ' ').replace('‍', ' ')
                .replace(PUNCT, " ")
                .replace(SPACES, " ")
                .trim()
        }

        fun stripStrength(normalized: String): String {
            val words = normalized.split(' ').filter { it !in FORM_WORDS }
            return words.joinToString(" ").replace(STRENGTH, "").trim()
        }

        /** Bounded Levenshtein: returns > maxDist early when it can't be within range. */
        fun levenshtein(a: String, b: String, maxDist: Int = Int.MAX_VALUE): Int {
            if (a == b) return 0
            var prev = IntArray(b.length + 1) { it }
            var cur = IntArray(b.length + 1)
            for (i in 1..a.length) {
                cur[0] = i
                var rowMin = cur[0]
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                    if (cur[j] < rowMin) rowMin = cur[j]
                }
                if (rowMin > maxDist) return maxDist + 1
                val t = prev; prev = cur; cur = t
            }
            return prev[b.length]
        }
    }
}
