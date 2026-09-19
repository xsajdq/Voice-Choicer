package com.voicechoicer.core.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WavTest {

    @Test
    fun `encode then decode round-trips sample data, rate and channel count`() {
        val pcm = shortArrayOf(0, 100, -100, Short.MAX_VALUE, Short.MIN_VALUE, 42)
        val bytes = Wav.encode(pcm, sampleRate = 22050, channels = 1)

        val decoded = Wav.decode(bytes)

        assertEquals(22050, decoded.sampleRate)
        assertEquals(1, decoded.channels)
        assertArrayEquals(pcm, decoded.pcm)
    }

    @Test
    fun `header reports correct riff and data chunk sizes`() {
        val pcm = ShortArray(100) { it.toShort() }
        val bytes = Wav.encode(pcm, sampleRate = 44100)

        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
        assertEquals(36 + 200, littleEndianInt(bytes, 4))
        assertEquals(200, littleEndianInt(bytes, 40))
    }

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}
