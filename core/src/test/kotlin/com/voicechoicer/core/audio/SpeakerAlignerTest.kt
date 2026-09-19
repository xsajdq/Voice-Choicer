package com.voicechoicer.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeakerAlignerTest {

    @Test
    fun `no diarization data yields null for every range`() {
        val ranges = listOf(TimeRange(0, 1000), TimeRange(1000, 2000))

        val result = SpeakerAligner.assignSpeakers(ranges, emptyList())

        assertEquals(listOf(null, null), result)
    }

    @Test
    fun `range fully inside one diarization segment gets its speaker`() {
        val ranges = listOf(TimeRange(500, 1500))
        val diarization = listOf(
            DiarizedSegment(0, 900, speakerId = 0),
            DiarizedSegment(900, 3000, speakerId = 1),
        )

        val result = SpeakerAligner.assignSpeakers(ranges, diarization)

        assertEquals(listOf(1), result)
    }

    @Test
    fun `range spanning two segments picks the one with more overlap`() {
        // Range 0-1000ms overlaps speaker 0 for 800ms and speaker 1 for only 200ms.
        val ranges = listOf(TimeRange(0, 1000))
        val diarization = listOf(
            DiarizedSegment(0, 800, speakerId = 0),
            DiarizedSegment(800, 2000, speakerId = 1),
        )

        val result = SpeakerAligner.assignSpeakers(ranges, diarization)

        assertEquals(listOf(0), result)
    }

    @Test
    fun `range in a gap between segments falls back to nearest midpoint`() {
        // Gap from 1000-2000ms; range sits entirely inside the gap, closer to the second segment.
        val ranges = listOf(TimeRange(1600, 1800))
        val diarization = listOf(
            DiarizedSegment(0, 1000, speakerId = 0),
            DiarizedSegment(2000, 3000, speakerId = 1),
        )

        val result = SpeakerAligner.assignSpeakers(ranges, diarization)

        assertEquals(listOf(1), result)
    }

    @Test
    fun `multiple ranges are each assigned independently`() {
        val ranges = listOf(TimeRange(0, 500), TimeRange(500, 1000), TimeRange(1000, 1500))
        val diarization = listOf(
            DiarizedSegment(0, 500, speakerId = 0),
            DiarizedSegment(500, 1000, speakerId = 1),
            DiarizedSegment(1000, 1500, speakerId = 0),
        )

        val result = SpeakerAligner.assignSpeakers(ranges, diarization)

        assertEquals(listOf(0, 1, 0), result)
    }

    @Test
    fun `empty ranges yields empty result even with diarization data`() {
        val result = SpeakerAligner.assignSpeakers(emptyList(), listOf(DiarizedSegment(0, 100, 0)))
        assertEquals(emptyList<Int?>(), result)
    }
}
