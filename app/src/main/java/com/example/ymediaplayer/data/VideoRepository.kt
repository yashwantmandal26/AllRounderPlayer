package com.example.ymediaplayer.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

@Serializable
private data class DiskVideoItem(
    val id: Long,
    val uriString: String,
    val title: String,
    val duration: Long,
    val size: Long,
    val bucketId: String,
    val bucketName: String,
    val relativePath: String = "",
    val dateAdded: Long = 0L,
    val width: Int = 0,
    val height: Int = 0
)

@Serializable
private data class DiskVideoFolder(
    val id: String,
    val name: String,
    val videos: List<DiskVideoItem>
)

data class VideoItem(
    val id: Long,
    val uri: Uri,
    val title: String,
    val duration: Long,
    val size: Long,
    val bucketId: String,
    val bucketName: String,
    val relativePath: String = "",   // e.g. "DCIM/Camera/"
    val dateAdded: Long = 0L,        // epoch seconds
    val width: Int = 0,
    val height: Int = 0
)

data class VideoFolder(
    val id: String,
    val name: String,
    val videos: List<VideoItem>
)

class VideoRepository(private val context: Context) {

    companion object {
        @Volatile
        private var memoryCachedFolders: List<VideoFolder>? = null
        @Volatile
        private var lastCacheTime: Long = 0L

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

        val videoDimensionsCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Int, Int>>()

        fun getVideoDimensions(uriString: String): Pair<Int, Int>? {
            videoDimensionsCache[uriString]?.let { return it }
            try {
                val mmr = android.media.MediaMetadataRetriever()
                try {
                    val path = if (uriString.startsWith("file://")) uriString.removePrefix("file://") else uriString
                    if (path.startsWith("/")) {
                        mmr.setDataSource(path)
                    } else {
                        mmr.setDataSource(com.example.ymediaplayer.YMediaApplication.instance, android.net.Uri.parse(uriString))
                    }
                    val w = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    val h = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    val rot = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    val effW = if (rot == 90 || rot == 270) h else w
                    val effH = if (rot == 90 || rot == 270) w else h
                    if (effW > 0 && effH > 0) {
                        val pair = Pair(effW, effH)
                        videoDimensionsCache[uriString] = pair
                        return pair
                    }
                } finally {
                    try { mmr.release() } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
            return null
        }

        fun cacheVideoDimensions(uriString: String, width: Int, height: Int) {
            if (width > 0 && height > 0) {
                videoDimensionsCache[uriString] = Pair(width, height)
            }
        }

        fun invalidateCache() {
            memoryCachedFolders = null
            lastCacheTime = 0L
        }

        val SUPPORTED_VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "webm", "avi", "mov", "ts",
            "m4v", "flv", "wmv", "3gp", "3g2", "mpg",
            "mpeg", "vob", "m2ts", "mts", "ogv", "asf",
            "rm", "rmvb", "divx", "f4v"
        )
    }

    /**
     * Fast synchronous disk-cache lookup (1-2ms) so UI displays folders instantly on app open without loading spinners.
     */
    fun getCachedFoldersSync(): List<VideoFolder>? {
        memoryCachedFolders?.let { return it }
        try {
            val file = java.io.File(context.filesDir, "video_folders_cache.json")
            if (file.exists() && file.length() > 0) {
                val raw = file.readText()
                val diskFolders = json.decodeFromString<List<DiskVideoFolder>>(raw)
                val mapped = diskFolders.map { f ->
                    VideoFolder(
                        id = f.id,
                        name = f.name,
                        videos = f.videos.map { v ->
                            VideoItem(
                                id = v.id,
                                uri = Uri.parse(v.uriString),
                                title = v.title,
                                duration = v.duration,
                                size = v.size,
                                bucketId = v.bucketId,
                                bucketName = v.bucketName,
                                relativePath = v.relativePath,
                                dateAdded = v.dateAdded,
                                width = v.width,
                                height = v.height
                            )
                        }
                    )
                }
                if (mapped.isNotEmpty()) {
                    memoryCachedFolders = mapped
                    lastCacheTime = System.currentTimeMillis()
                    return mapped
                }
            }
        } catch (_: Exception) {}
        return null
    }

    fun getCachedFolderByIdSync(folderId: String): VideoFolder? {
        return getCachedFoldersSync()?.find { it.id == folderId }
    }

    suspend fun getFoldersWithVideos(forceRefresh: Boolean = false): List<VideoFolder> = withContext(Dispatchers.IO) {
        val cached = memoryCachedFolders ?: getCachedFoldersSync()
        val now = System.currentTimeMillis()
        // Serve from memory if fresh within 15 seconds, unless explicitly invalidated
        if (!forceRefresh && cached != null && (now - lastCacheTime < 15_000L)) {
            return@withContext cached
        }

        val videos = mutableListOf<VideoItem>()

        val projection = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.DURATION)
            add(MediaStore.Video.Media.SIZE)
            add(MediaStore.Video.Media.BUCKET_ID)
            add(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            add(MediaStore.Video.Media.DATE_ADDED)
            add(MediaStore.Video.Media.WIDTH)
            add(MediaStore.Video.Media.HEIGHT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Video.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC, ${MediaStore.Video.Media._ID} DESC"

        try {
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
            val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val widthColumn = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
            val heightColumn = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
            val relativePathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH) else -1

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "Unknown"
                val duration = cursor.getLong(durationColumn)
                val size = cursor.getLong(sizeColumn)
                val bucketId = cursor.getString(bucketIdColumn) ?: "0"
                val bucketName = cursor.getString(bucketNameColumn) ?: "Unknown Folder"
                val dateAdded = cursor.getLong(dateAddedColumn)
                val width = if (widthColumn >= 0) cursor.getInt(widthColumn) else 0
                val height = if (heightColumn >= 0) cursor.getInt(heightColumn) else 0
                val relativePath = if (relativePathColumn >= 0)
                    cursor.getString(relativePathColumn) ?: "" else ""

                val contentUri: Uri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                if (width > 0 && height > 0) {
                    videoDimensionsCache[contentUri.toString()] = Pair(width, height)
                }

                videos.add(
                    VideoItem(
                        id = id,
                        uri = contentUri,
                        title = name,
                        duration = duration,
                        size = size,
                        bucketId = bucketId,
                        bucketName = bucketName,
                        relativePath = relativePath,
                        dateAdded = dateAdded,
                        width = width,
                        height = height
                    )
                )
            }
        }
        } catch (_: SecurityException) {
            return@withContext emptyList()
        }

        // Also scan local MYCinema downloads folder
        try {
            val moviesDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)
            val cinemaDir = if (moviesDir != null) java.io.File(moviesDir, "MYCinema") else null
            if (cinemaDir != null && cinemaDir.exists() && cinemaDir.isDirectory) {
                cinemaDir.walkTopDown().filter { file ->
                    val ext = file.extension.lowercase()
                    file.isFile && file.length() > 0 && ext in SUPPORTED_VIDEO_EXTENSIONS
                }.forEach { file ->
                    val fileUri = android.net.Uri.fromFile(file)
                    if (videos.none { it.uri.path == file.absolutePath || it.uri == fileUri }) {
                        val duration = try {
                            val retriever = android.media.MediaMetadataRetriever()
                            try {
                                retriever.setDataSource(file.absolutePath)
                                val dStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                                dStr?.toLongOrNull() ?: 0L
                            } finally {
                                try { retriever.release() } catch (_: Exception) {}
                            }
                        } catch (_: Exception) { 0L }

                        videos.add(
                            VideoItem(
                                id = file.absolutePath.hashCode().toLong(),
                                uri = fileUri,
                                title = file.nameWithoutExtension,
                                duration = duration,
                                size = file.length(),
                                bucketId = "mycinema",
                                bucketName = "MYCinema",
                                relativePath = "Movies/MYCinema/",
                                dateAdded = file.lastModified() / 1000L
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        // Group by folder bucket ID
        val folders = videos.groupBy { it.bucketId }
            .map { (bucketId, videoList) ->
                VideoFolder(
                    id = bucketId,
                    name = videoList.first().bucketName,
                    videos = videoList
                )
            }
            .sortedBy { it.name }

        memoryCachedFolders = folders
        lastCacheTime = now

        // Persist to disk cache asynchronously so next app open is 100% instantaneous
        try {
            val diskFolders = folders.map { f ->
                DiskVideoFolder(
                    id = f.id,
                    name = f.name,
                    videos = f.videos.map { v ->
                        DiskVideoItem(
                            id = v.id,
                            uriString = v.uri.toString(),
                            title = v.title,
                            duration = v.duration,
                            size = v.size,
                            bucketId = v.bucketId,
                            bucketName = v.bucketName,
                            relativePath = v.relativePath,
                            dateAdded = v.dateAdded,
                            width = v.width,
                            height = v.height
                        )
                    }
                )
            }
            val raw = json.encodeToString(diskFolders)
            val cacheFile = java.io.File(context.filesDir, "video_folders_cache.json")
            cacheFile.writeText(raw)
        } catch (_: Exception) {}

        folders
    }

    suspend fun getFolderById(folderId: String): VideoFolder? {
        val folders = getFoldersWithVideos(forceRefresh = false)
        return folders.find { it.id == folderId }
    }

    suspend fun getAllVideos(): List<VideoItem> {
        val folders = getFoldersWithVideos(forceRefresh = false)
        return folders.flatMap { it.videos }
    }
}
