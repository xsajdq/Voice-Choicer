package com.voicechoicer.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakerClustererTest {

    @Test
    fun `groups two well-separated voices into two clusters`() {
        // Low-pitched voice (~120 Hz) and high-pitched voice (~280 Hz), interleaved.
        val pitches = listOf(118.0, 282.0, 121.0, 279.0, 119.0, 285.0)

        val labels = SpeakerClusterer.cluster(pitches)

        assertEquals(6, labels.size)
        assertEquals("expected exactly 2 speaker clusters", 2, labels.toSet().size)
        assertEquals(labels[0], labels[2])
        assertEquals(labels[2], labels[4])
        assertEquals(labels[1], labels[3])
        assertEquals(labels[3], labels[5])
        assertTrue(labels[0] != labels[1])
    }

    @Test
    fun `keeps a single consistent voice as one cluster despite small natural variation`() {
        val pitches = listOf(150.0, 152.0, 148.0, 151.0)

        val labels = SpeakerClusterer.cluster(pitches)

        assertEquals(setOf(0), labels.toSet())
    }

    @Test
    fun `single fragment is its own cluster`() {
        assertEquals(listOf(0), SpeakerClusterer.cluster(listOf(150.0)))
    }

    @Test
    fun `empty input yields empty output`() {
        assertEquals(emptyList<Int>(), SpeakerClusterer.cluster(emptyList()))
    }

    @Test
    fun `labels are ordered by first appearance, not by pitch`() {
        val pitches = listOf(280.0, 120.0, 283.0, 118.0) // high voice speaks first

        val labels = SpeakerClusterer.cluster(pitches)

        assertEquals(0, labels[0]) // first-seen voice is always cluster 0
        assertEquals(2, labels.toSet().size)
    }

    @Test
    fun `respects maxClusters even with many distinct voices`() {
        val pitches = (0 until 10).map { 100.0 + it * 50.0 } // 10 voices, 50Hz apart each
        val labels = SpeakerClusterer.cluster(pitches, maxClusters = 3)
        assertTrue(labels.toSet().size <= 3)
    }

    @Test
    fun `three distinct voices yield three clusters`() {
        val pitches = listOf(100.0, 200.0, 300.0, 102.0, 198.0, 305.0)
        val labels = SpeakerClusterer.cluster(pitches)
        assertEquals(3, labels.toSet().size)
    }
}
