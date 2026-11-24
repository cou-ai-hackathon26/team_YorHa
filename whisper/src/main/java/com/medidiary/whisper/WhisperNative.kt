package com.medidiary.whisper

/**
 * Thin JNI surface over whisper.cpp. Each [transcribe] call loads the model, runs, and frees it,
 * so nothing stays resident between calls (sequential memory policy, CLAUDE.md §4.1).
 * Calls are blocking: invoke from a background dispatcher.
 */
object WhisperNative {
    init {
        System.loadLibrary("medidiary_whisper")
    }

    /**
     * @param modelPath absolute path to a ggml Whisper model (e.g. ggml-base.bin or your fine-tune).
     * @param samples mono PCM, 16 kHz, float in [-1, 1].
     * @param language ISO code ("bn", "en") or "auto".
     * @param initialPrompt optional decoder prompt (e.g. common brand names) — may be null.
     */
    external fun transcribe(
        modelPath: String,
        samples: FloatArray,
        language: String,
        nThreads: Int,
        initialPrompt: String?,
    ): String

    /** Requests the in-flight [transcribe] to stop; it then throws IllegalStateException. */
    external fun cancel()

    external fun systemInfo(): String
}
