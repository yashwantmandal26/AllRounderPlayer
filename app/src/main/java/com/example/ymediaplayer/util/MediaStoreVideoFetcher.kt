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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Caches video frames extracted at three seconds, independently of system posters. */
class MediaStoreVideoFetcher(
    private val context: Context,
    private val uri: Uri
) : Fetcher {

    companion object {
        // High-speed static in-memory LRU cache to make thumbnail reloads instantaneous (0ms)
        private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        private val cacheSizeKb = (maxMemory / 4).coerceIn(48 * 1024, 192 * 1024)

        val memoryCache = object : android.util.LruCache<String, Bitmap>(cacheSizeKb) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return (bitmap.byteCount / 1024).coerceAtLeast(1)
            }
        }

        fun getThumbDirectory(context: Context): java.io.File {
            // Use filesDir so it is permanent and NEVER purged by Android OS background cleaner
            val dir = java.io.File(context.filesDir, "video_thumbs_permanent_v1")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

        fun getCacheKey(uri: Uri): String {
            return java.security.MessageDigest.getInstance("SHA-256")
                .digest(uri.toString().toByteArray())
                .joinToString("") { "%02x".format(it) }
        }

        /**
         * Migrate legacy thumbnails from cacheDir to persistent filesDir so existing thumbnails are not lost.
         */
        fun migrateOldCacheIfNeeded(context: Context) {
            try {
                val oldDir = java.io.File(context.cacheDir, "video_thumbs_frame3_v3")
                val newDir = getThumbDirectory(context)
                if (oldDir.exists() && oldDir.isDirectory) {
                    oldDir.listFiles()?.forEach { file ->
                        if (file.isFile && file.length() > 0) {
                            val dest = java.io.File(newDir, file.name)
                            if (!dest.exists()) {
                                file.copyTo(dest, overwrite = true)
                            }
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        /**
         * Preload and warm up thumbnails in memory cache for instant, zero-flicker display on app launch.
         */
        fun warmUpThumbnails(context: Context, uris: List<Uri>) {
            if (uris.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch {
                migrateOldCacheIfNeeded(context)
                val thumbDir = getThumbDirectory(context)
                val opts = android.graphics.BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val imageLoader = coil3.SingletonImageLoader.get(context)
                val targetUris = uris.take(80)
                for (uri in targetUris) {
                    val key = getCacheKey(uri)
                    var bmp = memoryCache.get(key)
                    if (bmp == null || bmp.isRecycled) {
                        val file = java.io.File(thumbDir, "$key.webp")
                        if (file.exists() && file.length() > 0) {
                            try {
                                bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
                                if (bmp != null) {
                                    memoryCache.put(key, bmp)
                                }
                            } catch (_: Throwable) {}
                        }
                    }
                    if (bmp != null && !bmp.isRecycled) {
                        try {
                            imageLoader.memoryCache?.set(
                                coil3.memory.MemoryCache.Key("video-thumb-v4:$uri"),
                                coil3.memory.MemoryCache.Value(bmp.asImage())
                            )
                        } catch (_: Throwable) {}
                    }
                }
            }
        }
    }

    override suspend fun fetch(): FetchResult? {
        val scheme = uri.scheme
        if (scheme != ContentResolver.SCHEME_CONTENT && scheme != ContentResolver.SCHEME_FILE) return null

        val cacheKey = getCacheKey(uri)

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

        val thumbDir = getThumbDirectory(context)
        val cacheFile = java.io.File(thumbDir, "$cacheKey.webp")

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

        // 3. EXTRACTION:
        // Try hardware-accelerated MediaStore thumbnail on Android Q+ (1-2ms lookup)
        var bitmap: Bitmap? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                bitmap = context.contentResolver.loadThumbnail(uri, Size(360, 240), null)
            } catch (_: Throwable) {}
        }

        // Fallback to retriever extraction if native hardware thumbnail is unavailable
        if (bitmap == null) {
            try {
                bitmap = VideoThumbnailFrame.extract(context, uri)
            } catch (_: Throwable) {}
        }

        if (bitmap != null) {
            memoryCache.put(cacheKey, bitmap)
            try {
                val tempFile = java.io.File(thumbDir, "${cacheKey}_tmp.webp")
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

        return null
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val scheme = data.scheme
            if (scheme == ContentResolver.SCHEME_CONTENT || scheme == ContentResolver.SCHEME_FILE) {
                val path = data.path ?: ""
                val isVideo = path.contains("/video/") ||
                    path.endsWith(".mp4", ignoreCase = true) ||
                    path.endsWith(".mkv", ignoreCase = true) ||
                    path.endsWith(".webm", ignoreCase = true) ||
                    path.endsWith(".avi", ignoreCase = true) ||
                    path.endsWith(".mov", ignoreCase = true) ||
                    path.endsWith(".ts", ignoreCase = true) ||
                    runCatching { options.context.contentResolver.getType(data)?.startsWith("video/") == true }.getOrDefault(false)

                if (isVideo) {
                    return MediaStoreVideoFetcher(options.context, data)
                }
            }
            return null
        }
    }
}
