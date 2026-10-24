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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
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

private const val ACTIVE_CAPTURE_INTERVAL_MS = 36L // ~28 FPS real-time sync

/**
 * Authentic, cinematic Ambilight-style ambient backlighting.
 *
 * 1. Aspect-correct halo expansion: Projects edge colors outward from the video's actual boundaries.
 * 2. Saturation boost: Enriches captured video frames with vivid, radiant color (no dull gray wash).
 * 3. Deep Gaussian blur (48dp-80dp): Silky light dispersion without banding.
 * 4. Zero-allocation capture loop: Reuses persistent bitmaps to eliminate GC stutter.
 * 5. Edge vignette: Graceful feathering to pure OLED black at screen edges.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun VideoAmbientBackdrop(
    playerView: PlayerView,
    mediaUrl: String,
    isPlaying: Boolean = true,
    glowStrength: Float = 1.0f,
    spread: Float = 0.5f,
    saturation: Float = 1.0f,
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
            var reusableBitmap: Bitmap? = null

            try {
                while (isActive) {
                    val position = playerView.player?.currentPosition ?: 0L
                    if (!playing.value && frame.value != null && position == lastPosition) {
                        delay(120L)
                        continue
                    }
                    withFrameNanos { }

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

                    if (reusableBitmap == null || reusableBitmap.width != sampleW || reusableBitmap.height != sampleH) {
                        reusableBitmap = Bitmap.createBitmap(sampleW, sampleH, Bitmap.Config.ARGB_8888)
                    }

                    val target = reusableBitmap ?: break
                    val captured = when (val surface = playerView.videoSurfaceView) {
                        is TextureView -> if (surface.isAvailable) {
                            try { surface.getBitmap(target); true } catch (_: Throwable) { false }
                        } else false
                        is SurfaceView -> captureSurfaceFrame(surface, target, bgHandler)
                        else -> false
                    }

                    if (isActive && captured) {
                        frame.value = target.asImageBitmap()
                        lastPosition = position
                    }

                    if (saveBattery.value) delay(90L) else delay(ACTIVE_CAPTURE_INTERVAL_MS)
                }
            } finally {
                // Do not explicitly recycle reusableBitmap here as frame.value (ImageBitmap) may still be drawn by Compose Canvas
            }
        }
    }

    // High-vibrancy Ambilight color matrix: boosts saturation so ambient light radiates rich, vivid color
    val colorMatrix = remember(saturation) {
        ColorMatrix().apply {
            setToSaturation((1.95f * saturation.coerceIn(0.4f, 2.5f)).coerceIn(0.2f, 4.5f))
        }
    }

    val ambientScale = 1.30f + (spread.coerceIn(0.1f, 1.5f) * 0.70f) // 1.37x - 2.35x projection
    val blurRadius = (38 + spread.coerceIn(0.1f, 1.5f) * 44).dp     // 42dp - 104dp silky diffusion
    val effectiveGlow = glowStrength.coerceIn(0.2f, 2.0f)

    Box(Modifier.fillMaxSize()) {
        // 1. Ambient Glow Layer: accurately projected from video boundaries with saturation boost
        Box(
            Modifier
                .fillMaxSize()
                .blur(blurRadius)
                .drawBehind {
                    frame.value?.let { image ->
                        val screenW = size.width
                        val screenH = size.height
                        if (screenW <= 0f || screenH <= 0f) return@let

                        val imgW = image.width.toFloat().coerceAtLeast(1f)
                        val imgH = image.height.toFloat().coerceAtLeast(1f)
                        val imgAspect = imgW / imgH
                        val screenAspect = screenW / screenH

                        // Match exact video fit boundaries inside the screen
                        val (vidW, vidH) = if (screenAspect > imgAspect) {
                            (screenH * imgAspect) to screenH
                        } else {
                            screenW to (screenW / imgAspect)
                        }

                        // Project outward around the video
                        val targetW = vidW * ambientScale
                        val targetH = vidH * ambientScale

                        val left = (screenW - targetW) / 2f
                        val top = (screenH - targetH) / 2f

                        drawImage(
                            image = image,
                            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                            dstSize = IntSize(targetW.roundToInt(), targetH.roundToInt()),
                            colorFilter = ColorFilter.colorMatrix(colorMatrix),
                            filterQuality = FilterQuality.Medium,
                            alpha = (effectiveGlow * 1.15f).coerceIn(0.20f, 1.0f)
                        )
                    }
                }
        )

        // 2. Cinematic Edge Falloff: keeps full luminosity around video edges while softly feathering to deep black at screen borders
        val edgeDim = (0.75f - (spread.coerceIn(0.2f, 1.5f) - 0.5f) * 0.45f).coerceIn(0.15f, 0.85f)
        val midDim = (edgeDim * 0.45f).coerceIn(0.08f, 0.40f)

        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    if (w <= 0f || h <= 0f) return@drawBehind

                    if (h > w) {
                        // Portrait: Smooth exponential feathering at top and bottom edges
                        drawRect(
                            brush = Brush.verticalGradient(
                                0.00f to Color.Black.copy(alpha = edgeDim),
                                0.12f to Color.Black.copy(alpha = midDim),
                                0.25f to Color.Transparent,
                                0.75f to Color.Transparent,
                                0.88f to Color.Black.copy(alpha = midDim),
                                1.00f to Color.Black.copy(alpha = edgeDim)
                            )
                        )
                    } else {
                        // Landscape: Smooth exponential feathering at left and right edges
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0.00f to Color.Black.copy(alpha = edgeDim),
                                0.10f to Color.Black.copy(alpha = midDim),
                                0.22f to Color.Transparent,
                                0.78f to Color.Transparent,
                                0.90f to Color.Black.copy(alpha = midDim),
                                1.00f to Color.Black.copy(alpha = edgeDim)
                            )
                        )
                    }
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
