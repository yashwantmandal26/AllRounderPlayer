package com.example.ymediaplayer.data

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class VideoHomeSectionTest {
    private lateinit var storage: FakeSharedPreferences
    private lateinit var prefs: AppPreferences
    private val uri = "content://media/external/video/media/42"

    @Before fun prepare() {
        AppPreferences.clearCache()
        storage = FakeSharedPreferences()
        prefs = AppPreferences(storage)
    }

    @Test fun removeOnlyAffectsItsOwnShelfAndPreservesResumeFavoritesAndHistory() {
        prefs.saveVideoProgress(uri, 20000L, 120000L)
        prefs.toggleFavorite(uri)
        val played = prefs.getPlayedUris()
        val revision = prefs.lastProgressUpdate.longValue
        prefs.hideHomeVideo(VideoHomeSection.CONTINUE_WATCHING, uri)
        assertTrue(prefs.getHiddenHomeVideos(VideoHomeSection.CONTINUE_WATCHING).contains(uri))
        assertTrue(prefs.getHiddenHomeVideos(VideoHomeSection.RECENTLY_ADDED).isEmpty())
        assertEquals(20000L, prefs.getVideoProgress(uri))
        assertEquals(120000L, prefs.getVideoDuration(uri))
        assertEquals(played, prefs.getPlayedUris())
        assertTrue(prefs.isFavorite(uri))
        assertFalse(prefs.isCompleted(uri))
        assertTrue(prefs.lastProgressUpdate.longValue > revision)
    }

    @Test fun removalSurvivesNewReadersProgressUpdatesAndSettingsReset() {
        prefs.hideHomeVideo(VideoHomeSection.RECENTLY_ADDED, uri)
        val anotherReader = AppPreferences(storage)
        assertEquals(setOf(uri), anotherReader.getHiddenHomeVideos(VideoHomeSection.RECENTLY_ADDED))
        prefs.saveVideoProgress(uri, 30000L, 120000L)
        prefs.resetAllSettingsToDefaults()
        assertEquals(setOf(uri), anotherReader.getHiddenHomeVideos(VideoHomeSection.RECENTLY_ADDED))
        assertEquals(30000L, anotherReader.getVideoProgress(uri))
        assertTrue(anotherReader.getHiddenHomeVideos(VideoHomeSection.CONTINUE_WATCHING).isEmpty())
    }

    @Test fun undoRestoresOnlyThatShelfWithoutChangingCurrentPlaybackProgress() {
        prefs.hideHomeVideo(VideoHomeSection.RECENTLY_ADDED, uri)
        prefs.hideHomeVideo(VideoHomeSection.CONTINUE_WATCHING, uri)
        prefs.saveVideoProgress(uri, 40000L, 120000L)
        prefs.restoreHomeVideo(VideoHomeSection.CONTINUE_WATCHING, uri)
        assertTrue(prefs.getHiddenHomeVideos(VideoHomeSection.CONTINUE_WATCHING).isEmpty())
        assertEquals(setOf(uri), prefs.getHiddenHomeVideos(VideoHomeSection.RECENTLY_ADDED))
        assertEquals(40000L, prefs.getVideoProgress(uri))
        assertEquals(listOf(uri), prefs.getPlayedUris())
    }
}
