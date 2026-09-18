package com.example.ymediaplayer.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import java.io.ByteArrayOutputStream

/**
 * Utility for resolving and compressing album art (music) and video frame thumbnails (video)
 * into high quality byte arrays suitable for Media3 [androidx.media3.common.MediaMetadata.setArtworkData].
 *
 * Handles Scoped Storage on Android 10+ (API 29+) including Android 13-15 where legacy
 * content://media/external/audio/albumart URIs fail.
 */
object MediaArtworkHelper {

    private const val MAX_ARTWORK_DIMENSION = 512
    private const val JPEG_QUALITY = 85

    /**
     * Resolves artwork for audio playback.
     * Tries:
     * 1. MediaStore thumbnail via [ContentResolver.loadThumbnail] on API 29+ using [songUri]
     * 2. Embedded ID3 APIC picture via [MediaMetadataRetriever] from [songUri]
     * 3. Legacy [albumArtUri] stream decode (for Android 9 and below or cached files)
     */
    fun getAudioArtworkBytes(
        context: Context,
        songUri: Uri?,
        albumArtUri: Uri? = null
    ): ByteArray? {
        val cr = context.contentResolver

        // Strategy 1: Android 10+ ContentResolver.loadThumbnail from the audio track Uri
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && songUri != null) {
            try {
                val thumb = cr.loadThumbnail(songUri, Size(MAX_ARTWORK_DIMENSION, MAX_ARTWORK_DIMENSION), null)
                return compressToJpegBytes(thumb)
            } catch (_: Throwable) {}
        }

        // Strategy 2: Extract embedded artwork via MediaMetadataRetriever from the actual audio file Uri
        if (songUri != null) {
            try {
                MediaMetadataRetriever().use { mmr ->
                    mmr.setDataSource(context, songUri)
                    val rawBytes = mmr.embeddedPicture
                    if (rawBytes != null && rawBytes.isNotEmpty()) {
                        val bmp = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                        if (bmp != null) {
                            return compressToJpegBytes(bmp)
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        // Strategy 3: Open albumArtUri stream (legacy MediaStore album art or file URI)
        if (albumArtUri != null) {
            try {
                cr.openInputStream(albumArtUri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        return compressToJpegBytes(bmp)
                    }
                }
            } catch (_: Throwable) {}
        }

        return null
    }

    /**
     * Resolves and returns a Bitmap for audio playback (e.g. for notification largeIcon).
     */
    fun getAudioArtworkBitmap(
        context: Context,
        songUri: Uri?,
        albumArtUri: Uri? = null
    ): Bitmap? {
        val bytes = getAudioArtworkBytes(context, songUri, albumArtUri) ?: return null
        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Resolves video thumbnail for video playback.
     * Tries:
     * 1. Hardware accelerated thumbnail via [ContentResolver.loadThumbnail] on API 29+
     * 2. Frame extraction via [MediaMetadataRetriever] at 1 second
     */
    fun getVideoThumbnailBytes(context: Context, videoUri: Uri?): ByteArray? {
        if (videoUri == null) return null
        val cr = context.contentResolver

        // Strategy 1: Android 10+ loadThumbnail
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val thumb = cr.loadThumbnail(videoUri, Size(MAX_ARTWORK_DIMENSION, MAX_ARTWORK_DIMENSION), null)
                return compressToJpegBytes(thumb)
            } catch (_: Throwable) {}
        }

        // Strategy 2: MediaMetadataRetriever keyframe
        try {
            MediaMetadataRetriever().use { mmr ->
                mmr.setDataSource(context, videoUri)
                val frame = mmr.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: mmr.frameAtTime
                if (frame != null) {
                    return compressToJpegBytes(frame)
                }
            }
        } catch (_: Throwable) {}

        return null
    }

    fun compressToJpegBytes(bitmap: Bitmap): ByteArray {
        val scaled = if (bitmap.width > MAX_ARTWORK_DIMENSION || bitmap.height > MAX_ARTWORK_DIMENSION) {
            val ratio = MAX_ARTWORK_DIMENSION.toFloat() / maxOf(bitmap.width, bitmap.height)
            val w = (bitmap.width * ratio).toInt().coerceAtLeast(1)
            val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, w, h, true)
        } else {
            bitmap
        }

        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos)
        return baos.toByteArray()
    }
}
