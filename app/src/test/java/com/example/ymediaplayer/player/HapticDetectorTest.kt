package com.example.ymediaplayer.player

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class HapticDetectorTest {
    private fun detect(rate: Int = 48000, channels: Int = 2, chunk: Int = 1000,
                       signal: (Int, Int) -> Double): List<Pair<Long, CinemaImpact>> {
        val detector = HapticDetector().apply { configure(rate, channels) }
        val events = mutableListOf<Pair<Long, CinemaImpact>>()
        var offset = 0
        while (offset < rate * 2) {
            val end = minOf(offset + chunk, rate * 2)
            for (i in offset until end) for (channel in 0 until channels) {
                val time = i * 1_000_000L / rate
                detector.sample(signal(i, channel), channel, time)?.let { events.add(time to it) }
            }
            offset = end
        }
        return events
    }

    @Test fun silenceAndNearSilenceAreIgnored() {
        assertTrue(detect { _, _ -> 0.0 }.isEmpty())
        assertTrue(detect { i, _ -> if (i % 2 == 0) 0.001 else -0.001 }.isEmpty())
    }

    @Test fun sharpBurstTriggersAndOppositePhaseChannelsDoNotCancel() {
        val events = detect { i, channel ->
            if (i in 24000..24479) (if ((i + channel) % 2 == 0) 0.8 else -0.8) else 0.0
        }
        assertEquals(1, events.size)
        assertEquals(CinemaImpact.TAP, events.single().second)
    }

    @Test fun bassBurstProducesThumpAcrossSampleRatesAndChannels() {
        for (rate in listOf(44100, 48000, 96000)) for (channels in listOf(1, 2, 6)) {
            val events = detect(rate, channels) { i, channel ->
                if (i in rate / 2 until rate / 2 + rate / 10)
                    0.8 * sin(2 * PI * 65 * (i - rate / 2) / rate) * (if (channel % 2 == 0) 1 else -1)
                else 0.0
            }
            assertEquals("$rate/$channels", CinemaImpact.HEAVY_THUMP, events.first().second)
        }
    }

    @Test fun steadySignalsDoNotRepeatedlyFire() {
        assertTrue(detect { i, _ -> 0.6 * sin(2 * PI * 440 * i / 48000) }.isEmpty())
        val random = java.util.Random(2)
        val noise = DoubleArray(96000) { random.nextDouble() * 1.2 - 0.6 }
        assertTrue(detect { i, _ -> noise[i] }.isEmpty())
    }

    @Test fun bufferBoundariesDoNotChangeDetection() {
        val signal: (Int, Int) -> Double = { i, _ ->
            if (i in 24000..24479 || i in 60000..60479) (if (i % 2 == 0) 0.8 else -0.8) else 0.0
        }
        assertEquals(detect(chunk = 1, signal = signal), detect(chunk = 511, signal = signal))
        assertEquals(detect(chunk = 511, signal = signal), detect(chunk = 96000, signal = signal))
    }

    @Test fun cooldownAndRearmingLimitRapidBursts() {
        val events = detect { i, _ ->
            if (i > 24000 && i % 4800 < 480) (if (i % 2 == 0) 0.8 else -0.8) else 0.0
        }
        assertTrue(events.size > 1)
        events.zipWithNext().forEach { (a, b) -> assertTrue(b.first - a.first >= 180000) }
    }

    @Test fun invalidSamplesDoNotPoisonDetector() {
        assertTrue(detect { _, _ -> Double.NaN }.isEmpty())
    }

    @Test fun inaudibleDcBiasDoesNotBecomeContinuousRumble() {
        assertTrue(detect { _, _ -> 0.25 }.isEmpty())
    }

    @Test fun quietBassImpactsAreNotLostToTheBroadbandLoudnessFloor() {
        val events = detect { i, _ ->
            if (i in 24000 until 28800) 0.15 * sin(2 * PI * 65 * (i - 24000) / 48000)
            else 0.0
        }
        assertEquals(listOf(CinemaImpact.THUMP), events.map { it.second })
    }

    @Test fun bassImpactCanBeFoundOverContinuousMidrangeAudio() {
        val events = detect { i, _ ->
            0.25 * sin(2 * PI * 900 * i / 48000) +
                if (i in 24000 until 28800) 0.35 * sin(2 * PI * 65 * (i - 24000) / 48000) else 0.0
        }
        assertEquals(listOf(CinemaImpact.HEAVY_THUMP), events.map { it.second })
    }

    @Test fun bassStrengthTracksMasteredAudioLevels() {
        for ((amplitude, expected) in listOf(0.1 to CinemaImpact.SOFT_THUMP,
            0.15 to CinemaImpact.THUMP, 0.3 to CinemaImpact.HEAVY_THUMP)) {
            val events = detect { i, _ ->
                if (i in 24000 until 28800) amplitude * sin(2 * PI * 65 * (i - 24000) / 48000)
                else 0.0
            }
            assertEquals("amplitude=$amplitude", listOf(expected), events.map { it.second })
        }
    }

    @Test fun softerTransientsProduceLighterTaps() {
        val events = detect { i, _ ->
            if (i in 24000..24479) (if (i % 2 == 0) 0.15 else -0.15) else 0.0
        }
        assertEquals(listOf(CinemaImpact.TICK), events.map { it.second })
    }

    @Test fun confirmationKeepsTheOriginalOnsetTimestamp() {
        val detector = HapticDetector().apply { configure(48000, 1) }
        var detectedAt = 0L
        repeat(26000) { i ->
            val time = i * 1000000L / 48000
            val sample = if (i in 24000..24479) (if (i % 2 == 0) 0.8 else -0.8) else 0.0
            if (detector.sample(sample, 0, time) != null) detectedAt = time
        }
        assertEquals(500000L, detector.impactTimeUs)
        assertTrue(detectedAt - detector.impactTimeUs in 10000L..15000L)
    }
}
