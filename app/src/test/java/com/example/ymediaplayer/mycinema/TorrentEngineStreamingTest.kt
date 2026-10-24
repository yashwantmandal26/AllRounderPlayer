package com.example.ymediaplayer.mycinema

import com.example.ymediaplayer.mycinema.service.TorrentEngine
import com.example.ymediaplayer.mycinema.service.formatTorrentBytes
import org.junit.Assert.*
import org.junit.Test

class TorrentEngineStreamingTest {

    @Test
    fun defaultStreamBufferIsEightMegabytes() {
        assertEquals(8 * 1024 * 1024L, TorrentEngine.DEFAULT_STREAM_BUFFER_BYTES)
    }

    @Test
    fun requiredBufferBytesReturnsEightMegabytesForNormalMovies() {
        val bufferConnecting = TorrentEngine.getRequiredBufferBytes("nonexistent_hash")
        assertEquals(TorrentEngine.DEFAULT_STREAM_BUFFER_BYTES, bufferConnecting)
    }

    @Test
    fun pieceRangeCalculatesRelativeTargetPieceCorrectly() {
        val range = TorrentEngine.PieceRange(firstPiece = 50, lastPiece = 1050)
        val numPieces = range.lastPiece - range.firstPiece + 1
        assertEquals(1001, numPieces)

        // At 0% playback (start of movie)
        val startTarget = (range.firstPiece + 0.0 * (numPieces - 1)).toInt().coerceIn(range.firstPiece, range.lastPiece)
        assertEquals(50, startTarget)

        // At 50% playback (middle of movie)
        val midTarget = (range.firstPiece + 0.5 * (numPieces - 1)).toInt().coerceIn(range.firstPiece, range.lastPiece)
        assertEquals(550, midTarget)

        // At 100% playback (end of movie)
        val endTarget = (range.firstPiece + 1.0 * (numPieces - 1)).toInt().coerceIn(range.firstPiece, range.lastPiece)
        assertEquals(1050, endTarget)
    }

    @Test
    fun pieceRangeHandlesSinglePieceVideoFile() {
        val range = TorrentEngine.PieceRange(firstPiece = 12, lastPiece = 12)
        val numPieces = range.lastPiece - range.firstPiece + 1
        assertEquals(1, numPieces)

        val target = (range.firstPiece + 0.5 * (numPieces - 1)).toInt().coerceIn(range.firstPiece, range.lastPiece)
        assertEquals(12, target)
    }

    @Test
    fun formatTorrentBytesFormatsAccurately() {
        assertEquals("0 B", formatTorrentBytes(0L))
        assertEquals("0 KB", formatTorrentBytes(500L))
        assertEquals("1 KB", formatTorrentBytes(1024L))
        assertEquals("8.0 MB", formatTorrentBytes(8 * 1024 * 1024L))
        assertEquals("1.50 GB", formatTorrentBytes((1.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun testTorrentHandleHavePieceMethod() {
        val method = org.libtorrent4j.TorrentHandle::class.java.getMethod("havePiece", Int::class.javaPrimitiveType)
        assertNotNull(method)
        assertEquals(Boolean::class.javaPrimitiveType, method.returnType)
    }
}
