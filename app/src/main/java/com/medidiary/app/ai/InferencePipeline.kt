package com.medidiary.app.ai

import com.medidiary.app.data.SettingsStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Sequential memory manager (CLAUDE.md §4.1): a single mutex guarantees Whisper and Gemma are
 * never loaded at the same time. Each native call loads → infers → frees before releasing it.
 */
class InferencePipeline(
    private val models: ModelManager,
    private val settings: SettingsStore,
) {
    private val lock = Mutex()

    /** True when either engine is falling back to the scripted mock (no model file found). */
    fun isMockMode(): Boolean = !models.status.value.ready

    suspend fun transcribe(wav: File): String = lock.withLock {
        val file = models.refresh().whisper
        val engine: WhisperEngine = when {
            file != null -> NativeWhisperEngine(file) { settings.settings.value }
            settings.settings.value.allowMockEngines -> MockWhisperEngine()
            else -> error("Whisper model not installed. Open Settings → Models.")
        }
        engine.transcribe(wav)
    }

    suspend fun extract(transcript: String): String = lock.withLock {
        val file = models.refresh().gemma
        val engine: GemmaEngine = when {
            file != null -> NativeGemmaEngine(file) { settings.settings.value }
            settings.settings.value.allowMockEngines -> MockGemmaEngine()
            else -> error("Gemma model not installed. Open Settings → Models.")
        }
        engine.extract(transcript)
    }

    fun cancel() {
        // Safe to call even if nothing is running; libraries are loaded lazily on first use.
        runCatching { if (models.status.value.whisper != null) com.medidiary.whisper.WhisperNative.cancel() }
        runCatching { if (models.status.value.gemma != null) com.medidiary.llama.LlamaNative.cancel() }
    }
}
