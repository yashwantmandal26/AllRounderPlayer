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

/** Caches video frames extracted at three seconds, independently of system posters. */
class MediaStoreVideoFetcher(
    private val context: Context,
    private val uri: Uri
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null

        val cacheKey = java.security.MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray()).joinToString("") { "%02x".format(it) }
        val thumbCacheDir = java.io.File(context.cacheDir, "video_thumbs_frame3_v3").apply {
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

        // Extract a timestamped frame; system posters do not honor a timestamp.
        try {
            val bitmap = VideoThumbnailFrame.extract(context, uri)

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
            if (data.scheme == ContentResolver.SCHEME_CONTENT &&
                (data.path?.contains("/video/") == true ||
                    runCatching { options.context.contentResolver.getType(data)?.startsWith("video/") == true }.getOrDefault(false))) {
                return MediaStoreVideoFetcher(options.context, data)
            }
            return null
        }
    }
}
