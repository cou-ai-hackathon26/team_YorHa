package com.medidiary.app.ai

import java.io.File
import kotlinx.coroutines.delay

interface WhisperEngine {
    suspend fun transcribe(wavFile: File): String
}

class MockWhisperEngine : WhisperEngine {
    override suspend fun transcribe(wavFile: File): String {
        delay(1000) // simulate ASR latency
        return "সকালে নাস্তার আগে একটা Esonix 20 খেয়েছি, এখনো বুক জ্বালাপোড়া করছে আর একটু gas লাগছে।"
    }
}
