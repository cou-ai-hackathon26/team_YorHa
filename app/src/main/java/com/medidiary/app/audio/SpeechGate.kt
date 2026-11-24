package com.medidiary.app.audio

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Energy-based voice activity detector. Pure Kotlin so it is JVM-testable.
 *
 * A 30 ms frame is "voiced" when it is clearly louder than the quietest audio heard so far
 * (the noise floor), or simply loud. Speech onset = [onsetFrames] voiced frames among the last
 * [windowFrames], so a single tap or click on the phone does not count as speech.
 */
class EnergyVad(
    sampleRate: Int = AudioRecorder.SAMPLE_RATE,
    private val aboveFloorDb: Double = 12.0,
    private val minSpeechDb: Double = -48.0,
    private val loudDb: Double = -30.0,
    private val windowFrames: Int = 10,
    private val onsetFrames: Int = 8,
) {
    val frameSize = sampleRate * 30 / 1000

    private var floorDb = Double.NaN
    private val window = ArrayDeque<Boolean>()
    private val pending = ShortArray(frameSize)
    private var pendingCount = 0

    /**
     * Feeds [n] samples. Returns the index (within this call's [samples]) of the sample at which
     * speech onset was confirmed, or -1 if no onset yet.
     */
    fun feed(samples: ShortArray, n: Int = samples.size): Int {
        var i = 0
        while (i < n) {
            val take = minOf(frameSize - pendingCount, n - i)
            System.arraycopy(samples, i, pending, pendingCount, take)
            pendingCount += take
            i += take
            if (pendingCount == frameSize) {
                pendingCount = 0
                if (onFrame(frameDb(pending))) return i - 1
            }
        }
        return -1
    }

    private fun onFrame(db: Double): Boolean {
        floorDb = when {
            floorDb.isNaN() -> db
            db < floorDb -> db
            else -> floorDb + 0.02 // creep up slowly so a steady fan noise becomes the floor
        }
        val voiced = db >= loudDb || (db >= minSpeechDb && db >= floorDb + aboveFloorDb)
        window.addLast(voiced)
        if (window.size > windowFrames) window.removeFirst()
        return window.count { it } >= onsetFrames
    }

    companion object {
        /** RMS level of a frame in dBFS (0 = full scale, silence ≈ -90). */
        fun frameDb(frame: ShortArray, n: Int = frame.size): Double {
            var sum = 0.0
            for (k in 0 until n) {
                val s = frame[k].toDouble()
                sum += s * s
            }
            val rms = sqrt(sum / max(1, n)) / 32768.0
            return 20 * log10(max(rms, 1e-5))
        }
    }
}

enum class RecPhase { LISTENING, RECORDING }

enum class StopReason { NO_SPEECH, MAX_DURATION }

/**
 * Task 3 state machine, driven by the recorder one microphone chunk at a time.
 *
 *  - LISTENING: the first [silenceTimeoutMs] after the mic tap. Nothing is written; only the
 *    last [preRollMs] are kept so the first syllable isn't clipped. No speech in time → NO_SPEECH.
 *  - RECORDING: from speech onset, everything is written until [maxMs] after the mic tap
 *    (MAX_DURATION) or the user taps stop. The silent lead-in is never written, so Whisper
 *    never sees it.
 */
class SpeechGate(
    private val sampleRate: Int = AudioRecorder.SAMPLE_RATE,
    silenceTimeoutMs: Long = AudioRecorder.SILENCE_TIMEOUT_MS,
    maxMs: Long = AudioRecorder.MAX_SECONDS * 1000L,
    preRollMs: Long = 500,
    private val vad: EnergyVad = EnergyVad(sampleRate),
) {
    class Step(val write: ShortArray, val stop: StopReason?)

    private val silenceSamples = silenceTimeoutMs * sampleRate / 1000
    private val maxSamples = maxMs * sampleRate / 1000
    private val ring = ShortArray((preRollMs * sampleRate / 1000).toInt())
    private var ringStart = 0
    private var ringCount = 0

    var phase = RecPhase.LISTENING
        private set

    /** Samples consumed since the mic tap. */
    var consumed = 0L
        private set

    /** Samples handed out for writing (= length of the saved audio). */
    var written = 0L
        private set

    val elapsedMs: Long get() = consumed * 1000 / sampleRate

    fun onChunk(chunk: ShortArray, n: Int = chunk.size): Step {
        val room = (maxSamples - consumed).coerceAtLeast(0).toInt()
        val len = minOf(n, room)
        consumed += len
        val atCap = consumed >= maxSamples

        if (phase == RecPhase.RECORDING) {
            val out = chunk.copyOf(len)
            written += len
            return Step(out, if (atCap) StopReason.MAX_DURATION else null)
        }

        // LISTENING
        val onset = vad.feed(chunk, len)
        if (onset < 0) {
            pushRing(chunk, len)
            val stop = when {
                consumed >= silenceSamples -> StopReason.NO_SPEECH
                atCap -> StopReason.NO_SPEECH
                else -> null
            }
            return Step(EMPTY, stop)
        }
        // Speech: the pre-roll (audio just before this chunk) + this whole chunk.
        phase = RecPhase.RECORDING
        val pre = drainRing()
        val out = ShortArray(pre.size + len)
        System.arraycopy(pre, 0, out, 0, pre.size)
        System.arraycopy(chunk, 0, out, pre.size, len)
        written += out.size
        return Step(out, if (atCap) StopReason.MAX_DURATION else null)
    }

    private fun pushRing(src: ShortArray, n: Int) {
        if (ring.isEmpty()) return
        for (k in 0 until n) {
            val idx = (ringStart + ringCount) % ring.size
            ring[idx] = src[k]
            if (ringCount < ring.size) ringCount++ else ringStart = (ringStart + 1) % ring.size
        }
    }

    private fun drainRing(): ShortArray {
        val out = ShortArray(ringCount) { ring[(ringStart + it) % ring.size] }
        ringStart = 0
        ringCount = 0
        return out
    }

    private companion object {
        val EMPTY = ShortArray(0)
    }
}
