package com.example.ymediaplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

class VideoAudioTuningTest {
    private val gains = listOf(-4f, -1f, 6f, 4.5f, 1.5f)

    @Test fun preservesReferenceBands() {
        listOf(50f, 250f, 1000f, 4000f, 12000f).forEachIndexed { index, hz ->
            assertEquals(gains[index], interpolateVideoEqGain(hz, gains), 0.0001f)
        }
    }

    @Test fun interpolatesAtLogarithmicMidpoint() {
        assertEquals(2.5f, interpolateVideoEqGain(sqrt(250f * 1000f), gains), 0.0001f)
    }

    @Test fun clampsOutsideReferenceRange() {
        assertEquals(-4f, interpolateVideoEqGain(20f, gains), 0f)
        assertEquals(1.5f, interpolateVideoEqGain(20000f, gains), 0f)
    }
}
