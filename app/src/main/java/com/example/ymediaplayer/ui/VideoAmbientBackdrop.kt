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

private const val SAMPLE_WIDTH = 48
private const val SAMPLE_HEIGHT = 27
private const val ACTIVE_CAPTURE_INTERVAL_MS = 40L // ~25 FPS real-time sync with video

/** Low-resolution surface capture with bounded work and no queued frames. */
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
                // Published bitmaps are immutable: never recycle or overwrite a frame still in use by the GPU.
                val target = Bitmap.createBitmap(SAMPLE_WIDTH, SAMPLE_HEIGHT, Bitmap.Config.ARGB_8888)
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
                .blur((12 + spread * 36).dp)
                .drawBehind {
                    frame.value?.let { image ->
                        // 1.15x overscale to eliminate dark letterbox border influence on ambient color
                        val scale = max(size.width / image.width, size.height / image.height) * 1.15f
                        val width = (image.width * scale).roundToInt()
                        val height = (image.height * scale).roundToInt()
                        drawImage(
                            image = image,
                            dstOffset = IntOffset(
                                ((size.width - width) / 2).roundToInt(),
                                ((size.height - height) / 2).roundToInt()
                            ),
                            dstSize = IntSize(width, height),
                            filterQuality = FilterQuality.Low
                        )
                    }
                }
        )
        // Dynamic darkness overlay adapting to user ambient glow strength
        val dimAlpha = (1f - (0.44f * glowStrength.coerceIn(0.2f, 1.8f))).coerceIn(0.35f, 0.88f)
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(Color.Black.copy(alpha = dimAlpha))
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
