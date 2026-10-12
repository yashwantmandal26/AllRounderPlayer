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

    companion object {
        // High-speed static in-memory LRU cache to make thumbnail reloads instantaneous (0ms)
        private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        private val cacheSizeKb = (maxMemory / 5).coerceIn(32 * 1024, 128 * 1024)

        val memoryCache = object : android.util.LruCache<String, Bitmap>(cacheSizeKb) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return (bitmap.byteCount / 1024).coerceAtLeast(1)
            }
        }
    }

    override suspend fun fetch(): FetchResult? {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null

        val cacheKey = java.security.MessageDigest.getInstance("SHA-256")
            .digest(uri.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }

        // 1. ULTRA-FAST PATH: Check in-memory LRU cache (0ms lookup, 0 disk I/O, 0 decode)
        memoryCache.get(cacheKey)?.let { memBmp ->
            if (!memBmp.isRecycled) {
                return ImageFetchResult(
                    image = memBmp.asImage(),
                    isSampled = false,
                    dataSource = DataSource.MEMORY
                )
            }
        }

        val thumbCacheDir = java.io.File(context.cacheDir, "video_thumbs_frame3_v3").apply {
            if (!exists()) mkdirs()
        }
        val cacheFile = java.io.File(thumbCacheDir, "$cacheKey.webp")

        // 2. DISK PATH: Check persistent WebP disk cache, populate memory cache, return
        if (cacheFile.exists() && cacheFile.length() > 0) {
            try {
                val opts = android.graphics.BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val cachedBmp = android.graphics.BitmapFactory.decodeFile(cacheFile.absolutePath, opts)
                    ?: android.graphics.BitmapFactory.decodeFile(cacheFile.absolutePath)
                if (cachedBmp != null) {
                    memoryCache.put(cacheKey, cachedBmp)
                    return ImageFetchResult(
                        image = cachedBmp.asImage(),
                        isSampled = false,
                        dataSource = DataSource.DISK
                    )
                }
            } catch (_: Exception) {}
        }

        // 3. EXTRACTION: Extract a timestamped frame; persist to both memory and disk
        try {
            val bitmap = VideoThumbnailFrame.extract(context, uri)

            if (bitmap != null) {
                memoryCache.put(cacheKey, bitmap)
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
