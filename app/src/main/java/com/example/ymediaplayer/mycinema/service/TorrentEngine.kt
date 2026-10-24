package com.example.ymediaplayer.mycinema.service

import android.content.Context
import android.os.Environment
import com.example.ymediaplayer.mycinema.data.TorrentSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.libtorrent4j.*
import org.libtorrent4j.alerts.*
import java.io.File

data class TorrentDownloadState(
    val id: String,
    val title: String,
    val magnet: String,
    val progress: Float = 0f,
    val downloadSpeed: Long = 0L,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val seeds: Int = 0,
    val isSequential: Boolean = false,
    val isStream: Boolean = false,
    val status: Status = Status.CONNECTING,
    val savePath: String? = null,
    val error: String? = null
) {
    enum class Status {
        CONNECTING,
        DOWNLOADING,
        STREAMING,
        PAUSED,
        FINISHED,
        ERROR
    }

    val progressPercent: Int
        get() = (progress * 100).toInt().coerceIn(0, 100)

    val speedFormatted: String
        get() {
            if (downloadSpeed <= 0) return "0 KB/s"
            val kb = downloadSpeed / 1024.0
            return if (kb > 1024) String.format(java.util.Locale.US, "%.1f MB/s", kb / 1024.0)
            else String.format(java.util.Locale.US, "%.0f KB/s", kb)
        }

    val downloadedSizeFormatted: String
        get() = formatTorrentBytes(downloadedBytes)

    val totalSizeFormatted: String
        get() = formatTorrentBytes(totalBytes)

    val sizeFormatted: String
        get() {
            if (totalBytes <= 0L) return downloadedSizeFormatted
            return "$downloadedSizeFormatted / $totalSizeFormatted"
        }

    val etaFormatted: String
        get() {
            if (downloadSpeed <= 30 * 1024L || totalBytes <= 0L) return "Calculating ETA..."
            val remainingBytes = (totalBytes - downloadedBytes).coerceAtLeast(0L)
            if (remainingBytes <= 0L) return "Finishing..."
            val remainingSeconds = remainingBytes / downloadSpeed
            return when {
                remainingSeconds < 60 -> "~${remainingSeconds}s left"
                remainingSeconds < 3600 -> "~${remainingSeconds / 60}m ${remainingSeconds % 60}s left"
                else -> {
                    val hrs = remainingSeconds / 3600
                    val mins = (remainingSeconds % 3600) / 60
                    "~${hrs}h ${mins}m left"
                }
            }
        }
}

fun formatTorrentBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(java.util.Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
        else -> String.format(java.util.Locale.US, "%.0f KB", kb)
    }
}

object TorrentEngine {

    fun isConnectedToWifi(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return true
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) ||
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        } catch (_: Exception) {
            true
        }
    }

    private var sessionManager: SessionManager? = null
    private var isInitialized = false

    private val _downloads = MutableStateFlow<Map<String, TorrentDownloadState>>(emptyMap())
    val downloads: StateFlow<Map<String, TorrentDownloadState>> = _downloads.asStateFlow()

    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "webm", "mov", "ts", "m4v")

    /**
     * Checks if a file on disk is an uncompleted sparse torrent pre-allocation (all zeroes / parts file present).
     */
    fun isSparseUncompletedFile(file: java.io.File): Boolean {
        if (!file.exists() || !file.isFile) return true
        if (file.length() < 1024L) return true

        // Check if any matching .parts file exists in the directory
        val dir = file.parentFile
        if (dir != null && dir.exists()) {
            val partsFiles = dir.listFiles { _, name -> name.endsWith(".parts", ignoreCase = true) }
            if (!partsFiles.isNullOrEmpty()) {
                return true
            }
        }

        // Sample file at 25% and 50% to check for unallocated sparse zero blocks
        if (file.length() > 5 * 1024 * 1024L) {
            try {
                java.io.RandomAccessFile(file, "r").use { raf ->
                    val len = file.length()
                    val buf = ByteArray(2048)

                    val pos1 = len / 4
                    raf.seek(pos1)
                    val r1 = raf.read(buf)
                    if (r1 > 0 && buf.all { it == 0.toByte() }) return true

                    val pos2 = len / 2
                    raf.seek(pos2)
                    val r2 = raf.read(buf)
                    if (r2 > 0 && buf.all { it == 0.toByte() }) return true
                }
            } catch (_: Exception) {
                return false
            }
        }
        return false
    }

    fun findMainVideoFileIndex(files: FileStorage): Int {
        val numFiles = files.numFiles()
        if (numFiles <= 0) return -1
        if (numFiles == 1) return 0

        var largestVideoIndex = -1
        var maxVideoSize = -1L

        var largestAnyIndex = 0
        var maxAnySize = -1L

        for (i in 0 until numFiles) {
            val size = files.fileSize(i)
            val path = files.filePath(i)
            val ext = path.substringAfterLast(".", "").lowercase()

            if (size > maxAnySize) {
                maxAnySize = size
                largestAnyIndex = i
            }

            if (ext in VIDEO_EXTENSIONS && size > maxVideoSize) {
                maxVideoSize = size
                largestVideoIndex = i
            }
        }

        return if (largestVideoIndex >= 0) largestVideoIndex else largestAnyIndex
    }

    const val DEFAULT_STREAM_BUFFER_BYTES = 8 * 1024 * 1024L // 8 MB buffer for instant streaming

    data class PieceRange(val firstPiece: Int, val lastPiece: Int)

    fun getMainFilePieceRange(handle: TorrentHandle): PieceRange? {
        val tf = handle.torrentFile() ?: return null
        val files = tf.files() ?: return null
        val numFiles = files.numFiles()
        if (numFiles <= 0) return null
        val mainIdx = findMainVideoFileIndex(files)
        val fileIdx = if (mainIdx >= 0) mainIdx else 0
        val firstPiece = files.pieceIndexAtFile(fileIdx)
        val lastPiece = files.lastPieceIndexAtFile(fileIdx)
        val numPieces = tf.numPieces()
        if (numPieces <= 0) return null
        return PieceRange(firstPiece.coerceIn(0, numPieces - 1), lastPiece.coerceIn(firstPiece, numPieces - 1))
    }

    fun applyTorrentFilePriorities(handle: TorrentHandle, isStream: Boolean) {
        val tf = handle.torrentFile() ?: return
        val files = tf.files() ?: return
        val numFiles = files.numFiles()
        val mainFileIndex = findMainVideoFileIndex(files)

        // Ignore all non-main files (samples, extras, images, text) except subtitles
        if (numFiles > 1 && mainFileIndex >= 0) {
            for (i in 0 until numFiles) {
                if (i != mainFileIndex) {
                    val ext = files.filePath(i).substringAfterLast(".", "").lowercase()
                    if (ext != "srt") {
                        handle.filePriority(i, Priority.IGNORE)
                    }
                }
            }
            handle.filePriority(mainFileIndex, Priority.TOP_PRIORITY)
        }

        handle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)

        val range = getMainFilePieceRange(handle)
        if (range != null) {
            val (firstPiece, lastPiece) = range
            if (isStream) {
                // For live streaming, prioritize strictly sequentially from the head of the file.
                // Do NOT set deadlines on tail pieces! ExoPlayer plays linearly with FLAG_DISABLE_SEEK_FOR_CUES.
                val headLimit = minOf(lastPiece, firstPiece + 25)
                for (i in firstPiece..headLimit) {
                    handle.piecePriority(i, Priority.TOP_PRIORITY)
                    try {
                        handle.setPieceDeadline(i, ((i - firstPiece) * 50).coerceAtLeast(0))
                    } catch (_: Exception) {}
                }
                for (i in (headLimit + 1)..lastPiece) {
                    handle.piecePriority(i, Priority.DEFAULT)
                }
            } else {
                val totalPieces = lastPiece - firstPiece + 1
                // Prioritize the head pieces of the main video file (initial 15 pieces or 5%)
                val headLimit = minOf(lastPiece, firstPiece + minOf(15, maxOf(6, (totalPieces * 0.05).toInt())))
                for (i in firstPiece..headLimit) {
                    handle.piecePriority(i, Priority.TOP_PRIORITY)
                    try {
                        handle.setPieceDeadline(i, ((i - firstPiece) * 100).coerceAtLeast(0))
                    } catch (_: Exception) {}
                }

                // High priority for the tail pieces of the movie (MP4 moov atom / MKV cues if located at end)
                val tailStart = maxOf(headLimit + 1, lastPiece - 2)
                for (i in tailStart..lastPiece) {
                    handle.piecePriority(i, Priority.SIX)
                    try {
                        handle.setPieceDeadline(i, 500)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun getDownloadDir(context: Context): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) 
            ?: File(context.filesDir, "MYCinema")
        val dir = File(baseDir, "MYCinema")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getStreamCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "stream_cache")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun purgeStreamCache(context: Context) {
        try {
            val dir = File(context.cacheDir, "stream_cache")
            if (dir.exists()) {
                dir.deleteRecursively()
                dir.mkdirs()
            }
        } catch (_: Exception) {}
    }

    fun cleanupStream(context: Context, hash: String) {
        try {
            val state = _downloads.value[hash]
            val filePath = state?.savePath ?: getFilePath(context, hash)
            cancel(hash)
            if (filePath != null) {
                val file = File(filePath)
                if (file.exists() && file.absolutePath.contains("stream_cache")) {
                    file.delete()
                    val parent = file.parentFile
                    if (parent != null && parent.name != "stream_cache" && parent.isDirectory && parent.listFiles()?.isEmpty() == true) {
                        parent.delete()
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun startStream(
        context: Context,
        source: TorrentSource,
        title: String
    ): String? {
        return startDownload(context, source, title, isSequential = true, isStream = true)
    }

    @Synchronized
    fun initialize(context: Context) {
        if (isInitialized) return
        try {
            val sm = SessionManager()
            sm.addListener(object : AlertListener {
                override fun types(): IntArray? = null

                override fun alert(alert: Alert<*>) {
                    when (alert) {
                        is MetadataReceivedAlert -> {
                            try {
                                val handle = alert.handle()
                                val hash = handle.infoHash().toHex().lowercase()
                                val state = _downloads.value[hash]
                                applyTorrentFilePriorities(handle, state?.isStream == true)
                                updateState(hash) { it.copy(status = TorrentDownloadState.Status.DOWNLOADING) }
                            } catch (_: Exception) {}
                        }
                        is TorrentFinishedAlert -> {
                            val handle = alert.handle()
                            val hash = handle.infoHash().toHex().lowercase()
                            val torrentFile = handle.torrentFile()
                            val targetFile = if (torrentFile != null && torrentFile.numFiles() > 0) {
                                val mainIdx = findMainVideoFileIndex(torrentFile.files())
                                val relPath = torrentFile.files().filePath(if (mainIdx >= 0) mainIdx else 0)
                                sanitizePath(context, relPath)
                            } else null
                            if (targetFile != null) {
                                try {
                                    com.example.ymediaplayer.data.AppPreferences(context).markCinemaDownloadCompleted(targetFile)
                                } catch (_: Exception) {}
                            }
                            updateState(hash) {
                                it.copy(
                                    progress = 1f,
                                    status = TorrentDownloadState.Status.FINISHED,
                                    savePath = targetFile
                                )
                            }
                        }
                        is TorrentErrorAlert -> {
                            val handle = alert.handle()
                            val hash = handle?.infoHash()?.toHex()?.lowercase() ?: return
                            updateState(hash) {
                                it.copy(status = TorrentDownloadState.Status.ERROR, error = alert.message())
                            }
                        }
                        else -> {}
                    }
                }
            })
            sm.start()
            sessionManager = sm
            isInitialized = true
        } catch (e: Exception) {
            android.util.Log.e("TorrentEngine", "Failed to init SessionManager: ${e.message}")
        }
    }

    fun startDownload(
        context: Context,
        source: TorrentSource,
        title: String,
        isSequential: Boolean = false,
        isStream: Boolean = false
    ): String? {
        initialize(context)
        val sm = sessionManager ?: return null
        val saveDir = if (isStream) getStreamCacheDir(context) else getDownloadDir(context)
        val appPrefs = com.example.ymediaplayer.data.AppPreferences(context)
        if (!isStream && !isSequential && appPrefs.isWifiOnlyDownloadEnabled() && !isConnectedToWifi(context)) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, "Wi-Fi Only is enabled: Download will proceed on Wi-Fi", android.widget.Toast.LENGTH_LONG).show()
            }
        }

        return try {
            val hashMatch = Regex("xt=urn:btih:([a-zA-Z0-9]+)", RegexOption.IGNORE_CASE).find(source.magnet)
            val infoHash = hashMatch?.groupValues?.getOrNull(1)?.lowercase() ?: return null

            val initialState = TorrentDownloadState(
                id = infoHash,
                title = title,
                magnet = source.magnet,
                isSequential = isSequential,
                isStream = isStream,
                status = if (isSequential) TorrentDownloadState.Status.STREAMING else TorrentDownloadState.Status.CONNECTING
            )
            _downloads.value = _downloads.value + (infoHash to initialState)

            val flags = if (isSequential) TorrentFlags.SEQUENTIAL_DOWNLOAD else TorrentFlags.AUTO_MANAGED
            sm.download(source.magnet, saveDir, flags)

            val sha1 = Sha1Hash.parseHex(infoHash)
            val handle = sm.find(sha1)
            if (handle != null && handle.isValid) {
                if (isSequential) {
                    handle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
                }
                if (handle.torrentFile() != null) {
                    applyTorrentFilePriorities(handle, isStream)
                }
            }

            infoHash
        } catch (e: Exception) {
            android.util.Log.e("TorrentEngine", "Error starting download: ${e.message}")
            null
        }
    }

    fun updateProgressTicks() {
        val sm = sessionManager ?: return
        val currentMap = _downloads.value.toMutableMap()
        var changed = false

        for ((hash, state) in currentMap) {
            if (state.status == TorrentDownloadState.Status.FINISHED || state.status == TorrentDownloadState.Status.ERROR) {
                continue
            }
            try {
                val sha1 = Sha1Hash.parseHex(hash)
                val handle = sm.find(sha1) ?: continue
                if (!handle.isValid) continue

                val status = handle.status()
                val progress = status.progress()
                val speed = status.downloadPayloadRate().toLong()
                val downloaded = status.totalDone()
                val total = status.totalWanted()
                val seeds = status.numSeeds()

                val newStatus = if (progress >= 1f) {
                    TorrentDownloadState.Status.FINISHED
                } else if (state.isSequential) {
                    TorrentDownloadState.Status.STREAMING
                } else {
                    TorrentDownloadState.Status.DOWNLOADING
                }

                currentMap[hash] = state.copy(
                    progress = progress,
                    downloadSpeed = speed,
                    downloadedBytes = downloaded,
                    totalBytes = total,
                    seeds = seeds,
                    status = newStatus
                )
                changed = true
            } catch (_: Exception) {}
        }

        if (changed) {
            _downloads.value = currentMap
        }
    }

    fun pause(hash: String) {
        try {
            val sha1 = Sha1Hash.parseHex(hash)
            val handle = sessionManager?.find(sha1)
            handle?.pause()
            updateState(hash) { it.copy(status = TorrentDownloadState.Status.PAUSED) }
        } catch (_: Exception) {}
    }

    fun resume(hash: String) {
        try {
            val sha1 = Sha1Hash.parseHex(hash)
            val handle = sessionManager?.find(sha1)
            handle?.resume()
            updateState(hash) { it.copy(status = TorrentDownloadState.Status.DOWNLOADING) }
        } catch (_: Exception) {}
    }

    fun setSequential(hash: String, sequential: Boolean) {
        try {
            val sha1 = Sha1Hash.parseHex(hash)
            val handle = sessionManager?.find(sha1)
            if (handle != null && handle.isValid) {
                if (sequential) {
                    handle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
                } else {
                    handle.unsetFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
                }
                updateState(hash) { it.copy(isSequential = sequential) }
            }
        } catch (_: Exception) {}
    }

    fun cancel(hash: String) {
        try {
            val sha1 = Sha1Hash.parseHex(hash)
            val handle = sessionManager?.find(sha1)
            if (handle != null) {
                sessionManager?.remove(handle)
            }
            _downloads.value = _downloads.value - hash
        } catch (_: Exception) {}
    }

    private fun sanitizePath(context: Context, relPath: String): String? {
        return try {
            val streamDir = getStreamCacheDir(context).canonicalFile
            val streamTarget = File(streamDir, relPath).canonicalFile
            if (streamTarget.path.startsWith(streamDir.path) && streamTarget.exists()) {
                return streamTarget.absolutePath
            }

            val baseDir = getDownloadDir(context).canonicalFile
            val target = File(baseDir, relPath).canonicalFile
            if (target.path.startsWith(baseDir.path) && target.exists()) {
                target.absolutePath
            } else if (streamTarget.path.startsWith(streamDir.path)) {
                streamTarget.absolutePath
            } else if (target.path.startsWith(baseDir.path)) {
                target.absolutePath
            } else {
                android.util.Log.e("TorrentEngine", "Path traversal blocked: $relPath")
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun getFilePath(context: Context, hash: String): String? {
        val sha1 = try { Sha1Hash.parseHex(hash) } catch (_: Exception) { return null }
        val handle = sessionManager?.find(sha1) ?: return null
        val torrentFile = handle.torrentFile() ?: return null
        val files = torrentFile.files() ?: return null
        if (files.numFiles() > 0) {
            val mainIdx = findMainVideoFileIndex(files)
            val relPath = files.filePath(if (mainIdx >= 0) mainIdx else 0)
            val isStream = _downloads.value[hash]?.isStream ?: false
            val primaryDir = if (isStream) getStreamCacheDir(context) else getDownloadDir(context)
            val secondaryDir = if (isStream) getDownloadDir(context) else getStreamCacheDir(context)

            val primaryFile = File(primaryDir, relPath)
            if (primaryFile.exists()) {
                return primaryFile.absolutePath
            }
            val secondaryFile = File(secondaryDir, relPath)
            if (secondaryFile.exists()) {
                return secondaryFile.absolutePath
            }

            // Check flat filename directly in primary or secondary directories
            val simpleName = File(relPath).name
            val flatPrimary = File(primaryDir, simpleName)
            if (flatPrimary.exists()) {
                return flatPrimary.absolutePath
            }
            val flatSecondary = File(secondaryDir, simpleName)
            if (flatSecondary.exists()) {
                return flatSecondary.absolutePath
            }

            // Recursive walk to find the file if libtorrent created a nested subfolder structure
            try {
                val foundPrimary = primaryDir.walkTopDown().firstOrNull { it.isFile && it.name == simpleName }
                if (foundPrimary != null && foundPrimary.exists()) {
                    return foundPrimary.absolutePath
                }
                val foundSecondary = secondaryDir.walkTopDown().firstOrNull { it.isFile && it.name == simpleName }
                if (foundSecondary != null && foundSecondary.exists()) {
                    return foundSecondary.absolutePath
                }
            } catch (_: Exception) {}

            return primaryFile.absolutePath
        }
        return null
    }

    fun getDownloadedBytes(hash: String): Long {
        return try {
            val sha1 = Sha1Hash.parseHex(hash)
            val handle = sessionManager?.find(sha1) ?: return 0L
            if (!handle.isValid) return 0L
            handle.status().totalDone()
        } catch (_: Exception) { 0L }
    }

    fun getTotalWantedBytes(hash: String): Long {
        return try {
            val sha1 = Sha1Hash.parseHex(hash)
            val handle = sessionManager?.find(sha1) ?: return 0L
            if (!handle.isValid) return 0L
            handle.status().totalWanted()
        } catch (_: Exception) { 0L }
    }

    fun getRequiredBufferBytes(hash: String): Long {
        val wanted = getTotalWantedBytes(hash)
        if (wanted <= 0) return DEFAULT_STREAM_BUFFER_BYTES
        return if (wanted < 50 * 1024 * 1024L) {
            minOf(DEFAULT_STREAM_BUFFER_BYTES, maxOf(2 * 1024 * 1024L, wanted / 5))
        } else {
            DEFAULT_STREAM_BUFFER_BYTES
        }
    }

    fun isStreamTorrent(hash: String): Boolean {
        return _downloads.value[hash]?.isStream == true
    }

    fun getPieceForFileOffset(hash: String, fileOffset: Long): Int? {
        val sha1 = try { Sha1Hash.parseHex(hash) } catch (_: Exception) { return null }
        val handle = sessionManager?.find(sha1) ?: return null
        if (!handle.isValid) return null
        val tf = handle.torrentFile() ?: return null
        val files = tf.files() ?: return null
        val numFiles = files.numFiles()
        if (numFiles <= 0) return null
        val mainIdx = findMainVideoFileIndex(files)
        val fileIdx = if (mainIdx >= 0) mainIdx else 0
        val baseOffset = try { files.fileOffset(fileIdx) } catch (_: Exception) { 0L }
        val pieceLength = tf.pieceLength()
        if (pieceLength <= 0) return null
        val torrentOffset = baseOffset + fileOffset
        val pieceIndex = (torrentOffset / pieceLength).toInt()
        val numPieces = tf.numPieces()
        return pieceIndex.coerceIn(0, numPieces - 1)
    }

    fun isPieceDownloaded(hash: String, pieceIndex: Int): Boolean {
        val sha1 = try { Sha1Hash.parseHex(hash) } catch (_: Exception) { return false }
        val handle = sessionManager?.find(sha1) ?: return false
        if (!handle.isValid) return false
        return try { handle.havePiece(pieceIndex) } catch (_: Exception) { false }
    }

    fun prioritizeStreamPiece(hash: String, currentPiece: Int) {
        val sha1 = try { Sha1Hash.parseHex(hash) } catch (_: Exception) { return }
        val handle = sessionManager?.find(sha1) ?: return
        if (!handle.isValid) return
        val tf = handle.torrentFile() ?: return
        val numPieces = tf.numPieces()
        if (numPieces <= 0) return

        try {
            // Immediate priority for current piece
            if (!handle.havePiece(currentPiece)) {
                handle.piecePriority(currentPiece, Priority.TOP_PRIORITY)
                handle.setPieceDeadline(currentPiece, 0)
            }

            // High priority with ascending deadlines for the next 5 pieces
            val immediateLimit = minOf(numPieces - 1, currentPiece + 5)
            for (p in (currentPiece + 1)..immediateLimit) {
                if (!handle.havePiece(p)) {
                    handle.piecePriority(p, Priority.TOP_PRIORITY)
                    handle.setPieceDeadline(p, (p - currentPiece) * 50)
                }
            }

            // Lookahead priority for the next 20 pieces
            val lookaheadLimit = minOf(numPieces - 1, currentPiece + 25)
            for (p in (immediateLimit + 1)..lookaheadLimit) {
                if (!handle.havePiece(p)) {
                    handle.piecePriority(p, Priority.SIX)
                    handle.setPieceDeadline(p, (p - currentPiece) * 100)
                }
            }
        } catch (_: Exception) {}
    }

    fun getAvailableDownloadedBytesFrom(hash: String, fromOffset: Long): Long {
        val sha1 = try { Sha1Hash.parseHex(hash) } catch (_: Exception) { return 0L }
        val handle = sessionManager?.find(sha1) ?: return 0L
        if (!handle.isValid) return 0L
        val tf = handle.torrentFile() ?: return 0L
        val files = tf.files() ?: return 0L
        val mainIdx = findMainVideoFileIndex(files)
        val fileIdx = if (mainIdx >= 0) mainIdx else 0
        val baseOffset = try { files.fileOffset(fileIdx) } catch (_: Exception) { 0L }
        val fileSize = files.fileSize(fileIdx)
        val pieceLength = tf.pieceLength()
        if (pieceLength <= 0) return 0L
        val torrentOffset = baseOffset + fromOffset
        val startPiece = (torrentOffset / pieceLength).toInt()
        val numPieces = tf.numPieces()

        var p = startPiece
        while (p < numPieces) {
            val hasPiece = try { handle.havePiece(p) } catch (_: Exception) { false }
            if (!hasPiece) break
            p++
        }

        if (p == startPiece) return 0L // current piece not downloaded

        val endTorrentOffset = p.toLong() * pieceLength
        val endFileOffset = (endTorrentOffset - baseOffset).coerceAtMost(fileSize)
        return (endFileOffset - fromOffset).coerceAtLeast(0L)
    }

    fun isStreamBufferReady(context: Context, hash: String): Boolean {
        val path = getFilePath(context, hash) ?: return false
        val file = File(path)
        if (!file.exists()) {
            android.util.Log.d("TorrentEngine", "isStreamBufferReady: file does not exist yet: $path")
            return false
        }

        val sm = sessionManager ?: return false
        val sha1 = try { Sha1Hash.parseHex(hash) } catch (_: Exception) { return false }
        val handle = sm.find(sha1) ?: return false
        if (!handle.isValid) return false

        val tf = handle.torrentFile() ?: return false
        val pieceLength = tf.pieceLength()
        if (pieceLength <= 0) return false

        val range = getMainFilePieceRange(handle) ?: return false
        val (firstPiece, lastPiece) = range

        val required = getRequiredBufferBytes(hash)
        // Pieces needed to satisfy buffer requirement (at least 2 pieces, at most 8)
        val piecesNeeded = maxOf(2, minOf(8, Math.ceil(required.toDouble() / pieceLength).toInt()))
        val requiredHeadLimit = minOf(lastPiece + 1, firstPiece + piecesNeeded)

        // ALL initial contiguous head pieces must be verified and downloaded!
        for (p in firstPiece until requiredHeadLimit) {
            val has = try { handle.havePiece(p) } catch (_: Exception) { false }
            if (!has) {
                val done = getDownloadedBytes(hash)
                android.util.Log.d("TorrentEngine", "isStreamBufferReady: waiting for head piece $p, done=$done/$required")
                return false
            }
        }

        val done = getDownloadedBytes(hash)
        val minHeadBytes = (requiredHeadLimit - firstPiece).toLong() * pieceLength
        if (done < minOf(required, minHeadBytes)) {
            android.util.Log.d("TorrentEngine", "isStreamBufferReady: buffer not ready yet ($done / $required)")
            return false
        }

        android.util.Log.i("TorrentEngine", "isStreamBufferReady: READY! path=$path, done=$done, pieces=$firstPiece..<$requiredHeadLimit verified")
        return true
    }

    fun getActiveTorrent(filePath: String): String? {
        if (filePath.isBlank()) return null
        val cleanPath = if (filePath.startsWith("file://")) {
            try { android.net.Uri.parse(filePath).path ?: filePath.removePrefix("file://") } catch (_: Exception) { filePath.removePrefix("file://") }
        } else filePath
        val targetFile = File(cleanPath)
        val targetName = targetFile.name.lowercase()
        val targetNorm = targetFile.absolutePath.replace("\\", "/").lowercase()

        for ((hash, state) in _downloads.value) {
            if (state.status == TorrentDownloadState.Status.FINISHED) continue
            val savePathNorm = state.savePath?.replace("\\", "/")?.lowercase()
            if (savePathNorm != null && (savePathNorm == targetNorm || targetNorm.endsWith(savePathNorm) || savePathNorm.endsWith(targetNorm))) {
                return hash
            }
            val sm = sessionManager ?: continue
            val sha1 = try { Sha1Hash.parseHex(hash) } catch (_: Exception) { continue }
            val handle = sm.find(sha1) ?: continue
            val tf = handle.torrentFile() ?: continue
            val files = tf.files()
            for (i in 0 until files.numFiles()) {
                val fName = File(files.filePath(i)).name.lowercase()
                if (fName == targetName) {
                    return hash
                }
            }
        }
        return null
    }

    fun isPieceAvailable(hash: String, progressFraction: Double): Boolean {
        val sm = sessionManager ?: return false
        val sha1 = try { Sha1Hash.parseHex(hash) } catch (_: Exception) { return false }
        val handle = sm.find(sha1) ?: return false
        if (!handle.isValid) return false
        val range = getMainFilePieceRange(handle) ?: return false
        val (firstPiece, lastPiece) = range
        val numPieces = lastPiece - firstPiece + 1
        if (numPieces <= 0) return false
        val targetPiece = (firstPiece + progressFraction * (numPieces - 1)).toInt().coerceIn(firstPiece, lastPiece)
        return try { handle.havePiece(targetPiece) } catch (_: Exception) { false }
    }

    /**
     * Maintains a ~3 minute sliding buffer window ahead of current playback position.
     * Pieces within the window are set to TOP_PRIORITY with sequential deadlines.
     */
    fun updateStreamSlidingWindow(hash: String, progressFraction: Double, totalDurationSec: Long = 6600L) {
        val sm = sessionManager ?: return
        val sha1 = try { Sha1Hash.parseHex(hash) } catch (_: Exception) { return }
        val handle = sm.find(sha1) ?: return
        if (!handle.isValid) return
        val range = getMainFilePieceRange(handle) ?: return
        val (firstPiece, lastPiece) = range
        val numPieces = lastPiece - firstPiece + 1
        if (numPieces <= 0) return

        val targetPiece = (firstPiece + progressFraction * (numPieces - 1)).toInt().coerceIn(firstPiece, lastPiece)
        val tailStart = maxOf(firstPiece, lastPiece - 2)

        // 3 minutes (180s) buffer window ahead of current playback position
        val duration = totalDurationSec.coerceAtLeast(3600L).toDouble()
        val windowRatio = 180.0 / duration
        val windowPieces = maxOf(10, minOf(50, (numPieces * windowRatio).toInt()))
        val windowEnd = minOf(tailStart, targetPiece + windowPieces)

        // Activate next 3 minutes of pieces
        for (p in targetPiece until windowEnd) {
            val isDownloaded = try { handle.havePiece(p) } catch (_: Exception) { false }
            if (!isDownloaded) {
                handle.piecePriority(p, Priority.TOP_PRIORITY)
                try {
                    handle.setPieceDeadline(p, ((p - targetPiece) * 100).coerceAtLeast(0))
                } catch (_: Exception) {}
            }
        }
    }

    fun prioritizeStreamSeek(hash: String, progressFraction: Double, totalDurationSec: Long = 6600L) {
        val sm = sessionManager ?: return
        val sha1 = try { Sha1Hash.parseHex(hash) } catch (_: Exception) { return }
        val handle = sm.find(sha1) ?: return
        if (!handle.isValid) return
        try {
            handle.clearPieceDeadlines()
        } catch (_: Exception) {}
        updateStreamSlidingWindow(hash, progressFraction, totalDurationSec)
    }

    fun isSeekBufferReady(hash: String, progressFraction: Double): Boolean {
        return isPieceAvailable(hash, progressFraction)
    }

    fun getDownloadedRanges(hash: String): List<ClosedFloatingPointRange<Float>> {
        val state = _downloads.value[hash]
        if (state != null && (state.status == TorrentDownloadState.Status.FINISHED || state.progress >= 0.999f)) {
            return listOf(0f..1f)
        }
        val sm = sessionManager ?: return emptyList()
        val sha1 = try { Sha1Hash.parseHex(hash) } catch (_: Exception) { return emptyList() }
        val handle = sm.find(sha1) ?: return emptyList()
        if (!handle.isValid) return emptyList()
        val range = getMainFilePieceRange(handle) ?: return emptyList()
        val (firstPiece, lastPiece) = range
        val totalMoviePieces = lastPiece - firstPiece + 1
        if (totalMoviePieces <= 0) return emptyList()

        val ranges = mutableListOf<ClosedFloatingPointRange<Float>>()
        var inRange = false
        var startPiece = firstPiece

        for (i in firstPiece..lastPiece) {
            val bit = try { handle.havePiece(i) } catch (_: Exception) { false }
            if (bit && !inRange) {
                inRange = true
                startPiece = i
            } else if (!bit && inRange) {
                inRange = false
                val startFrac = (startPiece - firstPiece).toFloat() / totalMoviePieces.toFloat()
                val endFrac = (i - firstPiece).toFloat() / totalMoviePieces.toFloat()
                ranges.add(startFrac..endFrac)
            }
        }
        if (inRange) {
            val startFrac = (startPiece - firstPiece).toFloat() / totalMoviePieces.toFloat()
            val endFrac = 1.0f
            ranges.add(startFrac..endFrac)
        }
        return ranges
    }

    fun getDownloadSpeedFormatted(hash: String): String {
        return _downloads.value[hash]?.speedFormatted ?: "0 KB/s"
    }

    private fun updateState(hash: String, transform: (TorrentDownloadState) -> TorrentDownloadState) {
        val current = _downloads.value[hash] ?: return
        _downloads.value = _downloads.value + (hash to transform(current))
    }
}
