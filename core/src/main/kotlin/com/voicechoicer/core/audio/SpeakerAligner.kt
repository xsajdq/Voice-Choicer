package com.voicechoicer.core.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A speaker-labeled time range from a diarization pass (independent of any transcript segmentation). */
data class DiarizedSegment(val startMs: Long, val endMs: Long, val speakerId: Int)

/** Any time-ranged unit that needs a speaker assigned (e.g. a transcript segment). */
data class TimeRange(val startMs: Long, val endMs: Long)

/**
 * Speaker diarization and speech transcription are two independent passes over the same audio,
 * so their segment boundaries never line up exactly - a transcript segment might span two
 * diarization segments, or sit entirely inside one. This assigns each [TimeRange] the speaker of
 * whichever diarization segment overlaps it the most (by duration); if none overlap at all (a gap
 * in one pass but not the other), it falls back to the diarization segment whose midpoint is
 * closest in time. Returns null per range only when there is no diarization data at all.
 */
object SpeakerAligner {

    fun assignSpeakers(ranges: List<TimeRange>, diarization: List<DiarizedSegment>): List<Int?> {
        if (diarization.isEmpty()) return ranges.map { null }

        return ranges.map { range ->
            val bestByOverlap = diarization.maxByOrNull { overlapMs(range, it) }
            if (bestByOverlap != null && overlapMs(range, bestByOverlap) > 0) {
                bestByOverlap.speakerId
            } else {
                val rangeMid = (range.startMs + range.endMs) / 2
                diarization.minBy { abs(((it.startMs + it.endMs) / 2) - rangeMid) }.speakerId
            }
        }
    }

    private fun overlapMs(range: TimeRange, segment: DiarizedSegment): Long =
        max(0L, min(range.endMs, segment.endMs) - max(range.startMs, segment.startMs))
}
