package com.example.ymediaplayer.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options

/**
 * High-performance Coil 3 Fetcher that leverages Android OS MediaStore pre-computed thumbnails.
 * Instead of spinning up a full video decoder to parse the MP4 file, it retrieves the system's
 * cached thumbnail in <2ms.
 */
class MediaStoreVideoFetcher(
    private val context: Context,
    private val uri: Uri
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null

        val cacheKey = uri.lastPathSegment ?: uri.hashCode().toString()
        val thumbCacheDir = java.io.File(context.cacheDir, "video_thumbs").apply {
            if (!exists()) mkdirs()
        }
        val cacheFile = java.io.File(thumbCacheDir, "$cacheKey.webp")

        // 1. FAST PATH: Check persistent WebP disk cache (< 1ms read, 0 IPC overhead)
        if (cacheFile.exists() && cacheFile.length() > 0) {
            try {
                val cachedBmp = android.graphics.BitmapFactory.decodeFile(cacheFile.absolutePath)
                if (cachedBmp != null) {
                    return ImageFetchResult(
                        image = cachedBmp.asImage(),
                        isSampled = false,
                        dataSource = DataSource.DISK
                    )
                }
            } catch (_: Exception) {}
        }

        // 2. FETCH FROM SYSTEM: MediaStore pre-computed hardware thumbnail
        try {
            var bitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ (API 29+): Instant hardware-accelerated MediaStore thumbnail
                try {
                    context.contentResolver.loadThumbnail(uri, Size(360, 240), null)
                } catch (_: Exception) { null }
            } else {
                // Android 9 and below: MediaStore.Video.Thumbnails
                val id = uri.lastPathSegment?.toLongOrNull()
                if (id != null) {
                    @Suppress("DEPRECATION")
                    MediaStore.Video.Thumbnails.getThumbnail(
                        context.contentResolver,
                        id,
                        MediaStore.Video.Thumbnails.MINI_KIND,
                        null
                    )
                } else null
            }

            // 3. FALLBACK: Quick keyframe sync decode if system thumbnail hasn't been generated yet
            if (bitmap == null) {
                bitmap = try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(context, uri)
                    val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        retriever.getScaledFrameAtTime(
                            1_000_000L,
                            android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            360, 240
                        ) ?: retriever.getFrameAtTime(1_000_000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } else {
                        retriever.getFrameAtTime(1_000_000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    }
                    try { retriever.release() } catch (_: Exception) {}
                    frame
                } catch (_: Exception) { null }
            }

            // 4. PERSIST TO DISK: Write compressed WebP so subsequent loads are instantaneous
            if (bitmap != null) {
                try {
                    val tempFile = java.io.File(thumbCacheDir, "${cacheKey}_tmp.webp")
                    java.io.FileOutputStream(tempFile).use { out ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 82, out)
                        } else {
                            @Suppress("DEPRECATION")
                            bitmap.compress(Bitmap.CompressFormat.WEBP, 82, out)
                        }
                    }
                    tempFile.renameTo(cacheFile)
                } catch (_: Exception) {}

                return ImageFetchResult(
                    image = bitmap.asImage(),
                    isSampled = false,
                    dataSource = DataSource.DISK
                )
            }
        } catch (_: Exception) {
            // Fall through to other decoders if all fail
        }
        return null
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme == ContentResolver.SCHEME_CONTENT) {
                return MediaStoreVideoFetcher(options.context, data)
            }
            return null
        }
    }
}
