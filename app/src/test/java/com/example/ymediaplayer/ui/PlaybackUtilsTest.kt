package com.example.ymediaplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackUtilsTest {

    @Test
    fun calculateSeekTarget_forwardWithinBounds() {
        val target = calculateSeekTarget(currentPosition = 5000L, deltaMs = 3000L, duration = 60000L)
        assertEquals(8000L, target)
    }

    @Test
    fun calculateSeekTarget_rewindWithinBounds() {
        val target = calculateSeekTarget(currentPosition = 5000L, deltaMs = -2000L, duration = 60000L)
        assertEquals(3000L, target)
    }

    @Test
    fun calculateSeekTarget_clampToZeroOnExcessiveRewind() {
        val target = calculateSeekTarget(currentPosition = 1000L, deltaMs = -5000L, duration = 60000L)
        assertEquals(0L, target)
    }

    @Test
    fun calculateSeekTarget_clampToDurationOnExcessiveForward() {
        val target = calculateSeekTarget(currentPosition = 55000L, deltaMs = 10000L, duration = 60000L)
        assertEquals(60000L, target)
    }

    @Test
    fun calculateSeekTarget_durationUnsetOrZero_preventsForwardSeek() {
        val targetForward = calculateSeekTarget(currentPosition = 5000L, deltaMs = 5000L, duration = -1L)
        assertEquals(5000L, targetForward)

        val targetForwardZeroDur = calculateSeekTarget(currentPosition = 2000L, deltaMs = 1000L, duration = 0L)
        assertEquals(2000L, targetForwardZeroDur)
    }

    @Test
    fun calculateSeekTarget_durationUnset_allowsRewindToZero() {
        val targetRewind = calculateSeekTarget(currentPosition = 5000L, deltaMs = -3000L, duration = -1L)
        assertEquals(2000L, targetRewind)

        val targetExcessiveRewind = calculateSeekTarget(currentPosition = 1000L, deltaMs = -5000L, duration = -1L)
        assertEquals(0L, targetExcessiveRewind)
    }

    @Test
    fun formatTime_formatsCorrectly() {
        assertEquals("00:00", formatTime(0L))
        assertEquals("00:00", formatTime(-500L))
        assertEquals("00:45", formatTime(45000L))
        assertEquals("03:25", formatTime(205000L))
        assertEquals("1:01:05", formatTime(3665000L))
    }

    @Test
    fun formatSize_formatsBytesCleanly() {
        assertEquals("0 B", formatSize(0L))
        assertEquals("500 B", formatSize(500L))
        assertEquals("1023 B", formatSize(1023L))
        assertEquals("1 KB", formatSize(1024L))
        assertEquals("500 KB", formatSize(512000L))
        assertEquals("10 MB", formatSize(10485760L))
        assertEquals("1.5 GB", formatSize(1610612736L))
    }
}
