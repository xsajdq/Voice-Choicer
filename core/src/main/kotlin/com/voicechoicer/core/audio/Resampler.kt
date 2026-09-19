package com.voicechoicer.core.audio

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Minimal linear-interpolation resampler for mono PCM16. Good enough to
 * bring device-native audio (44.1/48 kHz) down to whatever fixed rate an
 * offline speech recognizer expects - not broadcast-quality, but speech
 * intelligibility survives linear interpolation just fine.
 */
object Resampler {

    fun resample(pcm: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        if (pcm.isEmpty() || fromRate <= 0 || toRate <= 0 || fromRate == toRate) return pcm

        val outputLength = ((pcm.size.toLong() * toRate) / fromRate).toInt()
        if (outputLength <= 0) return ShortArray(0)

        val output = ShortArray(outputLength)
        val ratio = fromRate.toDouble() / toRate.toDouble()
        for (i in 0 until outputLength) {
            val srcPos = i * ratio
            val srcIndex = srcPos.toInt()
            val frac = srcPos - srcIndex
            val a = pcm[min(srcIndex, pcm.size - 1)]
            val b = pcm[min(srcIndex + 1, pcm.size - 1)]
            output[i] = (a + (b - a) * frac).roundToInt().toShort()
        }
        return output
    }
}
