@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

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

internal fun isTorrentMediaRequest(scheme: String?, path: String?, mediaPath: String?): Boolean {
    if (scheme != null && scheme != "file") return false
    if (path.isNullOrBlank() || mediaPath.isNullOrBlank()) return false
    return runCatching { File(path).canonicalFile == File(mediaPath).canonicalFile }.getOrDefault(false)
}

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
    private var lastRequestedPiece: Int = -1
    private var isInitialReadAfterOpen: Boolean = true

    @Volatile
    private var opened: Boolean = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        isInitialReadAfterOpen = true
        lastRequestedPiece = -1

        val f = File(filePath)
        if (!f.exists()) {
            throw IOException("Torrent stream file not found on disk: $filePath")
        }

        val trueMovieSize = TorrentEngine.getMainFileSize(hash)
        if (trueMovieSize != null && trueMovieSize > 0L) {
            try {
                if (f.length() < trueMovieSize) {
                    RandomAccessFile(f, "rw").use { rwRaf ->
                        rwRaf.setLength(trueMovieSize)
                    }
                }
            } catch (_: Exception) {}
        }

        val raf = RandomAccessFile(f, "r")
        file = raf
        val totalLength = trueMovieSize?.takeIf { it > 0L } ?: f.length()
        currentPosition = dataSpec.position

        if (currentPosition > totalLength) {
            try { raf.close() } catch (_: Exception) {}
            file = null
            throw IOException("Seek position $currentPosition exceeds file length $totalLength")
        }

        raf.seek(currentPosition)

        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            minOf(dataSpec.length, (totalLength - currentPosition).coerceAtLeast(0L))
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
        val maxWaitMs = 30_000L

        if (currentPiece != null) {
            val isSeek = isInitialReadAfterOpen || (lastRequestedPiece >= 0 && kotlin.math.abs(currentPiece - lastRequestedPiece) > 2)
            isInitialReadAfterOpen = false
            lastRequestedPiece = currentPiece

            // Urgently prioritize current piece & queue next lookahead pieces (clearing stale deadlines if seeking)
            TorrentEngine.prioritizeStreamPiece(hash, currentPiece, isSeek = isSeek)

            // Progressive buffer loop: wait until libtorrent has downloaded and verified this piece
            val waitStart = android.os.SystemClock.elapsedRealtime()
            while (!TorrentEngine.isPieceDownloaded(hash, currentPiece)) {
                if (!opened) return C.RESULT_END_OF_INPUT
                if (android.os.SystemClock.elapsedRealtime() - waitStart > maxWaitMs) {
                    throw IOException("Torrent streaming timeout waiting for piece $currentPiece")
                }
                try {
                    Thread.sleep(40)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw java.io.InterruptedIOException("Torrent read interrupted")
                }
            }
        }

        if (!opened) return C.RESULT_END_OF_INPUT

        // Determine how many safe contiguous bytes are currently available on disk
        var safeBytesAvailable = TorrentEngine.getAvailableDownloadedBytesFrom(hash, currentPosition)
        if (safeBytesAvailable <= 0L && currentPiece != null) {
            val waitStart = android.os.SystemClock.elapsedRealtime()
            while (safeBytesAvailable <= 0L && android.os.SystemClock.elapsedRealtime() - waitStart < maxWaitMs) {
                if (!opened) return C.RESULT_END_OF_INPUT
                try {
                    Thread.sleep(30)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw java.io.InterruptedIOException("Torrent read interrupted")
                }
                safeBytesAvailable = TorrentEngine.getAvailableDownloadedBytesFrom(hash, currentPosition)
            }
        }

        if (!opened) return C.RESULT_END_OF_INPUT
        if (safeBytesAvailable <= 0L) {
            throw IOException("Torrent stream has no verified data available at byte $currentPosition")
        }

        val maxSafeToRead = minOf(length.toLong(), bytesRemaining, safeBytesAvailable).toInt().coerceAtLeast(1)

        val bytesRead = raf.read(buffer, offset, maxSafeToRead)
        if (bytesRead == -1) throw java.io.EOFException("Torrent data is not yet readable at byte $currentPosition")
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
    private val transferListeners = mutableListOf<TransferListener>()

    override fun addTransferListener(transferListener: TransferListener) {
        if (transferListener !in transferListeners) transferListeners += transferListener
        upstream.addTransferListener(transferListener)
        torrentDataSource?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val cleanPath = dataSpec.uri.path
        // The same factory loads external subtitles. Only the selected local
        // video may use torrent piece offsets or preallocation.
        val localRequest = dataSpec.uri.scheme == null || dataSpec.uri.scheme == "file"
        val activeHash = if (localRequest && cleanPath != null) {
            VideoPlaybackManager.activeStreamingHash ?: TorrentEngine.getActiveTorrent(cleanPath)
        } else null
        val state = activeHash?.let { TorrentEngine.downloads.value[it] }
        if (activeHash != null && state != null &&
            state.status != TorrentDownloadState.Status.FINISHED && state.status != TorrentDownloadState.Status.ERROR &&
            isTorrentMediaRequest(dataSpec.uri.scheme, cleanPath, TorrentEngine.getFilePath(context, activeHash))) {
            isTorrentStream = true
            val tds = TorrentStreamDataSource(activeHash, requireNotNull(cleanPath))
            transferListeners.forEach(tds::addTransferListener)
            torrentDataSource = tds
            try {
                return tds.open(dataSpec)
            } catch (e: Exception) {
                tds.close()
                torrentDataSource = null
                isTorrentStream = false
                throw e
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
