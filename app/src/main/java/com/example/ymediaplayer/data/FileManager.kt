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
    data class NeedsConfirmation(val intentSender: IntentSender, val retry: (suspend () -> FileOperationResult)? = null) : FileOperationResult
    data class Failure(val message: String) : FileOperationResult
}

class FileManager(private val context: Context) {
    companion object {
        fun supportsRecycleBin(sdkInt: Int = Build.VERSION.SDK_INT) = sdkInt >= Build.VERSION_CODES.R
        fun supportsRecycleBin(uris: List<Uri>) = supportsRecycleBin() && uris.all { it.scheme == "content" && it.authority == MediaStore.AUTHORITY }
        internal fun isValidFileName(name: String) = name.isNotBlank() && name != "." && name != ".." &&
            name.none { it == '/' || it == '\\' || it.isISOControl() }
    }

    suspend fun deleteVideos(uris: List<Uri>): FileOperationResult = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext FileOperationResult.Failure("No videos selected")
        val targets = uris.distinct()
        var remaining = targets
        try {
            val mediaUris = targets.filter { it.scheme == "content" && it.authority == MediaStore.AUTHORITY }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && mediaUris.isNotEmpty()) {
                val needsConsent = mediaUris.filter { uri ->
                    // App-owned media and previously granted items can be trashed
                    // directly. Some providers cancel redundant consent requests.
                    try {
                        context.contentResolver.update(uri, ContentValues().apply {
                            put(MediaStore.MediaColumns.IS_TRASHED, 1)
                        }, null, null) <= 0
                    } catch (_: SecurityException) { true }
                }
                val otherUris = targets.filterNot { it in mediaUris }
                if (needsConsent.isEmpty()) {
                    if (otherUris.isEmpty()) FileOperationResult.Success else deleteVideos(otherUris)
                } else {
                    val pendingIntent = MediaStore.createTrashRequest(context.contentResolver, needsConsent, true)
                    FileOperationResult.NeedsConfirmation(pendingIntent.intentSender,
                        retry = if (otherUris.isEmpty()) null else ({ deleteVideos(otherUris) }))
                }
            } else {
                var deleted = 0
                targets.forEachIndexed { index, uri ->
                    remaining = targets.drop(index)
                    val removed = if (uri.scheme == "file") File(requireNotNull(uri.path)).delete()
                        else context.contentResolver.delete(uri, null, null) > 0
                    if (removed) deleted++
                }
                if (deleted == targets.size) FileOperationResult.Success
                else FileOperationResult.Failure("Only deleted $deleted of ${targets.size} videos")
            }
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                FileOperationResult.NeedsConfirmation(e.userAction.actionIntent.intentSender,
                    retry = { deleteVideos(remaining) })
            } else {
                FileOperationResult.Failure(e.message ?: "Permission denied while deleting video")
            }
        } catch (e: Exception) { FileOperationResult.Failure(e.message ?: "Could not delete video") }
    }

    suspend fun deleteVideo(videoId: Long): FileOperationResult {
        val uri = android.content.ContentUris.withAppendedId(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoId
        )
        return deleteVideos(listOf(uri))
    }

    suspend fun renameVideo(videoId: Long, newName: String): FileOperationResult = renameVideo(
        android.content.ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoId), newName)

    private fun renameContentVideo(uri: Uri, newName: String): FileOperationResult {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, newName)
        }
        return try {
            if (context.contentResolver.update(uri, values, null, null) > 0) FileOperationResult.Success
            else FileOperationResult.Failure("Video could not be renamed")
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uri.authority == MediaStore.AUTHORITY) {
                FileOperationResult.NeedsConfirmation(
                    MediaStore.createWriteRequest(context.contentResolver, listOf(uri)).intentSender,
                    retry = { renameVideo(uri, newName) })
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                FileOperationResult.NeedsConfirmation(e.userAction.actionIntent.intentSender,
                    retry = { renameVideo(uri, newName) })
            } else {
                FileOperationResult.Failure(e.message ?: "Permission denied while renaming video")
            }
        } catch (e: Exception) { FileOperationResult.Failure(e.message ?: "Could not rename video") }
    }

    suspend fun renameVideo(uri: Uri, newName: String): FileOperationResult = withContext(Dispatchers.IO) {
        if (!isValidFileName(newName)) return@withContext FileOperationResult.Failure("Enter a valid file name without slashes")
        if (uri.scheme == "content") return@withContext renameContentVideo(uri, newName)
        if (uri.scheme != "file") return@withContext FileOperationResult.Failure("Unsupported file location")
        try {
            val file = File(requireNotNull(uri.path))
            if (!file.isFile) return@withContext FileOperationResult.Failure("Video no longer exists")
            val destination = File(file.parentFile, newName)
            if (destination == file) FileOperationResult.Success
            else if (destination.exists()) FileOperationResult.Failure("A file with that name already exists")
            else if (file.renameTo(destination)) FileOperationResult.Success
            else FileOperationResult.Failure("Video could not be renamed")
        } catch (e: Exception) { FileOperationResult.Failure(e.message ?: "Could not rename video") }
    }

    private fun shareUri(uri: Uri): Uri = if (uri.scheme == "file") {
        androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", File(requireNotNull(uri.path)))
    } else uri

    fun buildShareIntent(uri: Uri): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            val contentUri = shareUri(uri)
            putExtra(Intent.EXTRA_STREAM, contentUri)
            clipData = android.content.ClipData.newRawUri("Video", contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    fun buildShareMultipleIntent(uris: List<Uri>): Intent =
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "video/*"
            val contentUris = uris.map(::shareUri)
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(contentUris))
            contentUris.firstOrNull()?.let { first ->
                clipData = android.content.ClipData.newRawUri("Videos", first).apply {
                    contentUris.drop(1).forEach { addItem(android.content.ClipData.Item(it)) }
                }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    suspend fun getVideoInfo(videoId: Long): VideoInfo? = getVideoInfo(
        android.content.ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoId))

    private fun getContentVideoInfo(uri: Uri): VideoInfo? {
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
        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return null
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

    suspend fun getVideoInfo(uri: Uri): VideoInfo? = withContext(Dispatchers.IO) {
        if (uri.scheme == "content") return@withContext getContentVideoInfo(uri)
        if (uri.scheme != "file") return@withContext null
        val file = uri.path?.let(::File) ?: return@withContext null
        if (!file.isFile) return@withContext null
        val metadata = android.media.MediaMetadataRetriever()
        try {
            metadata.setDataSource(file.absolutePath)
            val width = metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            VideoInfo(file.name, if (width > 0 && height > 0) "${width}×${height}" else "Unknown",
                metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0,
                file.length(), file.absolutePath, file.lastModified() / 1000,
                metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "video/*")
        } catch (_: Exception) {
            VideoInfo(file.name, "Unknown", 0, file.length(), file.absolutePath, file.lastModified() / 1000, "video/*")
        } finally { runCatching { metadata.release() } }
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
