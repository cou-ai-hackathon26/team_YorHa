package com.medidiary.app.ui.checkin

import com.medidiary.app.domain.ProposedRecord

enum class Busy { NONE, RECORDING, TRANSCRIBING, EXTRACTING, SAVING }

/** A medicine name that matched nothing remotely (Task 2). */
data class RejectedName(val name: String, val closest: String?)

data class CheckInState(
    val busy: Busy = Busy.NONE,
    val transcript: String = "",
    val proposals: List<ProposedRecord> = emptyList(),
    val error: String? = null,
    val info: String? = null,
    val rawModelOutput: String? = null,
    val asrMs: Long? = null,
    val llmMs: Long? = null,
    val mockMode: Boolean = false,
    /** Non-empty → the check-in was stopped before Review: these names were not recognised. */
    val rejected: List<RejectedName> = emptyList(),
    /** Proposals held back while [rejected] is shown; released only by "Keep as typed". */
    val held: List<ProposedRecord> = emptyList(),
)
