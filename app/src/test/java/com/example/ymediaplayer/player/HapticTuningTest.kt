package com.example.ymediaplayer.player

import org.junit.Assert.*
import org.junit.Test

class HapticTuningTest {
    private val impact = HapticSignal.impact(CinemaImpact.THUMP, 0.02, 0.8, 2.0)
    private val texture = HapticSignal.texture(FloatArray(16) { 0.3f })

    @Test fun defaultsPreserveTheAudioEnvelope() {
        for (signal in listOf(impact, texture)) {
            val result = HapticTuning().render(signal)!!
            assertArrayEquals(signal.amplitudes, result.amplitudes)
            assertArrayEquals(signal.timingsMs, result.timingsMs)
        }
    }

    @Test fun familiesCanBeIndependentlyDisabledOrScaled() {
        assertNull(HapticTuning(impacts = false).render(impact))
        assertNotNull(HapticTuning(impacts = false).render(texture))
        assertNull(HapticTuning(textures = false).render(texture))
        assertArrayEquals(texture.amplitudes, HapticTuning(impactStrength = 0.5f).render(texture)!!.amplitudes)
        assertTrue(HapticTuning(impactStrength = 0.5f).render(impact)!!.amplitudes.max() < impact.amplitudes.max())
    }

    @Test fun invalidPreferencesAreBounded() {
        val value = HapticTuning(strength = Float.NaN, detail = 4f, sensitivity = -2f, syncMs = 999).bounded()
        assertEquals(1f, value.strength)
        assertEquals(1f, value.detail)
        assertEquals(0.5f, value.sensitivity)
        assertEquals(250L, value.syncMs)
    }

    @Test fun previewsRespectFamilySwitchesAndTouchStrength() {
        val disabled = HapticTuning(impacts = false, textures = false)
        assertTrue(HapticPreviewSamples.create(HapticPreviewType.MIX, disabled).amplitudes.all { it == 0 })
        val quiet = HapticPreviewSamples.create(HapticPreviewType.TOUCH, disabled, 0.5f)
        val strong = HapticPreviewSamples.create(HapticPreviewType.TOUCH, disabled, 1.5f)
        assertTrue(strong.amplitudes.max() > quiet.amplitudes.max())
        assertEquals(26L, strong.durationMs)
    }

    @Test fun presetsAndDynamicsProduceDifferentAmplitudes() {
        assertTrue(HapticTuning.preset("Cinematic").render(impact)!!.amplitudes.max() >
            HapticTuning.preset("Subtle").render(impact)!!.amplitudes.max())
        assertFalse(HapticTuning(dynamics = 0f).render(texture)!!.amplitudes.contentEquals(
            HapticTuning(dynamics = 1f).render(texture)!!.amplitudes))
    }
}
