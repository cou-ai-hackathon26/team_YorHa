package com.medidiary.app.domain

import java.util.UUID

object RecordType {
    const val MEDICINE = "medicine"
    const val SYMPTOM = "symptom"
}

object MatchStatus {
    const val EXACT = "EXACT"
    const val FUZZY = "FUZZY"
    const val UNMATCHED = "UNMATCHED"
    const val MANUAL = "MANUAL"
}

/**
 * A record proposed by the AI (or typed manually) that has NOT been saved yet.
 * Lives only in memory until the user confirms it on the Review screen (core rule #2).
 */
data class ProposedRecord(
    val localId: String = UUID.randomUUID().toString(),
    val type: String,
    val name: String,
    val genericSuggestion: String? = null,
    val genericMatchStatus: String = MatchStatus.UNMATCHED,
    val timePhrase: String? = null,
    val note: String? = null,
    val checked: Boolean = true,
    /** Set when the spoken name sounds like a known one ("Did you mean Seclo?"). Never applied silently. */
    val didYouMean: String? = null,
)
