package com.voicechoicer.core.audio

import kotlin.math.sqrt

/**
 * Rough voice-pitch estimation via time-domain autocorrelation. This is not
 * meant to compete with a real diarization model - it is a cheap, fully
 * offline signal to approximate "does this segment sound like the same
 * person as that one", which [SpeakerClusterer] then groups on. It works
 * best when speakers differ noticeably in pitch (e.g. an adult and a
 * child, or a lower vs. higher voice); two similar adult voices of the
 * same register will likely land in the same cluster.
 */
object PitchEstimator {

    /**
     * Returns the average fundamental frequency (Hz) across voiced frames in
     * [pcm], or 0.0 if no voiced (sufficiently loud, sufficiently periodic)
     * frame was found - e.g. the segment is silence or pure noise.
     */
    fun estimateAveragePitchHz(
        pcm: ShortArray,
        sampleRate: Int,
        frameMs: Int = 40,
        minHz: Int = 70,
        maxHz: Int = 400,
        voicingThreshold: Double = 0.35,
    ): Double {
        if (pcm.isEmpty() || sampleRate <= 0) return 0.0

        val frameSize = (sampleRate * frameMs) / 1000
        if (frameSize < 2) return 0.0

        val minLag = (sampleRate / maxHz).coerceAtLeast(1)
        val maxLag = (sampleRate / minHz).coerceAtMost(frameSize - 1)
        if (maxLag <= minLag) return 0.0

        val voicedPitches = mutableListOf<Double>()
        var frameStart = 0
        while (frameStart + frameSize <= pcm.size) {
            val pitch = estimateFramePitch(pcm, frameStart, frameSize, sampleRate, minLag, maxLag, voicingThreshold)
            if (pitch > 0.0) voicedPitches += pitch
            frameStart += frameSize
        }

        if (voicedPitches.isEmpty()) return 0.0
        return voicedPitches.sorted().let { it[it.size / 2] } // median: robust to octave-error outliers
    }

    private fun estimateFramePitch(
        pcm: ShortArray,
        start: Int,
        length: Int,
        sampleRate: Int,
        minLag: Int,
        maxLag: Int,
        voicingThreshold: Double,
    ): Double {
        var energy = 0.0
        for (i in start until start + length) energy += pcm[i].toDouble() * pcm[i]
        val rms = sqrt(energy / length)
        if (rms < 200.0) return 0.0 // treat as silence; below typical mic noise floor for PCM16

        val zeroLagEnergy = energy
        if (zeroLagEnergy <= 0.0) return 0.0

        var bestLag = -1
        var bestCorrelation = 0.0
        for (lag in minLag..maxLag) {
            var sum = 0.0
            val limit = length - lag
            for (i in 0 until limit) {
                sum += pcm[start + i].toDouble() * pcm[start + i + lag]
            }
            val normalized = sum / zeroLagEnergy
            if (normalized > bestCorrelation) {
                bestCorrelation = normalized
                bestLag = lag
            }
        }

        if (bestLag <= 0 || bestCorrelation < voicingThreshold) return 0.0
        return sampleRate.toDouble() / bestLag
    }
}
