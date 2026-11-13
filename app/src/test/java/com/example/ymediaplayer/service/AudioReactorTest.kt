package com.example.ymediaplayer.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioReactorTest {

    @Test
    fun smoothEma_risesAndDecaysWithoutStepJumps() {
        val risen = AudioReactor.smoothEma(previous = 0.1f, target = 1.0f, attackAlpha = 0.25f, releaseAlpha = 0.10f)
        assertTrue(risen > 0.1f && risen < 0.5f)
        assertEquals(0.325f, risen, 0.001f)

        val decayed = AudioReactor.smoothEma(previous = 0.8f, target = 0.0f, attackAlpha = 0.25f, releaseAlpha = 0.10f)
        assertEquals(0.72f, decayed, 0.001f)

        assertEquals(1.0f, AudioReactor.smoothEma(1.0f, 5.0f), 0.0001f)
        assertEquals(0.0f, AudioReactor.smoothEma(0.0f, -2.0f), 0.0001f)
    }

    @Test
    fun spatialSmooth3_smoothsNeighborsAndSupportsCircularWrap() {
        val spike = floatArrayOf(0f, 0f, 1f, 0f, 0f)
        val linear = AudioReactor.spatialSmooth3(spike, wrap = false)
        assertEquals(0.2f, linear[1], 0.001f)
        assertEquals(0.6f, linear[2], 0.001f)
        assertEquals(0.2f, linear[3], 0.001f)

        val edgeSpike = floatArrayOf(1f, 0f, 0f, 0f)
        val wrapped = AudioReactor.spatialSmooth3(edgeSpike, wrap = true)
        assertEquals(0.6f, wrapped[0], 0.001f)
        assertEquals(0.2f, wrapped[1], 0.001f)
        assertEquals(0.2f, wrapped[3], 0.001f)
    }

    @Test
    fun reset_clearsReactorState() {
        AudioReactor.reset()
        assertEquals(0f, AudioReactor.energy.value, 0.0001f)
        assertEquals(0f, AudioReactor.bassPulse.value, 0.0001f)
        assertEquals(AudioReactor.NUM_BANDS, AudioReactor.bands.value.size)
        assertEquals(AudioReactor.WAVEFORM_POINTS, AudioReactor.waveformData.value.size)
        assertTrue(AudioReactor.bands.value.all { it == 0f })
        assertTrue(AudioReactor.waveformData.value.all { it == 0f })
    }
}
