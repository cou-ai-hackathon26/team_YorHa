package com.medidiary.llama

/**
 * Thin JNI surface over llama.cpp. [complete] loads the GGUF, decodes greedily (temperature 0.0),
 * and frees everything before returning (sequential memory policy, CLAUDE.md §4.1).
 * Calls are blocking: invoke from a background dispatcher.
 */
object LlamaNative {
    init {
        System.loadLibrary("medidiary_llama")
    }

    /**
     * @param useChatTemplate true = wrap with the chat template embedded in the GGUF (Gemma 3
     *   `<start_of_turn>` format, system prompt merged into the user turn). false = send
     *   `systemPrompt + "\n\n" + userText` verbatim (use if the fine-tune was trained on a raw format).
     * @param stopAtJsonEnd stop as soon as the first top-level JSON object closes.
     */
    external fun complete(
        modelPath: String,
        systemPrompt: String,
        userText: String,
        nCtx: Int,
        nThreads: Int,
        maxTokens: Int,
        useChatTemplate: Boolean,
        stopAtJsonEnd: Boolean,
    ): String

    /** Requests the in-flight [complete] to stop; it then throws IllegalStateException. */
    external fun cancel()

    external fun systemInfo(): String
}
