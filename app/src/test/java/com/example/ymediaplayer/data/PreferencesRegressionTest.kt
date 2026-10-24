package com.example.ymediaplayer.data

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Regression coverage for settings contracts. */
class PreferencesRegressionTest {
    private lateinit var storage: FakeSharedPreferences
    private lateinit var prefs: AppPreferences
    @Before fun prepare() { AppPreferences.clearCache(); storage = FakeSharedPreferences(); prefs = AppPreferences(storage) }
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
    @Test fun resetRestoresAudioAppearanceAndUpdaterWithoutDeletingLibraryData() {
        prefs.saveEqSettings("Custom", listOf(3f, 2f, 1f, 0f, -1f), 0.9f, 0.8f, false)
        prefs.setMusicSoundMode("CUSTOM")
        prefs.setUiCornerStyle("ROUND")
        prefs.setAutoCheckUpdatesEnabled(false)
        prefs.setSkippedUpdateTag("v99")
        prefs.toggleFavorite("video")
        // Android's JSONObject is a stub in local JVM tests; preserve serialized library data verbatim.
        val playlists = """{"Keep":["song"]}"""
        val bookmarks = """[{"pos":5000,"label":"Keep bookmark"}]"""
        storage.edit().putString("custom_playlists_json", playlists).putString("bookmarks_video", bookmarks).apply()
        prefs.saveVideoProgress("video", 20000L, 60000L)
        prefs.resetAllSettingsToDefaults()
        assertEquals("Flat", prefs.getEqPreset())
        assertEquals(listOf(0f, 0f, 0f, 0f, 0f), prefs.getEqBands())
        assertTrue(prefs.isEqEnabled())
        assertEquals("BALANCED", prefs.getMusicSoundMode())
        assertEquals("EXTRA", prefs.getUiCornerStyle())
        assertTrue(prefs.isAutoCheckUpdatesEnabled())
        assertNull(prefs.getSkippedUpdateTag())
        assertTrue(prefs.isFavorite("video"))
        assertEquals(playlists, storage.getString("custom_playlists_json", null))
        assertEquals(bookmarks, storage.getString("bookmarks_video", null))
        assertEquals(20000L, prefs.getVideoProgress("video"))
    }

    @Test fun resumePolicyAlwaysHonorsStartAndCompletedVideos() {
        prefs.saveVideoProgress("video", 20000L, 60000L)
        prefs.setResumeMode("START")
        assertEquals(0L, prefs.getVideoStartPosition("video"))
        prefs.setResumeMode("AUTO")
        assertEquals(20000L, prefs.getVideoStartPosition("video"))
        prefs.markCompleted("video", true)
        assertEquals(0L, prefs.getVideoStartPosition("video"))
    }

    @Test fun playbackHistoryInvalidatesOtherPreferenceReadersImmediately() {
        val storage = FakeSharedPreferences()
        val writer = AppPreferences(storage)
        val screenReader = AppPreferences(storage)
        val revision = screenReader.lastProgressUpdate.longValue
        writer.recordMusicPlayed("new-song")
        assertTrue(screenReader.lastProgressUpdate.longValue > revision)
        assertEquals(listOf("new-song"), screenReader.getRecentlyPlayedMusicUris())
    }

}
