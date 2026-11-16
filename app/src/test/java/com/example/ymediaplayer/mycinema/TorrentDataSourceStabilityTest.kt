@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.example.ymediaplayer.mycinema

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import com.example.ymediaplayer.mycinema.service.TorrentStreamDataSource
import com.example.ymediaplayer.mycinema.service.isTorrentMediaRequest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.*

class TorrentDataSourceStabilityTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun remoteSubtitlesAndOtherLocalFilesNeverUseTheMoviesTorrentPieces() {
        val media = java.io.File(temporary.root, "movie.mkv").absolutePath
        assertTrue(isTorrentMediaRequest("file", media, media))
        assertTrue(isTorrentMediaRequest(null, media, media))
        assertFalse(isTorrentMediaRequest("https", media, media))
        assertFalse(isTorrentMediaRequest("content", media, media))
        assertFalse(isTorrentMediaRequest("file", "$media.srt", media))
        assertFalse(isTorrentMediaRequest("file", media, null))
    }

    @Test fun requestedRangeCannotExtendPastTheFileEnd() {
        val file = temporary.newFile("range.mkv").apply { writeBytes(ByteArray(100)) }
        val uri = mock(Uri::class.java)
        `when`(uri.toString()).thenReturn(file.toURI().toString())
        val source = TorrentStreamDataSource("invalid_hash", file.absolutePath)
        try {
            val request = DataSpec.Builder().setUri(uri).setPosition(80).setLength(50).build()
            assertEquals(20L, source.open(request))
        } finally { source.close() }
    }

    @Test fun openingAtTheFileEndReturnsEofWithoutWaitingForPieces() {
        val file = temporary.newFile("end.mkv").apply { writeBytes(ByteArray(100)) }
        val uri = mock(Uri::class.java)
        val source = TorrentStreamDataSource("invalid_hash", file.absolutePath)
        try {
            assertEquals(0L, source.open(DataSpec.Builder().setUri(uri).setPosition(100).setLength(50).build()))
            assertEquals(C.RESULT_END_OF_INPUT, source.read(ByteArray(10), 0, 10))
        } finally { source.close() }
    }
}
