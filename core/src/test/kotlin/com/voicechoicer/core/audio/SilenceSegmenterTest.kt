package com.voicechoicer.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin
import kotlin.random.Random

class SilenceSegmenterTest {

    private val sampleRate = 16000

    /** Builds sine-wave "speech" bursts separated by near-silent gaps, with light noise throughout. */
    private fun buildSignal(burstsMs: List<Long>, gapMs: Long): ShortArray {
        val random = Random(42)
        val samples = mutableListOf<Short>()
        fun appendNoise(durationMs: Long, amplitude: Double) {
            val count = (durationMs * sampleRate / 1000).toInt()
            repeat(count) {
                samples += (random.nextDouble(-amplitude, amplitude)).toInt().toShort()
            }
        }
        fun appendTone(durationMs: Long) {
            val count = (durationMs * sampleRate / 1000).toInt()
            for (i in 0 until count) {
                val t = i.toDouble() / sampleRate
                val value = (sin(2 * Math.PI * 220.0 * t) * 12000).toInt() + random.nextInt(-200, 200)
                samples += value.coerceIn(-32768, 32767).toShort()
            }
        }

        appendNoise(gapMs, 40.0)
        for ((idx, burst) in burstsMs.withIndex()) {
            appendTone(burst)
            if (idx != burstsMs.lastIndex) appendNoise(gapMs, 40.0)
        }
        appendNoise(gapMs, 40.0)
        return samples.toShortArray()
    }

    @Test
    fun `detects a single speech burst surrounded by silence`() {
        val pcm = buildSignal(listOf(1000), gapMs = 600)

        val segments = SilenceSegmenter.segment(pcm, sampleRate, minSilenceMs = 300, minSpeechMs = 150, paddingMs = 0)

        assertEquals(1, segments.size)
        val seg = segments[0]
        // Burst starts at 600ms and lasts 1000ms; allow frame-quantization slack.
        assertTrue("startMs=${seg.startMs}", seg.startMs in 500..700)
        assertTrue("endMs=${seg.endMs}", seg.endMs in 1500..1700)
    }

    @Test
    fun `detects multiple bursts as separate segments`() {
        val pcm = buildSignal(listOf(500, 700, 400), gapMs = 500)

        val segments = SilenceSegmenter.segment(pcm, sampleRate, minSilenceMs = 300, minSpeechMs = 100, paddingMs = 0)

        assertEquals(3, segments.size)
        for (i in 0 until segments.size - 1) {
            assertTrue(segments[i].endMs <= segments[i + 1].startMs)
        }
    }

    @Test
    fun `silent input yields no segments`() {
        val pcm = ShortArray(sampleRate) // 1s of true silence
        val segments = SilenceSegmenter.segment(pcm, sampleRate)
        assertEquals(0, segments.size)
    }

    @Test
    fun `empty input is handled gracefully`() {
        assertEquals(0, SilenceSegmenter.segment(ShortArray(0), sampleRate).size)
        assertEquals(0, SilenceSegmenter.segment(ShortArray(10), 0).size)
    }
}
