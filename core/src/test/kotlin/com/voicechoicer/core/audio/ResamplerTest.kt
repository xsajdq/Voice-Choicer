package com.voicechoicer.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class ResamplerTest {

    @Test
    fun `downsampling produces expected output length`() {
        val pcm = ShortArray(44100) { 0 } // 1 second at 44100 Hz
        val result = Resampler.resample(pcm, fromRate = 44100, toRate = 16000)
        assertEquals(16000, result.size)
    }

    @Test
    fun `upsampling produces expected output length`() {
        val pcm = ShortArray(16000) { 0 } // 1 second at 16000 Hz
        val result = Resampler.resample(pcm, fromRate = 16000, toRate = 44100)
        assertEquals(44100, result.size)
    }

    @Test
    fun `same rate returns input unchanged`() {
        val pcm = shortArrayOf(1, 2, 3, 4, 5)
        val result = Resampler.resample(pcm, fromRate = 16000, toRate = 16000)
        assertEquals(pcm.toList(), result.toList())
    }

    @Test
    fun `preserves a low-frequency tone's period well enough to keep it recognizable`() {
        val sampleRate = 44100
        val toneHz = 150.0
        val durationSec = 0.5
        val pcm = ShortArray((sampleRate * durationSec).toInt()) { i ->
            (sin(2 * Math.PI * toneHz * i / sampleRate) * 10000).toInt().toShort()
        }

        val resampled = Resampler.resample(pcm, sampleRate, 16000)

        // Re-estimate the pitch of the resampled signal and expect it close to the original tone.
        val estimated = PitchEstimator.estimateAveragePitchHz(resampled, 16000)
        assertTrue("estimated=$estimated", estimated in 130.0..170.0)
    }

    @Test
    fun `empty input yields empty output`() {
        assertEquals(0, Resampler.resample(ShortArray(0), 44100, 16000).size)
    }
}
