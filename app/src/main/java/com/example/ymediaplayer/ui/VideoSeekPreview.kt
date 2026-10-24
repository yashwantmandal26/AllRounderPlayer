package com.example.ymediaplayer.ui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** One decoder and one pending request; rapid scrubbing always samples the latest target. */
@Composable
internal fun VideoSeekPreview(
    url: String,
    positionMs: Long,
    primaryAccent: Color = Color(0xFFFF3B30),
    pointerOffset: Dp? = null
) {
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

    val cardBg = Color(0xFF10121A).copy(alpha = 0.95f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(150.dp)
    ) {
        // Main preview card
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(12.dp), ambientColor = Color.Black.copy(0.75f), spotColor = Color.Black.copy(0.75f))
                .clip(RoundedCornerShape(12.dp))
                .background(cardBg)
                .border(1.2.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                .padding(5.dp)
        ) {
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Seek preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Movie,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = formatTime(positionMs),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Text(
                text = "Drag up for finer seeking",
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal
            )
        }

        // Downward pointer caret pointing directly to the slider circle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
        ) {
            val caretOffset = pointerOffset ?: 75.dp
            Canvas(
                modifier = Modifier
                    .offset(x = (caretOffset - 6.dp).coerceAtLeast(4.dp))
                    .size(12.dp, 6.dp)
            ) {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                }
                drawPath(path, color = cardBg)
            }
        }
    }
}
