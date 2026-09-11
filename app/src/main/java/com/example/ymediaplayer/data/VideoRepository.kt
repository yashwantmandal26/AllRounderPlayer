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
    val dateAdded: Long = 0L         // epoch seconds
)

data class VideoFolder(
    val id: String,
    val name: String,
    val videos: List<VideoItem>
)

class VideoRepository(private val context: Context) {

    suspend fun getFoldersWithVideos(): List<VideoFolder> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<VideoItem>()

        val projection = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.DURATION)
            add(MediaStore.Video.Media.SIZE)
            add(MediaStore.Video.Media.BUCKET_ID)
            add(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            add(MediaStore.Video.Media.DATE_ADDED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Video.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC, ${MediaStore.Video.Media._ID} DESC"

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
                val relativePath = if (relativePathColumn >= 0)
                    cursor.getString(relativePathColumn) ?: "" else ""

                val contentUri: Uri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )

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
                        dateAdded = dateAdded
                    )
                )
            }
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

        folders
    }
}

