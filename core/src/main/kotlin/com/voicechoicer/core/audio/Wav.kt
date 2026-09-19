package com.voicechoicer.core.audio

import java.io.ByteArrayOutputStream

/**
 * Minimal PCM16 mono/stereo WAV (RIFF) encode/decode. AudioRecord gives us
 * headerless PCM, but we want takes stored as playable .wav files (so any
 * player, including Media3, can preview them directly) and we need to read
 * them back as raw PCM for mixing - hence both directions live here as pure
 * byte-array logic, independent of the Android APIs that produce/consume
 * the bytes.
 */
object Wav {

    fun encode(pcm: ShortArray, sampleRate: Int, channels: Int = 1): ByteArray {
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcm.size * 2
        val out = ByteArrayOutputStream(44 + dataSize)

        fun writeString(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun writeIntLE(v: Int) {
            out.write(v and 0xFF)
            out.write((v shr 8) and 0xFF)
            out.write((v shr 16) and 0xFF)
            out.write((v shr 24) and 0xFF)
        }
        fun writeShortLE(v: Int) {
            out.write(v and 0xFF)
            out.write((v shr 8) and 0xFF)
        }

        writeString("RIFF")
        writeIntLE(36 + dataSize)
        writeString("WAVE")
        writeString("fmt ")
        writeIntLE(16)
        writeShortLE(1) // PCM
        writeShortLE(channels)
        writeIntLE(sampleRate)
        writeIntLE(byteRate)
        writeShortLE(blockAlign)
        writeShortLE(bitsPerSample)
        writeString("data")
        writeIntLE(dataSize)
        for (sample in pcm) {
            writeShortLE(sample.toInt())
        }
        return out.toByteArray()
    }

    data class Decoded(val sampleRate: Int, val channels: Int, val pcm: ShortArray)

    fun decode(bytes: ByteArray): Decoded {
        require(bytes.size >= 44) { "Too small to be a WAV file" }
        fun u16(offset: Int) = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
        fun u32(offset: Int): Int =
            (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)

        var offset = 12 // past "RIFF"+size+"WAVE"
        var channels = 1
        var sampleRate = 44100
        var dataOffset = -1
        var dataSize = 0
        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = u32(offset + 4)
            val body = offset + 8
            when (chunkId) {
                "fmt " -> {
                    channels = u16(body + 2)
                    sampleRate = u32(body + 4)
                }
                "data" -> {
                    dataOffset = body
                    dataSize = chunkSize
                }
            }
            offset = body + chunkSize + (chunkSize and 1)
        }
        require(dataOffset >= 0) { "No data chunk found" }
        val sampleCount = dataSize / 2
        val pcm = ShortArray(sampleCount)
        for (i in 0 until sampleCount) {
            val p = dataOffset + i * 2
            pcm[i] = ((bytes[p].toInt() and 0xFF) or (bytes[p + 1].toInt() shl 8)).toShort()
        }
        return Decoded(sampleRate, channels, pcm)
    }
}
