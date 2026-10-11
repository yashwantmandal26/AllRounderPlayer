package com.example.ymediaplayer.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build

internal object VideoThumbnailFrame {
    fun timeMicros(durationMs: Long): Long =
        if (durationMs in 1..3000) (durationMs / 2) * 1000 else 3_000_000L

    fun extract(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val time = timeMicros(duration)
            fun frame(option: Int): Bitmap? = if (Build.VERSION.SDK_INT >= 27) {
                retriever.getScaledFrameAtTime(time, option, 360, 240)
            } else retriever.getFrameAtTime(time, option)
            // Prefer the actual frame at 3 seconds over an earlier keyframe.
            (try { frame(MediaMetadataRetriever.OPTION_CLOSEST) } catch (_: Exception) { null })
                ?: frame(MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Exception) { null }
        finally { try { retriever.release() } catch (_: Exception) {} }
    }
}
