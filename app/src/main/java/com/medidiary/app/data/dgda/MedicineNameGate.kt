package com.medidiary.app.data.dgda

import com.medidiary.app.domain.MatchStatus

/** Result of checking one spoken medicine name (hackathon Task 2). */
sealed interface NameVerdict {
    /** Matches the DGDA list or a name this diary already has: continue as normal. */
    data object Known : NameVerdict

    /** Mildly mispronounced: continue, but show "Did you mean [suggestion]?". */
    data class Close(val suggestion: String, val score: Double) : NameVerdict

    /** Nothing remotely similar: the check-in is stopped before Review/Save. */
    data class Reject(val closest: String?, val score: Double) : NameVerdict
}

/**
 * Decides whether an extracted medicine name is usable. Pure Kotlin, JVM-testable.
 *
 * Candidates = every DGDA brand, Bangla alias and generic ([DgdaMatcher.vocabulary]) plus every
 * medicine name already saved in this diary (the user's own vocabulary, which also holds names
 * the user once confirmed with "Keep as typed").
 *
 * score = best of (edit similarity of the transliterated names, 0.9 × edit similarity of the
 * consonant skeletons from [Phonetic.key], only for skeletons of ≥ 3 letters).
 */
class MedicineNameGate(
    vocabulary: List<Pair<String, String>>,
    history: Collection<String> = emptyList(),
    private val closeThreshold: Double = CLOSE_THRESHOLD,
) {
    private class Candidate(val display: String, val latin: String, val key: String) {
        val displayLatin = latin(DgdaMatcher.normalize(display))
    }

    private val historyKeys = history.map { DgdaMatcher.normalize(it) }.filter { it.isNotEmpty() }.toSet()
    private val candidates: List<Candidate> =
        (vocabulary + history.map { DgdaMatcher.normalize(it) to it.trim() })
            .filter { it.first.isNotBlank() }
            .distinctBy { it.first }
            .map { (k, display) -> Candidate(display, latin(k), Phonetic.key(k)) }

    /** [dgdaStatus] is the status [DgdaMatcher.match] already gave this name. */
    fun check(name: String, dgdaStatus: String): NameVerdict {
        if (dgdaStatus == MatchStatus.EXACT || dgdaStatus == MatchStatus.FUZZY || dgdaStatus == MatchStatus.MANUAL) {
            return NameVerdict.Known
        }
        val q = DgdaMatcher.normalize(name)
        if (q.isEmpty()) return NameVerdict.Reject(null, 0.0)
        if (q in historyKeys) return NameVerdict.Known
        val stripped = DgdaMatcher.stripStrength(q).ifEmpty { q }
        if (stripped in historyKeys) return NameVerdict.Known

        val qLatin = latin(stripped)
        val qKey = Phonetic.key(stripped)
        var best: Candidate? = null
        var bestScore = 0.0
        var bestTie = 0.0
        for (c in candidates) {
            val s = score(qLatin, qKey, c)
            if (s < bestScore) continue
            // Equal scores (e.g. a Bangla generic alias that belongs to a brand): prefer the
            // candidate whose shown name is closest to what was said.
            val tie = similarity(qLatin, c.displayLatin)
            if (s > bestScore || tie > bestTie) { bestScore = s; bestTie = tie; best = c }
        }
        return when {
            best != null && bestScore >= closeThreshold -> NameVerdict.Close(best.display, bestScore)
            else -> NameVerdict.Reject(best?.display, bestScore)
        }
    }

    private fun score(qLatin: String, qKey: String, c: Candidate): Double {
        val text = similarity(qLatin, c.latin)
        val sound = if (qKey.length >= 3 && c.key.length >= 3) 0.9 * similarity(qKey, c.key) else 0.0
        return maxOf(text, sound)
    }

    companion object {
        /** Tune on the demo phone: below this, the check-in is stopped. */
        const val CLOSE_THRESHOLD = 0.6

        private fun latin(normalized: String) = Phonetic.transliterate(normalized).replace(" ", "")

        /** 1 − (edit distance / longer length), in 0..1. */
        fun similarity(a: String, b: String): Double {
            if (a.isEmpty() && b.isEmpty()) return 1.0
            if (a.isEmpty() || b.isEmpty()) return 0.0
            val d = DgdaMatcher.levenshtein(a, b)
            return 1.0 - d.toDouble() / maxOf(a.length, b.length)
        }
    }
}
