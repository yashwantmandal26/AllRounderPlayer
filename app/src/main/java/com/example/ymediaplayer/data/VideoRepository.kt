package com.example.ymediaplayer.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

        val videoDimensionsCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Int, Int>>()

        fun getVideoDimensions(uriString: String): Pair<Int, Int>? = videoDimensionsCache[uriString]

        fun cacheVideoDimensions(uriString: String, width: Int, height: Int) {
            if (width > 0 && height > 0) {
                videoDimensionsCache[uriString] = Pair(width, height)
            }
        }

        fun invalidateCache() {
            memoryCachedFolders = null
            lastCacheTime = 0L
        }
    }

    suspend fun getFoldersWithVideos(forceRefresh: Boolean = false): List<VideoFolder> = withContext(Dispatchers.IO) {
        val cached = memoryCachedFolders
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
