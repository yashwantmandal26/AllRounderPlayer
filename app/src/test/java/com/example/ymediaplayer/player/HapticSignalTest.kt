package com.example.ymediaplayer.player

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class HapticSignalTest {
    @Test fun impactsHaveContinuouslyDifferentIntensityWithinTheSameCategory() {
        val quiet = HapticSignal.impact(CinemaImpact.THUMP, 0.009, 0.8, 2.0)
        val louder = HapticSignal.impact(CinemaImpact.THUMP, 0.02, 0.8, 2.0)
        assertEquals(quiet.shape, louder.shape)
        assertTrue(louder.amplitudes.max() > quiet.amplitudes.max())
        assertTrue(quiet.amplitudes.distinct().size > 3)
        assertEquals(0, quiet.amplitudes.last())
    }

    @Test fun bassClicksAndGrainHaveDistinctTemporalShapes() {
        val bass = HapticSignal.impact(CinemaImpact.THUMP, 0.02, 0.8, 2.0)
        val click = HapticSignal.impact(CinemaImpact.TAP, 0.02, 0.1, 2.0)
        val grain = HapticSignal.impact(CinemaImpact.TAP, 0.02, 0.1, 6.0)
        assertEquals(HapticShape.PUNCH, bass.shape)
        assertEquals(HapticShape.CLICK, click.shape)
        assertEquals(HapticShape.GRAIN, grain.shape)
        assertTrue(bass.durationMs > grain.durationMs && grain.durationMs > click.durationMs)
        assertTrue(grain.amplitudes[2] > grain.amplitudes[1])
    }

    @Test fun texturePacketsCanRiseAndModulateWithoutFixedStrengthSteps() {
        val swell = HapticSignal.texture(FloatArray(16) { 0.1f + it * 0.02f })
        val rumble = HapticSignal.texture(FloatArray(16) { 0.25f })
        val grain = HapticSignal.texture(FloatArray(16) { if (it % 2 == 0) 0.35f else 0.1f })
        assertEquals(HapticShape.SWELL, swell.shape)
        assertEquals(HapticShape.RUMBLE, rumble.shape)
        assertEquals(HapticShape.GRAIN, grain.shape)
        assertTrue(swell.amplitudes.last() > swell.amplitudes.first())
        assertTrue(swell.amplitudes.distinct().size > 10)
    }

    @Test fun playbackSpeedAndLateTrimmingPreservePacketAlignment() {
        val packet = HapticSignal.texture(FloatArray(16) { 0.2f + it * 0.005f })
        assertEquals(80L, packet.durationMs)
        assertEquals(40L, packet.atSpeed(2f).durationMs)
        assertEquals(27L, packet.atSpeed(3f).durationMs)
        assertEquals(160L, packet.atSpeed(0.5f).durationMs)
        assertEquals(63L, packet.trimStart(17)!!.durationMs)
        assertNull(packet.trimStart(80))
    }

    @Test fun sustainedBassHasAnEnvelopeAndDropsOutAfterSilence() {
        val detector = HapticDetector().apply { configure(48000, 1) }
        val textures = mutableListOf<Pair<Long, HapticSignal>>()
        repeat(96000) { frame ->
            val sample = if (frame < 48000) (0.10 + 0.2 * frame / 48000.0) * sin(2 * PI * 65 * frame / 48000) else 0.0
            detector.sample(sample, 0, frame * 1000000L / 48000)?.let {
                detector.signal?.takeIf { it.texture }?.let { signal -> textures.add(detector.impactTimeUs to signal) }
            }
        }
        assertTrue(textures.size >= 8)
        assertTrue(textures.first().first < 250000)
        assertTrue(textures.last().first < 1100000)
        assertTrue(textures.last().second.amplitudes.max() > textures.first().second.amplitudes.max())
    }
}
