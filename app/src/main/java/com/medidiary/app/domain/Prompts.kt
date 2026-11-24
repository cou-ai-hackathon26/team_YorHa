package com.medidiary.app.domain

object Prompts {
    /** CLAUDE.md §3.2 — exact string. Must match what the Gemma fine-tune was trained with. */
    const val EXTRACTION_SYSTEM_PROMPT =
        "Extract medicine and symptom records from the transcript into strict JSON. " +
            "Output only JSON with schema: {\"records\": [{\"type\": \"medicine\"|\"symptom\", \"name\": string, " +
            "\"generic_suggestion\": null, \"time_phrase\": string|null, \"note\": string|null}]}. " +
            "Keep unknown fields null. Never infer dose, strength, frequency, or missing time."
}
