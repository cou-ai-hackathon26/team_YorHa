package com.medidiary.app.ai

import com.medidiary.app.data.AppSettings
import com.medidiary.app.domain.Prompts
import com.medidiary.llama.LlamaNative
import com.medidiary.whisper.WhisperNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private fun inferenceThreads(): Int =
    Runtime.getRuntime().availableProcessors().coerceIn(2, 6) // leave big.LITTLE headroom for UI

class NativeWhisperEngine(
    private val modelFile: File,
    private val settings: () -> AppSettings,
) : WhisperEngine {
    override suspend fun transcribe(wavFile: File): String = withContext(Dispatchers.Default) {
        val samples = WavReader.readPcm16MonoAsFloat(wavFile)
        WhisperNative.transcribe(
            modelPath = modelFile.absolutePath,
            samples = samples,
            language = settings().asrLanguage,
            nThreads = inferenceThreads(),
            initialPrompt = null,
        ).trim()
    }

    fun cancel() = WhisperNative.cancel()
}

class NativeGemmaEngine(
    private val modelFile: File,
    private val settings: () -> AppSettings,
) : GemmaEngine {
    override suspend fun extract(transcript: String): String = withContext(Dispatchers.Default) {
        val s = settings()
        LlamaNative.complete(
            modelPath = modelFile.absolutePath,
            systemPrompt = Prompts.EXTRACTION_SYSTEM_PROMPT,
            userText = transcript.trim(),
            nCtx = s.llmContextSize,
            nThreads = inferenceThreads(),
            maxTokens = s.llmMaxTokens,
            useChatTemplate = s.useChatTemplate,
            stopAtJsonEnd = true,
        )
    }

    fun cancel() = LlamaNative.cancel()
}
