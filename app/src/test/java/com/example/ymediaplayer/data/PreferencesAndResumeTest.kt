package com.example.ymediaplayer.data

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PreferencesAndResumeTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var appPreferences: AppPreferences

    @Before
    fun setUp() {
        AppPreferences.clearCache()
        fakePrefs = FakeSharedPreferences()
        appPreferences = AppPreferences(fakePrefs)
    }

    @Test
    fun recordPlayback_incompleteVideo_savesProgressAndNotCompleted() {
        val uri = "content://media/external/video/media/101"
        appPreferences.recordPlayback(uri, position = 15000L, duration = 60000L)

        assertFalse(appPreferences.isCompleted(uri))
        assertEquals(15000L, appPreferences.getVideoProgress(uri))
        assertEquals(60000L, appPreferences.getVideoDuration(uri))
        assertTrue(appPreferences.getPlayedUris().contains(uri))
    }

    @Test
    fun recordPlayback_completedVideoNearEnd_marksCompletedAndReturnsZeroProgress() {
        val uri = "content://media/external/video/media/102"
        // 58 seconds into 60 second video (within 3 seconds of end)
        appPreferences.recordPlayback(uri, position = 58000L, duration = 60000L)

        assertTrue(appPreferences.isCompleted(uri))
        // Issue 5 requirement: completed videos restart from the beginning (0L)
        assertEquals(0L, appPreferences.getVideoProgress(uri))
    }

    @Test
    fun clearVideoProgress_removesAllStateAndHistory() {
        val uri = "content://media/external/video/media/103"
        appPreferences.recordPlayback(uri, position = 20000L, duration = 40000L)
        assertEquals(20000L, appPreferences.getVideoProgress(uri))

        appPreferences.clearVideoProgress(uri)
        assertEquals(0L, appPreferences.getVideoProgress(uri))
        assertEquals(0L, appPreferences.getVideoDuration(uri))
        assertFalse(appPreferences.isCompleted(uri))
        assertFalse(appPreferences.getPlayedUris().contains(uri))
    }

    @Test
    fun favorites_toggleWorksCorrectly() {
        val uri = "content://media/external/video/media/104"
        assertFalse(appPreferences.isFavorite(uri))

        appPreferences.toggleFavorite(uri)
        assertTrue(appPreferences.isFavorite(uri))

        appPreferences.toggleFavorite(uri)
        assertFalse(appPreferences.isFavorite(uri))
    }

    @Test
    fun pinnedFolders_pinAndUnpinWork() {
        val folderId = "camera_folder_1"
        assertFalse(appPreferences.isFolderPinned(folderId))

        appPreferences.pinFolder(folderId)
        assertTrue(appPreferences.isFolderPinned(folderId))

        appPreferences.unpinFolder(folderId)
        assertFalse(appPreferences.isFolderPinned(folderId))

        appPreferences.togglePinFolder(folderId)
        assertTrue(appPreferences.isFolderPinned(folderId))
    }

    @Test
    fun playedUris_ordersMostRecentFirstAndDeduplicates() {
        val uri1 = "content://media/external/video/media/1"
        val uri2 = "content://media/external/video/media/2"
        val uri3 = "content://media/external/video/media/3"

        appPreferences.saveVideoProgress(uri1, 1000L)
        appPreferences.saveVideoProgress(uri2, 2000L)
        appPreferences.saveVideoProgress(uri3, 3000L)

        // uri3 was played last, so it should be first
        assertEquals(listOf(uri3, uri2, uri1), appPreferences.getPlayedUris())

        // Re-play uri1, it should move to the top
        appPreferences.saveVideoProgress(uri1, 5000L)
        assertEquals(listOf(uri1, uri3, uri2), appPreferences.getPlayedUris())
    }

    @Test
    fun playbackSettings_defaultsAndUpdates() {
        assertEquals("AUTO", appPreferences.getResumeMode())
        appPreferences.setResumeMode("ASK")
        assertEquals("ASK", appPreferences.getResumeMode())

        assertEquals(10, appPreferences.getDoubleTapSeekSeconds())
        appPreferences.setDoubleTapSeekSeconds(15)
        assertEquals(15, appPreferences.getDoubleTapSeekSeconds())

        assertTrue(appPreferences.isHwAccelerationEnabled())
        appPreferences.setHwAccelerationEnabled(false)
        assertFalse(appPreferences.isHwAccelerationEnabled())

        assertEquals("off", appPreferences.getMemcMode())
        appPreferences.setMemcMode("high")
        assertEquals("high", appPreferences.getMemcMode())

        assertTrue(appPreferences.isKeepScreenAwake())
        appPreferences.setKeepScreenAwake(false)
        assertFalse(appPreferences.isKeepScreenAwake())
    }

    @Test
    fun gestureSettings_defaultsAndUpdates() {
        assertTrue(appPreferences.isBrightnessGestureEnabled())
        appPreferences.setBrightnessGestureEnabled(false)
        assertFalse(appPreferences.isBrightnessGestureEnabled())

        assertTrue(appPreferences.isVolumeGestureEnabled())
        appPreferences.setVolumeGestureEnabled(false)
        assertFalse(appPreferences.isVolumeGestureEnabled())

        assertEquals(2.0f, appPreferences.getPressHoldSpeed(), 0.01f)
        appPreferences.setPressHoldSpeed(2.5f)
        assertEquals(2.5f, appPreferences.getPressHoldSpeed(), 0.01f)
    }

    @Test
    fun subtitleSettings_defaultsAndUpdates() {
        assertEquals(18, appPreferences.getSubtitleFontSize())
        appPreferences.setSubtitleFontSize(24)
        assertEquals(24, appPreferences.getSubtitleFontSize())

        assertEquals(0xFFFFFFFFL, appPreferences.getSubtitleColor())
        appPreferences.setSubtitleColor(0xFFFFD700L)
        assertEquals(0xFFFFD700L, appPreferences.getSubtitleColor())

        assertTrue(appPreferences.isPauseOnHeadsetDisconnect())
        appPreferences.setPauseOnHeadsetDisconnect(false)
        assertFalse(appPreferences.isPauseOnHeadsetDisconnect())
    }

    @Test
    fun libraryAndMaintenanceSettings_resetAllRestoresDefaults() {
        appPreferences.setShowContinueWatching(false)
        appPreferences.setContinueWatchingLimit(5)
        appPreferences.setDoubleTapSeekSeconds(30)
        appPreferences.setResumeMode("START")
        appPreferences.setMemcMode("medium")

        assertFalse(appPreferences.isShowContinueWatching())
        assertEquals(5, appPreferences.getContinueWatchingLimit())
        assertEquals(30, appPreferences.getDoubleTapSeekSeconds())
        assertEquals("START", appPreferences.getResumeMode())

        appPreferences.resetAllSettingsToDefaults()

        assertTrue(appPreferences.isShowContinueWatching())
        assertEquals(20, appPreferences.getContinueWatchingLimit())
        assertEquals(10, appPreferences.getDoubleTapSeekSeconds())
        assertEquals("AUTO", appPreferences.getResumeMode())
        assertEquals("off", appPreferences.getMemcMode())
    }

    @Test
    fun playbackSpeed_rememberSpeedAndReset() {
        assertFalse(appPreferences.isRememberPlaybackSpeed())
        assertEquals(1.0f, appPreferences.getLastPlaybackSpeed(), 0.001f)

        appPreferences.setRememberPlaybackSpeed(true)
        appPreferences.setLastPlaybackSpeed(1.75f)

        assertTrue(appPreferences.isRememberPlaybackSpeed())
        assertEquals(1.75f, appPreferences.getLastPlaybackSpeed(), 0.001f)

        appPreferences.resetAllSettingsToDefaults()

        assertFalse(appPreferences.isRememberPlaybackSpeed())
        assertEquals(1.0f, appPreferences.getLastPlaybackSpeed(), 0.001f)
    }

    @Test
    fun playlistSettings_defaultsAndResetRestoresDefaults() {
        // AutoPlay default is true (by default ON), RememberPlaylistQueue default is true
        assertTrue(appPreferences.isAutoPlayNextEnabled())
        assertTrue(appPreferences.isRememberPlaylistQueue())

        // Can be toggled
        appPreferences.setAutoPlayNextEnabled(false)
        appPreferences.setRememberPlaylistQueue(false)
        assertFalse(appPreferences.isAutoPlayNextEnabled())
        assertFalse(appPreferences.isRememberPlaylistQueue())

        // Reset to defaults must restore both
        appPreferences.resetAllSettingsToDefaults()
        assertTrue(appPreferences.isAutoPlayNextEnabled())
        assertTrue(appPreferences.isRememberPlaylistQueue())
    }

    @Test
    fun soundModeSettings_defaultAndToggleWorks() {
        assertEquals("BALANCED", appPreferences.getMusicSoundMode())

        appPreferences.setMusicSoundMode("BASS_PUNCH")
        assertEquals("BASS_PUNCH", appPreferences.getMusicSoundMode())

        appPreferences.setMusicSoundMode("VOCAL_BOOST")
        assertEquals("VOCAL_BOOST", appPreferences.getMusicSoundMode())

        appPreferences.setMusicSoundMode("SPATIAL_3D")
        assertEquals("SPATIAL_3D", appPreferences.getMusicSoundMode())
    }

}

/**
 * In-memory implementation of SharedPreferences for fast, hermetic unit tests.
 */
class FakeSharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any>()

    override fun getAll(): MutableMap<String, *> = HashMap(data)

    override fun getString(key: String?, defValue: String?): String? =
        data[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (data[key] as? Set<String>)?.toMutableSet() ?: defValues

    override fun getInt(key: String?, defValue: Int): Int =
        (data[key] as? Number)?.toInt() ?: defValue

    override fun getLong(key: String?, defValue: Long): Long =
        (data[key] as? Number)?.toLong() ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float =
        (data[key] as? Number)?.toFloat() ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        data[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor(this)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    class FakeEditor(private val prefs: FakeSharedPreferences) : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()
        private var clear = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            if (key != null) temp[key] = values?.toSet()
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) temp[key] = null
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clear = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clear) prefs.data.clear()
            temp.forEach { (k, v) ->
                if (v == null) prefs.data.remove(k) else prefs.data[k] = v
            }
        }
    }
}
