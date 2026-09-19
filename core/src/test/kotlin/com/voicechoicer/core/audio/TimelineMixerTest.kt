package com.voicechoicer.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineMixerTest {

    private val sampleRate = 1000 // 1 sample == 1 ms, makes the math trivial to verify

    @Test
    fun `places a clip at its start offset inside silence`() {
        val clipPcm = shortArrayOf(100, 200, 300)
        val result = TimelineMixer.mix(
            totalDurationMs = 10,
            sampleRate = sampleRate,
            clips = listOf(TimelineClip(startMs = 3, pcm = clipPcm)),
        )

        assertEquals(10, result.size)
        for (i in listOf(0, 1, 2, 6, 7, 8, 9)) assertEquals(0, result[i].toInt())
        assertEquals(100, result[3].toInt())
        assertEquals(200, result[4].toInt())
        assertEquals(300, result[5].toInt())
    }

    @Test
    fun `overlapping clips are mixed by clamped addition, not overwritten`() {
        val a = ShortArray(5) { 10000 }
        val b = ShortArray(5) { 10000 }
        val result = TimelineMixer.mix(
            totalDurationMs = 8,
            sampleRate = sampleRate,
            clips = listOf(TimelineClip(0, a), TimelineClip(3, b)),
        )

        // Samples 3,4 overlap -> summed; must not clip incorrectly nor silently drop one clip.
        assertEquals(20000, result[3].toInt())
        assertEquals(20000, result[4].toInt())
        assertEquals(10000, result[0].toInt())
        assertEquals(10000, result[7].toInt())
    }

    @Test
    fun `clips are clamped to short range on extreme overlap`() {
        val a = ShortArray(2) { Short.MAX_VALUE }
        val b = ShortArray(2) { Short.MAX_VALUE }
        val result = TimelineMixer.mix(4, sampleRate, listOf(TimelineClip(0, a), TimelineClip(0, b)))

        assertEquals(Short.MAX_VALUE, result[0])
        assertEquals(Short.MAX_VALUE, result[1])
    }

    @Test
    fun `clip extending past total duration is truncated safely`() {
        val clip = ShortArray(10) { 500 }
        val result = TimelineMixer.mix(5, sampleRate, listOf(TimelineClip(startMs = 3, pcm = clip)))

        assertEquals(5, result.size)
        assertEquals(500, result[3].toInt())
        assertEquals(500, result[4].toInt())
    }

    @Test
    fun `clip starting after total duration is ignored without crashing`() {
        val result = TimelineMixer.mix(5, sampleRate, listOf(TimelineClip(startMs = 100, pcm = shortArrayOf(1, 2, 3))))
        assertTrue(result.all { it.toInt() == 0 })
    }
}
