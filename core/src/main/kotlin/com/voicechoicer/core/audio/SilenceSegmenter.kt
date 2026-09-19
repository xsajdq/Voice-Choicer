package com.voicechoicer.core.audio

import com.voicechoicer.core.model.SpeechSegment
import kotlin.math.sqrt

/**
 * Splits a mono PCM16 signal into speech segments using simple frame-energy
 * VAD. This is the fallback path used when the user doesn't supply a
 * subtitle file: it has no idea *who* is speaking, only *when* - the app
 * pairs it with manually-typed lines and manual character assignment.
 *
 * The threshold is adaptive: we estimate the noise floor as the 20th
 * percentile of per-frame RMS energy, then require a frame to be
 * [thresholdMultiplier]x louder than that floor to count as speech. This
 * keeps it usable across clips with very different background noise
 * levels without any external calibration step.
 */
object SilenceSegmenter {

    fun segment(
        pcm: ShortArray,
        sampleRate: Int,
        frameMs: Int = 20,
        minSilenceMs: Long = 350,
        minSpeechMs: Long = 200,
        paddingMs: Long = 80,
        thresholdMultiplier: Double = 2.5,
    ): List<SpeechSegment> {
        if (pcm.isEmpty() || sampleRate <= 0) return emptyList()

        val frameSize = max1((sampleRate * frameMs) / 1000)
        val frameCount = (pcm.size + frameSize - 1) / frameSize
        if (frameCount == 0) return emptyList()

        val energies = DoubleArray(frameCount)
        for (f in 0 until frameCount) {
            val start = f * frameSize
            val end = minOf(start + frameSize, pcm.size)
            var sumSquares = 0.0
            for (i in start until end) {
                val normalized = pcm[i] / 32768.0
                sumSquares += normalized * normalized
            }
            energies[f] = sqrt(sumSquares / max1(end - start))
        }

        val noiseFloor = percentile(energies, 0.20).coerceAtLeast(1e-6)
        val threshold = noiseFloor * thresholdMultiplier

        val minSilenceFrames = max1((minSilenceMs / frameMs).toInt())
        val minSpeechFrames = max1((minSpeechMs / frameMs).toInt())

        val rawSegments = mutableListOf<IntRange>()
        var speechStart = -1
        var silenceRun = 0
        for (f in 0 until frameCount) {
            val isSpeech = energies[f] >= threshold
            if (isSpeech) {
                if (speechStart == -1) speechStart = f
                silenceRun = 0
            } else if (speechStart != -1) {
                silenceRun++
                if (silenceRun >= minSilenceFrames) {
                    val speechEnd = f - silenceRun
                    if (speechEnd - speechStart + 1 >= minSpeechFrames) {
                        rawSegments += speechStart..speechEnd
                    }
                    speechStart = -1
                    silenceRun = 0
                }
            }
        }
        if (speechStart != -1) {
            val speechEnd = frameCount - 1 - silenceRun
            if (speechEnd >= speechStart && speechEnd - speechStart + 1 >= minSpeechFrames) {
                rawSegments += speechStart..speechEnd
            }
        }

        val totalMs = pcm.size.toLong() * 1000L / sampleRate
        return rawSegments.map { range ->
            val startMs = (range.first.toLong() * frameMs - paddingMs).coerceAtLeast(0)
            val endMs = (range.last.toLong() * frameMs + frameMs + paddingMs).coerceAtMost(totalMs)
            SpeechSegment(startMs, endMs)
        }
    }

    private fun percentile(values: DoubleArray, p: Double): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sortedArray()
        val idx = (p * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
        return sorted[idx]
    }

    private fun max1(v: Int): Int = if (v < 1) 1 else v
}
