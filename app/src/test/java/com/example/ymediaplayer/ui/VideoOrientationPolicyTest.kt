package com.example.ymediaplayer.ui

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoOrientationPolicyTest {

    @Test
    fun `quadrantForDegrees maps all four cardinal directions and deadzones`() {
        assertEquals(VideoOrientationPolicy.QUADRANT_UP, VideoOrientationPolicy.quadrantForDegrees(0))
        assertEquals(VideoOrientationPolicy.QUADRANT_UP, VideoOrientationPolicy.quadrantForDegrees(350))
        assertEquals(VideoOrientationPolicy.QUADRANT_RIGHT, VideoOrientationPolicy.quadrantForDegrees(90))
        assertEquals(VideoOrientationPolicy.QUADRANT_DOWN, VideoOrientationPolicy.quadrantForDegrees(180))
        assertEquals(VideoOrientationPolicy.QUADRANT_LEFT, VideoOrientationPolicy.quadrantForDegrees(270))

        // Diagonal hysteresis bands & OrientationEventListener.ORIENTATION_UNKNOWN (-1)
        assertEquals(VideoOrientationPolicy.QUADRANT_UNKNOWN, VideoOrientationPolicy.quadrantForDegrees(-1))
        assertEquals(VideoOrientationPolicy.QUADRANT_UNKNOWN, VideoOrientationPolicy.quadrantForDegrees(45))
        assertEquals(VideoOrientationPolicy.QUADRANT_UNKNOWN, VideoOrientationPolicy.quadrantForDegrees(135))
        assertEquals(VideoOrientationPolicy.QUADRANT_UNKNOWN, VideoOrientationPolicy.quadrantForDegrees(225))
        assertEquals(VideoOrientationPolicy.QUADRANT_UNKNOWN, VideoOrientationPolicy.quadrantForDegrees(315))
    }

    @Test
    fun `manual override holds in same quadrant and clears immediately when tilted elsewhere`() {
        // Holding upright (UP) after tapping Rotate -> stays LANDSCAPE while phone stays UP
        val (kept, keptAnchor) = VideoOrientationPolicy.updateManualOverrideOnSensor(
            manualOverride = VideoOrientationPolicy.ManualOrientation.LANDSCAPE,
            anchorQuadrant = VideoOrientationPolicy.QUADRANT_UP,
            sensorQuadrant = VideoOrientationPolicy.QUADRANT_UP
        )
        assertEquals(VideoOrientationPolicy.ManualOrientation.LANDSCAPE, kept)
        assertEquals(VideoOrientationPolicy.QUADRANT_UP, keptAnchor)

        // Flat / unknown orientation also preserves override without changing anchor
        val (flatKept, flatAnchor) = VideoOrientationPolicy.updateManualOverrideOnSensor(
            manualOverride = VideoOrientationPolicy.ManualOrientation.LANDSCAPE,
            anchorQuadrant = VideoOrientationPolicy.QUADRANT_UP,
            sensorQuadrant = VideoOrientationPolicy.QUADRANT_UNKNOWN
        )
        assertEquals(VideoOrientationPolicy.ManualOrientation.LANDSCAPE, flatKept)
        assertEquals(VideoOrientationPolicy.QUADRANT_UP, flatAnchor)

        // Physically tilting left, right, or upside-down clears manual override back to 4-way auto
        val (cleared, nextAnchor) = VideoOrientationPolicy.updateManualOverrideOnSensor(
            manualOverride = VideoOrientationPolicy.ManualOrientation.LANDSCAPE,
            anchorQuadrant = VideoOrientationPolicy.QUADRANT_UP,
            sensorQuadrant = VideoOrientationPolicy.QUADRANT_LEFT
        )
        assertNull(cleared)
        assertEquals(VideoOrientationPolicy.QUADRANT_LEFT, nextAnchor)
    }

    @Test
    fun `resolveRequestedOrientation enables all four directions unless locked or manually overridden`() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,
            VideoOrientationPolicy.resolveRequestedOrientation(isLocked = false, manualOverride = null)
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            VideoOrientationPolicy.resolveRequestedOrientation(
                isLocked = false,
                manualOverride = VideoOrientationPolicy.ManualOrientation.LANDSCAPE
            )
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
            VideoOrientationPolicy.resolveRequestedOrientation(
                isLocked = false,
                manualOverride = VideoOrientationPolicy.ManualOrientation.PORTRAIT
            )
        )
        // Lock button overrides sensor and manual choices cleanly
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_LOCKED,
            VideoOrientationPolicy.resolveRequestedOrientation(
                isLocked = true,
                manualOverride = VideoOrientationPolicy.ManualOrientation.LANDSCAPE
            )
        )
    }
}
