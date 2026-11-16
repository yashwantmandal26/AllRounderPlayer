package com.example.ymediaplayer.player

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class MusicHapticDetectorTest {
    private fun detect(rate: Int = 48000, channels: Int = 2, seconds: Double = 2.0,
                       audio: (Double, Int) -> Double): List<Pair<Long, HapticSignal>> {
        val detector = MusicHapticDetector().apply { configure(rate, channels) }
        val result = mutableListOf<Pair<Long, HapticSignal>>()
        repeat((rate * seconds).toInt()) { frame ->
            repeat(channels) { channel ->
                detector.sample(audio(frame.toDouble() / rate, channel), channel, frame * 1_000_000L / rate)?.let {
                    result.add(detector.impactTimeUs to requireNotNull(detector.signal))
                }
            }
        }
        return result
    }

    private fun kick(t: Double, frequency: Double = 65.0): Double {
        val age = t - (0.4 + ((t - 0.4) / 0.4).toInt() * 0.4)
        return if (t >= 0.4 && age in 0.0..0.16) 0.65 * exp(-age * 22) * sin(2 * PI * frequency * age) else 0.0
    }

    @Test fun bassDrumsAcrossMusicalPitchesStayResponsive() {
        for (frequency in listOf(45.0, 65.0, 85.0, 105.0)) {
            val hits = detect { t, _ -> kick(t, frequency) }
            assertEquals("Bass frequency $frequency: ${hits.map { it.first }}", 4, hits.size)
        }
    }

    @Test fun bassBeatsProduceSoftShortEnvelopesAcrossRatesAndOppositePhaseChannels() {
        for (rate in listOf(16000, 44100, 48000, 96000)) {
            for (channels in listOf(1, 2, 6)) {
                val hits = detect(rate, channels) { t, channel -> kick(t) * if (channel % 2 == 0) 1 else -1 }
                assertEquals("rate=$rate channels=$channels: ${hits.map { it.first }}", 4, hits.size)
                hits.forEachIndexed { index, (time, signal) ->
                    assertTrue("onset=$time", time - (400_000L + index * 400_000L) in -10_000..20_000)
                    assertFalse(signal.texture)
                    assertEquals(28L, signal.durationMs)
                    assertTrue(signal.amplitudes.max() <= 218)
                    assertEquals(0, signal.amplitudes.last())
                }
            }
        }
    }

    @Test fun silenceDcAndVocalRangeBurstsDoNotTrigger() {
        assertTrue(detect { _, _ -> 0.0 }.isEmpty())
        assertTrue(detect { _, _ -> 0.6 }.isEmpty())
        for (frequency in listOf(160, 220, 440, 1000, 3000)) {
            val hits = detect { t, _ ->
                if (t > 0.3 && t % 0.4 < 0.16) 0.65 * sin(2 * PI * frequency * t) else 0.0
            }
            assertTrue("vocal-range frequency=$frequency: ${hits.map { it.first }}", hits.isEmpty())
        }
    }

    @Test fun voicedHarmonicsAndSteadyBassDoNotBecomeContinuousBuzz() {
        val vocal = detect { t, _ ->
            val envelope = if (t > 0.3 && t % 0.4 < 0.2) 1.0 else 0.0
            envelope * (0.25 * sin(2 * PI * 90 * t) + 0.3 * sin(2 * PI * 180 * t) +
                0.2 * sin(2 * PI * 270 * t) + 0.1 * sin(2 * PI * 900 * t))
        }
        assertTrue("harmonic vocal: ${vocal.map { it.first }}", vocal.isEmpty())
        val steady = detect { t, _ -> if (t > 0.4) 0.6 * sin(2 * PI * 65 * t) else 0.0 }
        assertEquals(1, steady.size)
    }

    @Test fun kicksRemainDetectableUnderMidrangeVocals() {
        val hits = detect { t, _ -> kick(t) + 0.10 * sin(2 * PI * 240 * t) }
        assertEquals(4, hits.size)
    }

    @Test fun quieterKicksStillTriggerBelowLouderVocals() {
        val hits = detect { t, _ -> kick(t) * 0.35 + 0.30 * sin(2 * PI * 240 * t) }
        assertEquals("Mixed-song beats: ${hits.map { it.first }}", 4, hits.size)
    }

    @Test fun beatIntensityFollowsTheSongInsteadOfUsingOneFixedPulse() {
        val hits = detect { t, _ ->
            val gain = when { t < 0.7 -> 0.12; t < 1.1 -> 0.30; t < 1.5 -> 0.60; else -> 1.0 }
            kick(t) * gain
        }
        assertEquals(4, hits.size)
        val peaks = hits.map { it.second.amplitudes.max() }
        assertTrue("Dynamic amplitudes: $peaks", peaks.zipWithNext().all { (a, b) -> b > a + 8 })
        assertTrue(peaks.last() > peaks.first() * 1.6)
        assertTrue(hits.all { it.second.amplitudes.first() == it.second.amplitudes.max() })
    }

    @Test fun fastBeatRunsAreNotCutDownByTheMovieCooldown() {
        val hits = detect(seconds = 1.4) { t, _ ->
            val age = t - (0.4 + ((t - 0.4) / 0.125).toInt() * 0.125)
            if (t >= 0.4 && age in 0.0..0.1) 0.5 * exp(-age * 32) * sin(2 * PI * 65 * age) else 0.0
        }
        assertEquals("Fast beats: ${hits.map { it.first }}", 8, hits.size)
    }

    @Test fun resetDiscardsFilterHistoryAndPendingConfirmation() {
        val detector = MusicHapticDetector().apply { configure(48000, 2) }
        repeat(30000) { frame -> repeat(2) { channel ->
            detector.sample(kick(frame / 48000.0), channel, frame * 1_000_000L / 48000)
        } }
        detector.reset()
        assertNull(detector.signal)
        repeat(24000) { frame -> repeat(2) { channel ->
            assertNull(detector.sample(if (frame % 2 == 0) Double.NaN else Double.POSITIVE_INFINITY,
                channel, frame * 1_000_000L / 48000))
        } }
    }
}
