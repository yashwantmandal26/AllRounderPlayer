package com.example.ymediaplayer.ui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** One decoder and one pending request; rapid scrubbing always samples the latest target. */
@Composable
internal fun VideoSeekPreview(url: String, positionMs: Long) {
    val context = LocalContext.current
    val latestPosition = rememberUpdatedState(positionMs)
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url) {
        // Older retrievers only decode full-size frames; keep time-based seeking without
        // allocating a potentially huge 4K frame on low-memory API 24–26 devices.
        if (Build.VERSION.SDK_INT < 27) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            val cache = object : android.util.LruCache<Long, Bitmap>(2 * 1024 * 1024) {
                override fun sizeOf(key: Long, value: Bitmap) = value.allocationByteCount
            }
            try {
                retriever.setDataSource(context, url.toUri())
                var lastTime = -1L
                while (isActive) {
                    val time = latestPosition.value.coerceAtLeast(0L) / 500 * 500
                    if (time != lastTime) {
                        val result = cache.get(time) ?: retriever.getScaledFrameAtTime(
                            time * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 240, 136)
                        if (result != null) cache.put(time, result)
                        withContext(Dispatchers.Main) { bitmap = result }
                        lastTime = time
                    }
                    delay(80L)
                }
            } catch (_: Exception) {
                // Seeking remains available for streams or formats without thumbnail extraction.
            } finally { cache.evictAll(); retriever.release() }
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        bitmap?.let {
            Image(it.asImageBitmap(), contentDescription = "Seek preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier.width(160.dp).height(90.dp).clip(RoundedCornerShape(8.dp)))
        }
        Text(formatTime(positionMs), color = Color.White, fontSize = 15.sp)
        Text("Drag up for finer seeking", color = Color.White, fontSize = 11.sp)
    }
}
