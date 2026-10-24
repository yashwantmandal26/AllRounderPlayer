package com.example.ymediaplayer.mycinema.service

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.example.ymediaplayer.player.VideoPlaybackManager
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Custom DataSource for progressive torrent playback.
 * Checks piece availability from TorrentEngine, prioritizes needed pieces in real-time,
 * and pauses/buffers gracefully instead of reading unwritten sparse zeros from disk.
 */
class TorrentStreamDataSource(
    private val hash: String,
    private val filePath: String
) : BaseDataSource(/* isNetwork = */ false) {

    private var file: RandomAccessFile? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0L
    private var currentPosition: Long = 0L

    @Volatile
    private var opened: Boolean = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)

        val f = File(filePath)
        if (!f.exists()) {
            throw IOException("Torrent stream file not found on disk: $filePath")
        }

        val raf = RandomAccessFile(f, "r")
        file = raf
        val totalLength = f.length()
        currentPosition = dataSpec.position

        if (currentPosition > totalLength) {
            try { raf.close() } catch (_: Exception) {}
            file = null
            throw IOException("Seek position $currentPosition exceeds file length $totalLength")
        }

        raf.seek(currentPosition)

        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            (totalLength - currentPosition).coerceAtLeast(0L)
        }

        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val raf = file ?: throw IOException("TorrentStreamDataSource is not opened")

        // Map current file byte offset to libtorrent piece
        val currentPiece = TorrentEngine.getPieceForFileOffset(hash, currentPosition)
        if (currentPiece != null) {
            // Urgently prioritize current piece & queue next lookahead pieces
            TorrentEngine.prioritizeStreamPiece(hash, currentPiece)

            // Progressive buffer loop: wait until libtorrent has downloaded and verified this piece
            val waitStart = System.currentTimeMillis()
            while (!TorrentEngine.isPieceDownloaded(hash, currentPiece)) {
                if (!opened) return C.RESULT_END_OF_INPUT
                if (System.currentTimeMillis() - waitStart > 60_000L) {
                    throw IOException("Torrent streaming timeout waiting for piece $currentPiece")
                }
                try {
                    Thread.sleep(50)
                } catch (_: InterruptedException) {
                    return C.RESULT_END_OF_INPUT
                }
            }
        }

        if (!opened) return C.RESULT_END_OF_INPUT

        // Determine how many safe contiguous bytes are currently available on disk
        val safeBytesAvailable = TorrentEngine.getAvailableDownloadedBytesFrom(hash, currentPosition)
        val maxSafeToRead = if (safeBytesAvailable > 0) {
            minOf(length.toLong(), bytesRemaining, safeBytesAvailable).toInt()
        } else {
            minOf(length.toLong(), bytesRemaining).toInt()
        }

        val bytesRead = raf.read(buffer, offset, maxSafeToRead.coerceAtLeast(1))
        if (bytesRead > 0) {
            currentPosition += bytesRead
            if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                bytesRemaining -= bytesRead
            }
            bytesTransferred(bytesRead)
        }
        return bytesRead
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        val wasOpened = opened
        opened = false
        try {
            file?.close()
        } catch (_: Exception) {}
        file = null
        if (wasOpened) {
            transferEnded()
        }
    }
}

/**
 * DataSource wrapper that transparently routes torrent streams through TorrentStreamDataSource
 * while delegating all other video playback to the default DataSource.
 */
class TorrentAwareDataSource(
    private val context: Context,
    private val upstream: DataSource
) : DataSource {

    private var torrentDataSource: TorrentStreamDataSource? = null
    private var isTorrentStream: Boolean = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val rawPath = dataSpec.uri.path
        val cleanPath = if (rawPath != null && rawPath.startsWith("file://")) {
            rawPath.removePrefix("file://")
        } else rawPath

        val activeHash = VideoPlaybackManager.activeStreamingHash
            ?: (if (cleanPath != null) TorrentEngine.getActiveTorrent(cleanPath) else null)

        val isStream = (activeHash != null && TorrentEngine.isStreamTorrent(activeHash)) ||
                (cleanPath != null && cleanPath.contains("stream_cache"))

        if (isStream && cleanPath != null) {
            val hashToUse = activeHash ?: (if (cleanPath.contains("stream_cache")) TorrentEngine.getActiveTorrent(cleanPath) else null)
            if (hashToUse != null) {
                isTorrentStream = true
                val tds = TorrentStreamDataSource(hashToUse, cleanPath)
                torrentDataSource = tds
                return tds.open(dataSpec)
            }
        }

        isTorrentStream = false
        return upstream.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return if (isTorrentStream) {
            torrentDataSource?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT
        } else {
            upstream.read(buffer, offset, length)
        }
    }

    override fun getUri(): Uri? {
        return if (isTorrentStream) torrentDataSource?.getUri() else upstream.getUri()
    }

    override fun close() {
        if (isTorrentStream) {
            try {
                torrentDataSource?.close()
            } finally {
                torrentDataSource = null
                isTorrentStream = false
            }
        } else {
            upstream.close()
        }
    }
}

/**
 * Factory for creating TorrentAwareDataSource instances for ExoPlayer.
 */
class TorrentAwareDataSourceFactory(
    private val context: Context,
    private val upstreamFactory: DataSource.Factory
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return TorrentAwareDataSource(context, upstreamFactory.createDataSource())
    }
}
