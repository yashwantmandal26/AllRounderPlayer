package com.example.ymediaplayer.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt

private const val ACTIVE_CAPTURE_INTERVAL_MS = 40L // ~25 FPS real-time sync with video

/** High-fidelity low-overhead surface capture for authentic ambient backlighting. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun VideoAmbientBackdrop(
    playerView: PlayerView,
    mediaUrl: String,
    isPlaying: Boolean = true,
    glowStrength: Float = 1.0f,
    spread: Float = 0.5f,
    batterySaver: Boolean = false
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val frame = remember(playerView, mediaUrl) { mutableStateOf<ImageBitmap?>(null) }

    val playing = rememberUpdatedState(isPlaying)
    val saveBattery = rememberUpdatedState(batterySaver)
    val workerThread = remember { HandlerThread("AmbientCapture").apply { start() } }
    val bgHandler = remember(workerThread) { Handler(workerThread.looper) }
    DisposableEffect(workerThread) {
        onDispose { workerThread.quitSafely() }
    }
    LaunchedEffect(playerView, mediaUrl, lifecycle) {
        if (mediaUrl.isBlank()) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var lastPosition = Long.MIN_VALUE
            while (isActive) {
                // Keep the last color while paused; refresh once after seeking while paused.
                val position = playerView.player?.currentPosition ?: 0L
                if (!playing.value && frame.value != null && position == lastPosition) {
                    delay(120L)
                    continue
                }
                withFrameNanos { }

                // Determine native aspect ratio of the active video
                val videoSize = playerView.player?.videoSize
                val vw = videoSize?.width?.takeIf { it > 0 }
                    ?: (playerView.videoSurfaceView as? SurfaceView)?.width?.takeIf { it > 0 }
                    ?: 16
                val vh = videoSize?.height?.takeIf { it > 0 }
                    ?: (playerView.videoSurfaceView as? SurfaceView)?.height?.takeIf { it > 0 }
                    ?: 9

                val maxDim = 96
                val (sampleW, sampleH) = if (vw >= vh) {
                    maxDim to max(16, (maxDim * (vh.toFloat() / vw)).roundToInt())
                } else {
                    max(16, (maxDim * (vw.toFloat() / vh)).roundToInt()) to maxDim
                }

                val target = Bitmap.createBitmap(sampleW, sampleH, Bitmap.Config.ARGB_8888)
                val captured = when (val surface = playerView.videoSurfaceView) {
                    is TextureView -> if (surface.isAvailable) {
                        try { surface.getBitmap(target); true } catch (_: IllegalStateException) { false }
                    } else false
                    is SurfaceView -> captureSurfaceFrame(surface, target, bgHandler)
                    else -> false
                }
                if (isActive && captured) {
                    frame.value = target.asImageBitmap()
                    lastPosition = position
                }
                if (saveBattery.value) delay(100L) else delay(ACTIVE_CAPTURE_INTERVAL_MS)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .blur((28 + spread * 44).dp)
                .drawBehind {
                    frame.value?.let { image ->
                        // Draw without aggressive zoom, gracefully mapping the video's edge colors
                        drawImage(
                            image = image,
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                            filterQuality = FilterQuality.Medium
                        )
                    }
                }
        )
        // Luminous ambient vignette: vibrant light radiating around the video, smoothly fading to deep black at edges
        val baseGlow = glowStrength.coerceIn(0.2f, 1.8f)
        val centerAlpha = (1f - (0.60f * baseGlow)).coerceIn(0.12f, 0.70f)
        val edgeAlpha = (centerAlpha + 0.35f).coerceAtMost(0.92f)

        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = centerAlpha),
                                Color.Black.copy(alpha = (centerAlpha + edgeAlpha) / 2f),
                                Color.Black.copy(alpha = edgeAlpha)
                            ),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = max(size.width, size.height) * 0.75f
                        )
                    )
                }
        )
    }
}

private suspend fun captureSurfaceFrame(
    surface: SurfaceView,
    destination: Bitmap,
    handler: Handler
): Boolean {
    if (!surface.holder.surface.isValid) return false
    return suspendCancellableCoroutine { continuation ->
        try {
            PixelCopy.request(surface, destination, { result ->
                if (continuation.isActive) {
                    continuation.resume(result == PixelCopy.SUCCESS)
                }
            }, handler)
        } catch (_: Throwable) {
            if (continuation.isActive) continuation.resume(false)
        }
    }
}
