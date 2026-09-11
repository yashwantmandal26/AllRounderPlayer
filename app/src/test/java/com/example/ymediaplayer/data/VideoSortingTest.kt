package com.example.ymediaplayer.data

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class VideoSortingTest {

    @Test
    fun sortVideos_byDateDescWithIdDescTiebreaker() {
        val comparator = compareByDescending<VideoItem> { it.dateAdded }.thenByDescending { it.id }

        val videoOlder = createVideo(id = 10L, title = "Older Video", dateAdded = 1000L)
        val videoNewer = createVideo(id = 5L, title = "Newer Video", dateAdded = 2000L)
        // Same dateAdded as videoSameDate1, but different ID
        val videoSameDate1 = createVideo(id = 20L, title = "Same Date ID 20", dateAdded = 3000L)
        val videoSameDate2 = createVideo(id = 35L, title = "Same Date ID 35", dateAdded = 3000L)

        val unsorted = listOf(videoOlder, videoSameDate1, videoNewer, videoSameDate2)
        val sorted = unsorted.sortedWith(comparator)

        // videoSameDate2 (id 35) should come before videoSameDate1 (id 20) due to id desc tiebreaker
        val expected = listOf(videoSameDate2, videoSameDate1, videoNewer, videoOlder)
        assertEquals(expected.map { it.id }, sorted.map { it.id })
    }

    @Test
    fun sortVideos_byTitleAlphabetical() {
        val v1 = createVideo(id = 1L, title = "Apple")
        val v2 = createVideo(id = 2L, title = "banana")
        val v3 = createVideo(id = 3L, title = "Carrot")

        val list = listOf(v2, v3, v1)
        val sorted = list.sortedBy { it.title.lowercase(Locale.getDefault()) }

        assertEquals(listOf(1L, 2L, 3L), sorted.map { it.id })
    }

    @Test
    fun sortVideos_bySizeDescending() {
        val small = createVideo(id = 1L, size = 1024L)
        val medium = createVideo(id = 2L, size = 1048576L)
        val large = createVideo(id = 3L, size = 1073741824L)

        val list = listOf(small, large, medium)
        val sorted = list.sortedByDescending { it.size }

        assertEquals(listOf(3L, 2L, 1L), sorted.map { it.id })
    }

    private val dummyUri: Uri = org.mockito.Mockito.mock(Uri::class.java)

    private fun createVideo(
        id: Long,
        title: String = "Test Video",
        duration: Long = 10000L,
        size: Long = 50000L,
        dateAdded: Long = 1000L
    ): VideoItem {
        return VideoItem(
            id = id,
            uri = dummyUri,
            title = title,
            duration = duration,
            size = size,
            bucketId = "bucket_1",
            bucketName = "Camera",
            relativePath = "DCIM/Camera/",
            dateAdded = dateAdded
        )
    }
}
