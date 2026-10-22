package com.example.ymediaplayer.data

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Temporary QA probes: intentionally assert the expected user-facing contracts. */
class FullQaRegressionProbeTest {
    private lateinit var prefs: AppPreferences
    @Before fun prepare() { AppPreferences.clearCache(); prefs = AppPreferences(FakeSharedPreferences()) }
    @Test fun resetRestoresHdrDefault() {
        prefs.setHdrEnabled(false); prefs.resetAllSettingsToDefaults()
        assertTrue("Reset All should restore HDR=true", prefs.isHdrEnabled())
    }
    @Test fun resetRestoresSubtitleBackgroundDefault() {
        prefs.setSubtitleBackgroundEnabled(false); prefs.resetAllSettingsToDefaults()
        assertTrue("Reset All should restore subtitle background=true", prefs.isSubtitleBackgroundEnabled())
    }
    @Test fun resetRestoresCallPlaybackDefault() {
        prefs.setPlayDuringCallsEnabled(true); prefs.resetAllSettingsToDefaults()
        assertFalse("Reset All should restore play during calls=false", prefs.isPlayDuringCallsEnabled())
    }
    @Test fun resetRestoresArtworkDefault() {
        prefs.setMusicArtworkStyle("CARD"); prefs.resetAllSettingsToDefaults()
        assertEquals("Reset All should restore artwork=VINYL", "VINYL", prefs.getMusicArtworkStyle())
    }
    @Test fun settingsSoundChoiceReachesExistingSpeakerProfile() {
        prefs.setVideoRouteSoundMode("speakers", "CINEMA")
        prefs.setVideoSoundMode("CLEAR_VOICE")
        assertEquals("Settings selection should reach the speaker profile used by playback", "CLEAR_VOICE", prefs.getVideoRouteSoundMode("speakers"))
    }
}
