// ai/GemmaEngine.kt
package com.medidiary.app.ai

/** Returns the model's raw text output (expected: JSON per CLAUDE.md §3.1). */
interface GemmaEngine {
    suspend fun extract(transcript: String): String
}

class MockGemmaEngine : GemmaEngine {
    override suspend fun extract(transcript: String): String {
        kotlinx.coroutines.delay(1500) // simulate LLM latency
        return """{"records": [{"type": "medicine", "name": "Esonix", "generic_suggestion": null, "time_phrase": "সকালে নাস্তার আগে", "note": "20"}, {"type": "symptom", "name": "বুক জ্বালাপোড়া", "generic_suggestion": null, "time_phrase": "এখনো", "note": null}, {"type": "symptom", "name": "gas", "generic_suggestion": null, "time_phrase": "এখনো", "note": "একটু"}]}"""
    }
}
