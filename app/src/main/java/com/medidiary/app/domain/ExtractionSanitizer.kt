package com.medidiary.app.domain

/**
 * CLAUDE.md §5 — defensive filter applied to LLM proposals before they reach the UI.
 * Extended slightly vs. the spec: ZWJ/ZWNJ and a few punctuation marks that legitimately appear
 * in Bangla text and brand names ("Napa Extra", "Calbo-D", "Entacyd (plus)") are allowed.
 */
object ExtractionSanitizer {
    private val SCRIPT_REGEX = Regex("^[\\u0980-\\u09FF\\u200C\\u200Da-zA-Z0-9\\s\\-+./(),']+$")

    private val PLACEHOLDER_BLACKLIST = setOf(
        "কিছু না", "কিছু নেই", "কোনো কিছু না", "none", "nothing",
        "no medicine", "na", "n/a", "null", "ঔষধ", "ওষুধ", "medicine", "symptom",
    )
    private val INVALID_SYMPTOM_WORDS = setOf("ভাত", "খাবার", "পানি", "রুটি")

    fun sanitize(records: List<ProposedRecord>): List<ProposedRecord> {
        val seen = HashSet<String>()
        return records.filter { rec ->
            val name = rec.name.trim().lowercase()

            // 1. Drop blacklisted placeholders & hallucinations
            if (name.isEmpty() || PLACEHOLDER_BLACKLIST.contains(name)) return@filter false

            // 2. Reject foreign scripts (e.g. Chinese tokens like "药")
            if (!SCRIPT_REGEX.matches(rec.name.trim())) return@filter false

            // 3. Prevent food phrases misclassified as symptoms
            if (rec.type == RecordType.SYMPTOM && INVALID_SYMPTOM_WORDS.any { name.contains(it) }) return@filter false

            // 4. Must have a valid type
            if (rec.type != RecordType.MEDICINE && rec.type != RecordType.SYMPTOM) return@filter false

            // 5. Drop exact duplicates the model sometimes repeats
            seen.add("${rec.type}|$name|${rec.timePhrase.orEmpty().trim().lowercase()}")
        }.map { it.copy(name = it.name.trim()) }
    }
}
