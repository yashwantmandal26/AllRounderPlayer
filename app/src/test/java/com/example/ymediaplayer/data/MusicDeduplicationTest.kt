package com.example.ymediaplayer.data

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

class MusicDeduplicationTest {

    private val dummyUri: Uri = Mockito.mock(Uri::class.java)

    private fun createSong(
        id: Long,
        title: String,
        artist: String = "Test Artist",
        album: String = "Test Album",
        albumId: Long = 1L,
        duration: Long = 180_000L,
        size: Long = 5_000_000L,
        path: String = "/storage/emulated/0/Music/${id}_$title.mp3"
    ): MusicItem {
        return MusicItem(
            id = id,
            uri = dummyUri,
            title = title,
            artist = artist,
            album = album,
            albumId = albumId,
            duration = duration,
            size = size,
            path = path,
            relativePath = "Music/"
        )
    }

    @Test
    fun eliminatesDuplicateMediaStoreIds() {
        val s1 = createSong(id = 101L, title = "Song A")
        val s2 = createSong(id = 101L, title = "Song A Duplicate ID")
        val result = deduplicateMusicItems(listOf(s1, s2))
        assertEquals(1, result.size)
        assertEquals("Song A", result[0].title)
    }

    @Test
    fun eliminatesDuplicateFilePaths() {
        val s1 = createSong(id = 101L, title = "Song A", path = "/storage/emulated/0/Music/SongA.mp3")
        val s2 = createSong(id = 102L, title = "Song A Different ID", path = "/storage/emulated/0/Music/SongA.mp3")
        val result = deduplicateMusicItems(listOf(s1, s2))
        assertEquals(1, result.size)
    }

    @Test
    fun eliminatesSameSongInDifferentFoldersAndPrefersHigherQuality() {
        val lowQualityDownload = createSong(
            id = 101L,
            title = "Believer",
            artist = "Imagine Dragons",
            album = "Unknown Album",
            duration = 204_000L,
            size = 3_000_000L,
            path = "/storage/emulated/0/Download/Believer.mp3"
        )
        val highQualityMusic = createSong(
            id = 102L,
            title = "Believer",
            artist = "Imagine Dragons",
            album = "Evolve",
            duration = 204_500L,
            size = 8_500_000L,
            path = "/storage/emulated/0/Music/Believer.mp3"
        )

        val result = deduplicateMusicItems(listOf(lowQualityDownload, highQualityMusic))
        assertEquals(1, result.size)
        // High quality copy from Music/ folder with album name should be retained
        assertEquals(102L, result[0].id)
        assertEquals("Evolve", result[0].album)
    }

    @Test
    fun keepsDistinctSongs() {
        val s1 = createSong(id = 1L, title = "Song One", artist = "Artist A", duration = 180_000L)
        val s2 = createSong(id = 2L, title = "Song Two", artist = "Artist A", duration = 195_000L)
        val s3 = createSong(id = 3L, title = "Song One", artist = "Artist B", duration = 210_000L)

        val result = deduplicateMusicItems(listOf(s1, s2, s3))
        assertEquals(3, result.size)
    }

    @Test
    fun genericTitlesWithDifferentSizesAreNotFalselyDeduplicated() {
        val t1 = createSong(id = 1L, title = "Track 1", artist = "Unknown Artist", duration = 120_000L, size = 2_000_000L, path = "/storage/emulated/0/AlbumA/track1.mp3")
        val t2 = createSong(id = 2L, title = "Track 1", artist = "Unknown Artist", duration = 240_000L, size = 6_000_000L, path = "/storage/emulated/0/AlbumB/track1.mp3")

        val result = deduplicateMusicItems(listOf(t1, t2))
        assertEquals(2, result.size)
    }
}
