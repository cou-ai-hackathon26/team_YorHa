package com.medidiary.app.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Defensive parser for the LLM output (CLAUDE.md §3.1). Small models sometimes wrap JSON in
 * markdown fences, add chatter, emit a bare array or trailing junk — all handled here.
 * Anything out-of-schema is dropped rather than guessed. `generic_suggestion` from the model is
 * ALWAYS ignored (core rule #4: generics come only from the DGDA table).
 */
object ModelResponseParser {

    data class Result(
        val records: List<ProposedRecord>,
        /** Non-null when the output could not be parsed as JSON at all. */
        val error: String? = null,
        /** Number of array items rejected for being out of schema. */
        val rejectedCount: Int = 0,
    )

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    private val NULL_STRINGS = setOf("", "null", "none", "n/a", "na", "unknown", "নেই", "জানা নেই")

    fun parse(raw: String): Result {
        val candidate = extractJsonBlock(raw) ?: return Result(emptyList(), "No JSON found in model output")
        val root: JsonElement = try {
            json.parseToJsonElement(candidate)
        } catch (e: Exception) {
            return Result(emptyList(), "Malformed JSON: ${e.message?.take(120)}")
        }

        val items: List<JsonElement> = when (root) {
            is JsonObject -> when (val r = root["records"]) {
                is JsonArray -> r
                is JsonObject -> listOf(r)
                null, JsonNull -> if (root.containsKey("name")) listOf(root) else emptyList()
                else -> return Result(emptyList(), "\"records\" is not an array")
            }
            is JsonArray -> root
            else -> return Result(emptyList(), "Unexpected JSON root")
        }

        var rejected = 0
        val records = items.mapNotNull { el ->
            toRecord(el).also { if (it == null) rejected++ }
        }
        return Result(records, null, rejected)
    }

    private fun toRecord(el: JsonElement): ProposedRecord? {
        if (el !is JsonObject) return null
        val type = normalizeType(el.str("type")) ?: return null
        val name = el.str("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return ProposedRecord(
            type = type,
            name = name,
            genericSuggestion = null, // never trust the LLM for generics
            genericMatchStatus = MatchStatus.UNMATCHED,
            timePhrase = el.str("time_phrase")?.trim()?.takeIf { it.isNotEmpty() },
            note = el.str("note")?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private fun JsonObject.str(key: String): String? {
        val v = this[key] ?: return null
        if (v !is JsonPrimitive || v is JsonNull) return null
        val s = v.content
        return if (s.trim().lowercase() in NULL_STRINGS) null else s
    }

    private fun normalizeType(t: String?): String? = when (t?.trim()?.lowercase()) {
        "medicine", "medication", "drug", "ওষুধ", "ঔষধ" -> RecordType.MEDICINE
        "symptom", "symptoms", "লক্ষণ", "উপসর্গ" -> RecordType.SYMPTOM
        else -> null
    }

    /**
     * Returns the first balanced top-level JSON object/array in [raw], ignoring markdown fences
     * and any prose around it. Brackets inside string literals are skipped.
     */
    internal fun extractJsonBlock(raw: String): String? {
        val text = raw.replace("```json", "").replace("```", "")
        val start = text.indexOfFirst { it == '{' || it == '[' }
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{', '[' -> depth++
                '}', ']' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        // Truncated output (hit max tokens): try to close it so partial records survive.
        return repairTruncated(text.substring(start))
    }

    private fun repairTruncated(s: String): String? {
        // Cut back to the last complete object inside the records array, then close brackets.
        val lastObjEnd = s.lastIndexOf('}')
        if (lastObjEnd <= 0) return null
        val head = s.substring(0, lastObjEnd + 1)
        val opens = ArrayDeque<Char>()
        var inString = false
        var escaped = false
        for (c in head) {
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> opens.addLast('}')
                '[' -> opens.addLast(']')
                '}', ']' -> if (opens.isNotEmpty()) opens.removeLast()
            }
        }
        if (inString) return null
        return head + opens.reversed().joinToString("")
    }
}
