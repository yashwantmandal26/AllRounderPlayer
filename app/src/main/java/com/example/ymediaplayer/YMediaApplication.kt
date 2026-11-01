package com.example.ymediaplayer

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.video.VideoFrameDecoder
import com.example.ymediaplayer.util.MediaStoreVideoFetcher
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath

class YMediaApplication : Application(), SingletonImageLoader.Factory {

    companion object {
        lateinit var instance: YMediaApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Warm up video thumbnails in background immediately on app start
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val repo = com.example.ymediaplayer.data.VideoRepository(this@YMediaApplication)
                val cached = repo.getCachedFoldersSync()
                if (cached != null) {
                    val uris = cached.flatMap { it.videos }.map { it.uri }
                    com.example.ymediaplayer.util.MediaStoreVideoFetcher.warmUpThumbnails(this@YMediaApplication, uris)
                }
            } catch (_: Throwable) {}
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(MediaStoreVideoFetcher.Factory())
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.35)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.filesDir.resolve("coil_disk_cache").absolutePath.toPath())
                    .maxSizeBytes(250L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
