package com.example.ymediaplayer.data

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class VideoInfo(
    val filename: String,
    val resolution: String,
    val duration: Long,
    val size: Long,
    val path: String,
    val dateAdded: Long,
    val mimeType: String,
    val frameRate: Int = 0
)

sealed interface FileOperationResult {
    data object Success : FileOperationResult
    data class NeedsConfirmation(val intentSender: IntentSender) : FileOperationResult
    data class Failure(val message: String) : FileOperationResult
}

class FileManager(private val context: Context) {

    suspend fun deleteVideos(uris: List<Uri>): FileOperationResult = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext FileOperationResult.Failure("No videos selected")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent = MediaStore.createTrashRequest(context.contentResolver, uris, true)
                FileOperationResult.NeedsConfirmation(pendingIntent.intentSender)
            } else {
                val deleted = uris.count { uri ->
                    context.contentResolver.delete(uri, null, null) > 0
                }
                if (deleted == uris.size) FileOperationResult.Success
                else FileOperationResult.Failure("Only deleted $deleted of ${uris.size} videos")
            }
        } catch (e: RecoverableSecurityException) {
            FileOperationResult.NeedsConfirmation(e.userAction.actionIntent.intentSender)
        } catch (e: Exception) { FileOperationResult.Failure(e.message ?: "Could not delete video") }
    }

    suspend fun deleteVideo(videoId: Long): FileOperationResult {
        val uri = android.content.ContentUris.withAppendedId(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoId
        )
        return deleteVideos(listOf(uri))
    }

    suspend fun renameVideo(videoId: Long, newName: String): FileOperationResult = withContext(Dispatchers.IO) {
        val uri = android.content.ContentUris.withAppendedId(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoId
        )
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, newName)
        }
        try {
            if (context.contentResolver.update(uri, values, null, null) > 0) FileOperationResult.Success
            else FileOperationResult.Failure("Video could not be renamed")
        } catch (e: RecoverableSecurityException) {
            FileOperationResult.NeedsConfirmation(e.userAction.actionIntent.intentSender)
        } catch (e: Exception) { FileOperationResult.Failure(e.message ?: "Could not rename video") }
    }

    fun buildShareIntent(uri: Uri): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    fun buildShareMultipleIntent(uris: List<Uri>): Intent =
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "video/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    suspend fun getVideoInfo(videoId: Long): VideoInfo? = withContext(Dispatchers.IO) {
        val uri = android.content.ContentUris.withAppendedId(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoId
        )
        val projection = arrayOf(
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.MIME_TYPE
        )
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@withContext null
                val width = runCatching { cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)) }.getOrDefault(0)
                val height = runCatching { cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)) }.getOrDefault(0)
                VideoInfo(
                    filename = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)) ?: "",
                    resolution = if (width > 0 && height > 0) "${width}×${height}" else "Unknown",
                    duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)),
                    size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)),
                    path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)) ?: "",
                    dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)),
                    mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)) ?: ""
                )
            }
        } catch (e: Exception) { null }
    }

    fun excludeFolder(folderPath: String): Boolean {
        return try {
            val dir = File(folderPath)
            val nomedia = File(dir, ".nomedia")
            if (!nomedia.exists()) nomedia.createNewFile() else true
        } catch (e: Exception) { false }
    }

    fun formatDate(epochSeconds: Long): String {
        return try {
            SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(epochSeconds * 1000L))
        } catch (e: Exception) { "Unknown" }
    }
}
