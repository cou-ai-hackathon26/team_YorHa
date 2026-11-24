package com.medidiary.app.audio

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechGateTest {
    private val sr = 16_000
    private val chunk = 3_200 // 200 ms, like the real recorder

    /** Silence / noise with optional "speech" (a loud 220 Hz tone) between [speechFromMs] and [speechToMs]. */
    private fun signal(totalMs: Long, speechFromMs: Long? = null, speechToMs: Long = Long.MAX_VALUE, noise: Int = 30, seed: Int = 1): ShortArray {
        val rnd = Random(seed)
        val n = (totalMs * sr / 1000).toInt()
        return ShortArray(n) { i ->
            val tMs = i * 1000L / sr
            val base = rnd.nextInt(-noise, noise + 1)
            val speaking = speechFromMs != null && tMs >= speechFromMs && tMs < speechToMs
            val v = if (speaking) base + 8000 * sin(2 * PI * 220 * i / sr) else base.toDouble()
            v.toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    private class Run(val written: Int, val stop: StopReason?, val stoppedAtMs: Long, val firstWrittenAtMs: Long?)

    /** Feeds [audio] chunk by chunk until the gate stops (or the audio ends = user tapped stop). */
    private fun run(audio: ShortArray, gate: SpeechGate = SpeechGate(sr)): Run {
        var written = 0
        var firstAt: Long? = null
        var i = 0
        while (i < audio.size) {
            val n = minOf(chunk, audio.size - i)
            val step = gate.onChunk(audio.copyOfRange(i, i + n), n)
            if (step.write.isNotEmpty() && firstAt == null) firstAt = gate.elapsedMs
            written += step.write.size
            i += n
            if (step.stop != null) return Run(written, step.stop, gate.elapsedMs, firstAt)
        }
        return Run(written, null, gate.elapsedMs, firstAt)
    }

    @Test fun pureSilence_stopsAt10s_withNothingWritten() {
        val r = run(signal(20_000, noise = 0))
        assertEquals(StopReason.NO_SPEECH, r.stop)
        assertEquals(0, r.written)
        assertEquals(10_000L, r.stoppedAtMs)
    }

    @Test fun roomNoise_isNotSpeech() {
        val r = run(signal(20_000, noise = 200))
        assertEquals(StopReason.NO_SPEECH, r.stop)
        assertEquals(0, r.written)
    }

    @Test fun speechAt3s_dropsLeadInSilence_keepsShortPreRoll() {
        val r = run(signal(20_000, speechFromMs = 3_000)) // audio ends at 20 s = user taps stop
        assertNull(r.stop)
        val writtenMs = r.written * 1000L / sr
        // 17 s of speech + ≤ 0.5 s pre-roll + detection latency rounding; the 3 s lead-in is gone.
        assertTrue("written=$writtenMs ms", writtenMs in 17_000L..17_700L)
    }

    @Test fun speechAt11s_isTooLate() {
        val r = run(signal(20_000, speechFromMs = 11_000))
        assertEquals(StopReason.NO_SPEECH, r.stop)
        assertEquals(0, r.written)
    }

    @Test fun speechAt9s_isAccepted() {
        val r = run(signal(20_000, speechFromMs = 9_000))
        assertNull(r.stop)
        assertTrue(r.written > 0)
    }

    @Test fun singleClick_isNotSpeech() {
        // 60 ms loud burst at 2 s, then silence.
        val r = run(signal(15_000, speechFromMs = 2_000, speechToMs = 2_060))
        assertEquals(StopReason.NO_SPEECH, r.stop)
    }

    @Test fun longSpeech_stopsAt60sFromTap() {
        val r = run(signal(90_000, speechFromMs = 2_000))
        assertEquals(StopReason.MAX_DURATION, r.stop)
        assertEquals(60_000L, r.stoppedAtMs)
        val writtenMs = r.written * 1000L / sr
        assertTrue("written=$writtenMs ms", writtenMs in 58_000L..58_600L)
    }

    @Test fun speakingRightAtTap_isDetected() {
        val r = run(signal(5_000, speechFromMs = 0))
        assertNull(r.stop)
        assertTrue(r.written * 1000L / sr >= 4_900)
    }

    @Test fun quietSpeakerAboveNoiseFloor_isDetected() {
        // ~-40 dBFS voice over ~-60 dBFS noise.
        val rnd = Random(3)
        val audio = ShortArray(8 * sr) { i ->
            val noise = rnd.nextInt(-30, 31)
            val voice = if (i >= 2 * sr) 450 * sin(2 * PI * 180 * i / sr) else 0.0
            (noise + voice).toInt().toShort()
        }
        val r = run(audio)
        assertNull(r.stop)
        assertTrue(r.written > 0)
    }
}
