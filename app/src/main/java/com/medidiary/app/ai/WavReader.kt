package com.medidiary.app.ai

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavReader {
    /** Reads a 16-bit PCM mono WAV (as written by AudioRecorder) into floats in [-1, 1]. */
    fun readPcm16MonoAsFloat(file: File): FloatArray {
        val bytes = file.readBytes()
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        // Walk RIFF chunks to find "data" instead of assuming a 44-byte header.
        var pos = 12
        var dataStart = 44
        var dataLen = bytes.size - 44
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val len = bb.getInt(pos + 4)
            if (id == "data") {
                dataStart = pos + 8
                dataLen = minOf(len, bytes.size - dataStart)
                break
            }
            pos += 8 + len + (len and 1)
        }
        val n = dataLen / 2
        val out = FloatArray(n)
        for (i in 0 until n) out[i] = bb.getShort(dataStart + i * 2) / 32768f
        return out
    }
}
