package com.voicechoicer.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class PitchEstimatorTest {

    private val sampleRate = 16000

    private fun tone(hz: Double, durationSec: Double, amplitude: Double = 10000.0): ShortArray {
        val count = (sampleRate * durationSec).toInt()
        return ShortArray(count) { i -> (sin(2 * Math.PI * hz * i / sampleRate) * amplitude).toInt().toShort() }
    }

    @Test
    fun `estimates a low tone close to its true frequency`() {
        val pitch = PitchEstimator.estimateAveragePitchHz(tone(120.0, 0.5), sampleRate)
        assertTrue("pitch=$pitch", pitch in 110.0..130.0)
    }

    @Test
    fun `estimates a high tone close to its true frequency`() {
        val pitch = PitchEstimator.estimateAveragePitchHz(tone(300.0, 0.5), sampleRate)
        assertTrue("pitch=$pitch", pitch in 280.0..320.0)
    }

    @Test
    fun `silence yields zero`() {
        val pitch = PitchEstimator.estimateAveragePitchHz(ShortArray(sampleRate), sampleRate)
        assertEquals(0.0, pitch, 0.001)
    }

    @Test
    fun `low amplitude noise floor yields zero`() {
        val quietNoise = ShortArray(sampleRate) { (it % 7 - 3).toShort() } // tiny, non-periodic wiggle
        val pitch = PitchEstimator.estimateAveragePitchHz(quietNoise, sampleRate)
        assertEquals(0.0, pitch, 0.001)
    }

    @Test
    fun `empty input is handled gracefully`() {
        assertEquals(0.0, PitchEstimator.estimateAveragePitchHz(ShortArray(0), sampleRate), 0.001)
    }
}
