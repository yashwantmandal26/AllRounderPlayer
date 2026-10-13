package com.example.ymediaplayer.player

import org.junit.Assert.*
import org.junit.Test

class VideoDynamicsTest {
    @Test fun nightModeReducesSustainedLoudPeaks() {
        val dynamics = VideoDynamics()
        var gain = 1f
        repeat(48_000) { gain = dynamics.frameGain(0.8f, 1f, false) }
        assertTrue("Loud scenes should be reduced", gain < 0.5f)
    }

    @Test fun levelingRaisesQuietSpeechButLeavesSilenceAlone() {
        val dynamics = VideoDynamics()
        assertEquals(1f, dynamics.frameGain(0f, 0f, true), 0.0001f)
        var gain = 1f
        repeat(96_000) { gain = dynamics.frameGain(0.05f, 0f, true) }
        assertTrue(gain > 2f && gain <= 3f)
    }

    @Test fun suddenPeaksCannotExceedCeilingAfterQuietSpeech() {
        val dynamics = VideoDynamics()
        repeat(96_000) { dynamics.frameGain(0.05f, 0f, true) }
        val gain = dynamics.frameGain(0.99f, 0f, true)
        assertTrue(0.99f * gain <= 0.95001f)
    }

    @Test fun resetRemovesGainFromPreviousVideo() {
        val dynamics = VideoDynamics()
        repeat(48_000) { dynamics.frameGain(0.8f, 1f, false) }
        dynamics.reset()
        assertEquals(1f, dynamics.frameGain(0f, 0f, false), 0.0001f)
    }
}
