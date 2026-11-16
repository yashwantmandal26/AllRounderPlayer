package com.example.ymediaplayer.player

import org.junit.Assert.*
import org.junit.Test

class HapticTimelineTest {
    @Test fun musicCanRenderFastBeatsWhileMovieCooldownStaysUnchanged() {
        for ((timeline, expected) in listOf(HapticTimeline(100L) to 8, HapticTimeline() to 4)) {
            var delivered = 0
            repeat(8) { index ->
                timeline.offer(index * 125000L, CinemaImpact.SOFT_THUMP, timeline.generation)
                if (timeline.due(index * 125000L, 1f, index * 125L) != null) delivered++
            }
            assertEquals(expected, delivered)
        }
    }

    @Test fun waitsForAudiblePositionAndMapsPlaybackSpeed() {
        val timeline = HapticTimeline()
        timeline.offer(1_000_000, CinemaImpact.TAP, timeline.generation)
        assertNull(timeline.due(990_000, 1f, 0))
        assertEquals(CinemaImpact.TAP, timeline.due(1_000_000, 1f, 10))
        assertEquals(1_100_000L, VideoHaptics.extrapolate(1_000_000, 50, 2f))
        assertEquals(1_025_000L, VideoHaptics.extrapolate(1_000_000, 50, 0.5f))
    }

    @Test fun lateEventsAreDroppedUsingWallTimeAtDifferentSpeeds() {
        val timeline = HapticTimeline()
        timeline.offer(1_000_000, CinemaImpact.TAP, timeline.generation)
        assertNull(timeline.due(1_100_000, 1f, 100))
        timeline.offer(1_000_000, CinemaImpact.TAP, timeline.generation)
        assertEquals(CinemaImpact.TAP, timeline.due(1_100_000, 2f, 100))
    }

    @Test fun invalidationRejectsQueuedAndInFlightEvents() {
        for (reason in listOf("pause", "seek", "disable", "release", "route", "buffering")) {
            val timeline = HapticTimeline()
            val old = timeline.generation
            timeline.offer(0, CinemaImpact.TAP, old)
            timeline.invalidate()
            timeline.offer(0, CinemaImpact.TAP, old)
            assertNull(reason, timeline.due(0, 1f, 100))
            timeline.offer(0, CinemaImpact.TAP, timeline.generation)
            assertEquals(reason, CinemaImpact.TAP, timeline.due(0, 1f, 100))
        }
    }

    @Test fun rollingBudgetAndCooldownSurviveDiscontinuities() {
        val timeline = HapticTimeline()
        var total = 0L
        repeat(25) { i ->
            timeline.invalidate()
            timeline.offer(0, CinemaImpact.THUMP, timeline.generation)
            total += timeline.due(0, 1f, i * 190L)?.durationMs ?: 0L
        }
        assertTrue(total in 350..400)
        timeline.offer(0, CinemaImpact.TAP, timeline.generation)
        assertNotNull(timeline.due(0, 1f, 6000))
    }

    @Test fun acceleratedPlaybackCannotProduceCatchupBurst() {
        val timeline = HapticTimeline()
        repeat(100) { timeline.offer(0, CinemaImpact.TAP, timeline.generation) }
        assertNotNull(timeline.due(0, 2f, 0))
        assertNull(timeline.due(0, 2f, 0))
    }

    @Test fun workerSleepsUntilNextImpactAndOnlyEmptyMailboxNeedsWaking() {
        val timeline = HapticTimeline()
        assertEquals(40L, timeline.nextDelayMs(0, 1f))
        assertTrue(timeline.offer(30000, CinemaImpact.TAP, timeline.generation))
        assertFalse(timeline.offer(250000, CinemaImpact.THUMP, timeline.generation))
        assertEquals(30L, timeline.nextDelayMs(0, 1f))
        assertEquals(15L, timeline.nextDelayMs(0, 2f))
        assertEquals(1L, timeline.nextDelayMs(31000, 1f))
        timeline.invalidate()
        assertEquals(40L, timeline.nextDelayMs(0, 1f))
        assertFalse(timeline.offer(0, CinemaImpact.TAP, timeline.generation - 1))
    }

    @Test fun lateTextureIsTrimmedAndEarlierPacketsAreOrderedByTheirAudioTimestamp() {
        val timeline = HapticTimeline()
        val texture = HapticSignal.texture(FloatArray(16) { 0.2f })
        timeline.offer(200000, CinemaImpact.TAP, timeline.generation)
        assertTrue(timeline.offer(100000, texture, timeline.generation))
        val due = timeline.dueSignal(120000, 1f, 120)!!
        assertTrue(due.texture)
        assertEquals(60L, due.durationMs)
        assertEquals(CinemaImpact.TAP, timeline.due(200000, 1f, 200))
    }

    @Test fun textureDoesNotUseTheImpactCooldownAndImpactsInterruptIt() {
        val timeline = HapticTimeline()
        val texture = HapticSignal.texture(FloatArray(16) { 0.2f })
        timeline.offer(0, texture, timeline.generation)
        timeline.offer(80000, texture, timeline.generation)
        timeline.offer(100000, CinemaImpact.TAP, timeline.generation)
        assertEquals(160L, timeline.dueSignal(0, 1f, 0)!!.durationMs)
        assertNull(timeline.dueSignal(80000, 1f, 80))
        assertEquals(CinemaImpact.TAP, timeline.due(100000, 1f, 100))
        timeline.offer(105000, texture, timeline.generation)
        assertNull(timeline.dueSignal(105000, 1f, 105))
    }

    @Test fun intensityIsAppliedContinuouslyBeforeExposureAccounting() {
        val wave = HapticSignal.texture(FloatArray(16) { 0.3f })
        val low = HapticTimeline().run { offer(0, wave, generation); dueSignal(0, 1f, 0, 0.5f)!! }
        val high = HapticTimeline().run { offer(0, wave, generation); dueSignal(0, 1f, 0, 1.25f)!! }
        assertTrue(high.amplitudes.max() > low.amplitudes.max() * 2)
        assertTrue(high.exposureMs > low.exposureMs * 4)
    }

    @Test fun adjacentKnownTexturesBatchWithoutInventingFutureAudioOrCrossingGaps() {
        val timeline = HapticTimeline()
        repeat(4) { i -> timeline.offer(i * 80000L, HapticSignal.texture(FloatArray(16) { 0.1f + i * 0.05f }), timeline.generation) }
        val first = timeline.dueSignal(0, 1f, 0)!!
        assertEquals(240L, first.durationMs)
        assertTrue(first.amplitudes.last() > first.amplitudes.first())
        assertNull(timeline.dueSignal(200000, 1f, 200))
        assertEquals(80L, timeline.dueSignal(240000, 1f, 240)!!.durationMs)
        timeline.invalidate()
        timeline.offer(400000, HapticSignal.texture(FloatArray(16) { 0.2f }), timeline.generation)
        timeline.offer(600000, HapticSignal.texture(FloatArray(16) { 0.2f }), timeline.generation)
        assertEquals(80L, timeline.dueSignal(400000, 1f, 400)!!.durationMs)
    }
}
