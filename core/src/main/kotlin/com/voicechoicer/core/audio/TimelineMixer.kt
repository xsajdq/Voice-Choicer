package com.voicechoicer.core.audio

import kotlin.math.max
import kotlin.math.min

/** One recorded take, placed on the output timeline at [startMs]. */
data class TimelineClip(
    val startMs: Long,
    val pcm: ShortArray,
)

/**
 * Builds the final "dub" audio track: a silence-initialized buffer spanning
 * the whole clip duration, with each player's recorded take mixed in at the
 * timestamp of the fragment it belongs to. If two takes overlap (a
 * recording ran longer than the gap to the next line) they're mixed by
 * clamped addition rather than one clobbering the other.
 *
 * This is pure sample-array math so it can be unit tested without touching
 * any Android media APIs; the app layer is only responsible for decoding
 * takes to PCM and encoding the result back to AAC.
 */
object TimelineMixer {

    fun mix(totalDurationMs: Long, sampleRate: Int, clips: List<TimelineClip>): ShortArray {
        val totalSamples = max(0L, totalDurationMs * sampleRate / 1000L).toInt()
        val output = ShortArray(totalSamples)
        val accumulator = IntArray(totalSamples)

        for (clip in clips) {
            val startSample = (clip.startMs * sampleRate / 1000L).toInt()
            if (startSample >= totalSamples) continue
            val usableLength = min(clip.pcm.size, totalSamples - startSample)
            for (i in 0 until usableLength) {
                accumulator[startSample + i] += clip.pcm[i]
            }
        }

        for (i in accumulator.indices) {
            output[i] = accumulator[i].coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return output
    }
}
