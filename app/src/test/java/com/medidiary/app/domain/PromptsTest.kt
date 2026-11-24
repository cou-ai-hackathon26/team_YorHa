package com.medidiary.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The fine-tuned Gemma (notebooks: gemma3.ipynb, SYSTEM_PROMPT) was trained with this exact string.
 * Changing even whitespace shifts the model off its training distribution — retrain if you edit it.
 */
class PromptsTest {
    @Test fun systemPromptMatchesFineTuneTrainingPrompt() {
        val trained = "Extract medicine and symptom records from the transcript into strict JSON. " +
            "Output only JSON with schema: {\"records\": [{\"type\": \"medicine\"|\"symptom\", " +
            "\"name\": string, \"generic_suggestion\": null, " +
            "\"time_phrase\": string|null, \"note\": string|null}]}. " +
            "Keep unknown fields null. Never infer dose, strength, frequency, or missing time."
        assertEquals(trained, Prompts.EXTRACTION_SYSTEM_PROMPT)
    }
}
