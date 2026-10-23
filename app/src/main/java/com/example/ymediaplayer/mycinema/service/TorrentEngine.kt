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

    fun getDownloadDir(context: Context): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) 
            ?: File(context.filesDir, "MYCinema")
        val dir = File(baseDir, "MYCinema")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
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
                                val torrentFile = handle.torrentFile()
                                if (torrentFile != null) {
                                    val files = torrentFile.files()
                                    val numFiles = files.numFiles()
                                    val mainFileIndex = findMainVideoFileIndex(files)

                                    // Set non-video/small files to IGNORE to save data & bandwidth
                                    if (numFiles > 1 && mainFileIndex >= 0) {
                                        for (i in 0 until numFiles) {
                                            if (i != mainFileIndex) {
                                                val ext = files.filePath(i).substringAfterLast(".", "").lowercase()
                                                if (ext !in VIDEO_EXTENSIONS && ext != "srt") {
                                                    handle.filePriority(i, Priority.IGNORE)
                                                }
                                            }
                                        }
                                        handle.filePriority(mainFileIndex, Priority.TOP_PRIORITY)
                                    }

                                    val numPieces = torrentFile.numPieces()
                                    val state = _downloads.value[hash]
                                    if (state?.isSequential == true && numPieces > 0) {
                                        handle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
                                        val headLimit = minOf(8, numPieces)
                                        for (i in 0 until headLimit) {
                                            handle.piecePriority(i, Priority.TOP_PRIORITY)
                                        }
                                        val tailStart = maxOf(0, numPieces - 8)
                                        for (i in tailStart until numPieces) {
                                            handle.piecePriority(i, Priority.TOP_PRIORITY)
                                        }
                                    }
                                }
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
        isSequential: Boolean = false
    ): String? {
        initialize(context)
        val sm = sessionManager ?: return null
        val saveDir = getDownloadDir(context)
        val appPrefs = com.example.ymediaplayer.data.AppPreferences(context)
        if (!isSequential && appPrefs.isWifiOnlyDownloadEnabled() && !isConnectedToWifi(context)) {
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
                status = if (isSequential) TorrentDownloadState.Status.STREAMING else TorrentDownloadState.Status.CONNECTING
            )
            _downloads.value = _downloads.value + (infoHash to initialState)

            val flags = if (isSequential) TorrentFlags.SEQUENTIAL_DOWNLOAD else TorrentFlags.AUTO_MANAGED
            sm.download(source.magnet, saveDir, flags)

            val sha1 = Sha1Hash.parseHex(infoHash)
            val handle = sm.find(sha1)
            if (handle != null && handle.isValid && isSequential) {
                handle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
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
            val baseDir = getDownloadDir(context).canonicalFile
            val target = File(baseDir, relPath).canonicalFile
            if (target.path.startsWith(baseDir.path)) {
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
        val sha1 = Sha1Hash.parseHex(hash)
        val handle = sessionManager?.find(sha1) ?: return null
        val torrentFile = handle.torrentFile() ?: return null
        val files = torrentFile.files()
        if (files.numFiles() > 0) {
            val mainIdx = findMainVideoFileIndex(files)
            val relPath = files.filePath(if (mainIdx >= 0) mainIdx else 0)
            val sanitized = sanitizePath(context, relPath)
            if (sanitized != null && File(sanitized).exists()) {
                return sanitized
            }
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
        if (wanted <= 0) return 15_000_000L
        return minOf(15_000_000L, maxOf(5_000_000L, (wanted * 0.02).toLong()))
    }

    fun isStreamBufferReady(context: Context, hash: String): Boolean {
        val path = getFilePath(context, hash) ?: return false
        val file = File(path)
        if (!file.exists()) return false

        val done = getDownloadedBytes(hash)
        val required = getRequiredBufferBytes(hash)
        return done >= required
    }

    private fun updateState(hash: String, transform: (TorrentDownloadState) -> TorrentDownloadState) {
        val current = _downloads.value[hash] ?: return
        _downloads.value = _downloads.value + (hash to transform(current))
    }
}
