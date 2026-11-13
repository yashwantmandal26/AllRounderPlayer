package com.example.ymediaplayer.ui

import android.content.pm.ActivityInfo

/**
 * Pure 4-direction orientation & manual-override resolution for fluid video rotation.
 *
 * Quadrants follow [android.view.OrientationEventListener] degree conventions:
 * - [QUADRANT_UP] (0): 0° upright portrait
 * - [QUADRANT_RIGHT] (1): 90° clockwise (right-side-up / reverse landscape)
 * - [QUADRANT_DOWN] (2): 180° upside-down portrait
 * - [QUADRANT_LEFT] (3): 270° counter-clockwise (left-side-up / natural landscape)
 */
object VideoOrientationPolicy {
    const val QUADRANT_UNKNOWN = -1
    const val QUADRANT_UP = 0
    const val QUADRANT_RIGHT = 1
    const val QUADRANT_DOWN = 2
    const val QUADRANT_LEFT = 3

    enum class ManualOrientation {
        PORTRAIT,
        LANDSCAPE
    }

    /**
     * Maps sensor degrees [0, 360) into one of the 4 cardinal quadrants with hysteresis
     * deadzones around 45° diagonals so edge tilts do not flicker back and forth.
     */
    fun quadrantForDegrees(degrees: Int): Int {
        if (degrees < 0) return QUADRANT_UNKNOWN
        val norm = degrees % 360
        return when {
            norm >= 330 || norm <= 30 -> QUADRANT_UP
            norm in 60..120 -> QUADRANT_RIGHT
            norm in 150..210 -> QUADRANT_DOWN
            norm in 240..300 -> QUADRANT_LEFT
            else -> QUADRANT_UNKNOWN
        }
    }

    /**
     * Clears a temporary manual orientation override once the phone physically moves into
     * a different cardinal quadrant than where the override was triggered.
     * Returns `(nextManualOverride, updatedAnchorQuadrant)`.
     */
    fun updateManualOverrideOnSensor(
        manualOverride: ManualOrientation?,
        anchorQuadrant: Int,
        sensorQuadrant: Int
    ): Pair<ManualOrientation?, Int> {
        if (manualOverride == null) return null to sensorQuadrant
        if (sensorQuadrant == QUADRANT_UNKNOWN) return manualOverride to anchorQuadrant
        if (anchorQuadrant == QUADRANT_UNKNOWN) return manualOverride to sensorQuadrant
        return if (sensorQuadrant != anchorQuadrant) {
            null to sensorQuadrant
        } else {
            manualOverride to anchorQuadrant
        }
    }

    /**
     * Resolves the Android [ActivityInfo] screenOrientation constant for the player window:
     * - Locked screen -> [ActivityInfo.SCREEN_ORIENTATION_LOCKED]
     * - Manual override to landscape -> [ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE] (left & right)
     * - Manual override to portrait -> [ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT] (up & down)
     * - Active unlocked playback -> [ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR] (all 4 directions: up, down, left, right)
     */
    fun resolveRequestedOrientation(
        isLocked: Boolean,
        manualOverride: ManualOrientation?
    ): Int = when {
        isLocked -> ActivityInfo.SCREEN_ORIENTATION_LOCKED
        manualOverride == ManualOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        manualOverride == ManualOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        else -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
    }
}
