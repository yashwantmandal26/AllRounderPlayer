package com.example.ymediaplayer.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

// Timed sequentially so each crossfade finishes cleanly before the next capture starts (zero overlap/dip flicker)
private const val ACTIVE_FADE_MS = 460
private const val ACTIVE_REST_MS = 120L
private const val BATTERY_SAVER_FADE_MS = 700
private const val BATTERY_SAVER_REST_MS = 400L

/**
 * Pure temporal + spatial smoothing helpers for ambient backlight sampling so rapid flashes,
 * compression jitter, and single-frame decoder glitches never cause strobe/flicker.
 */
internal object VideoAmbientMath {

    /**
     * Exponential moving average per RGB channel with deadband suppression (stops minor shimmer
     * on steady scenes) and per-step channel delta clamping (prevents sudden strobing flashes).
     */
    fun smoothAmbientPixels(
        previous: IntArray?,
        sampled: IntArray,
        blendAlpha: Float = 0.30f,
        deadband: Int = 3,
        maxStepPerChannel: Int = 28
    ): IntArray {
        val count = sampled.size
        if (count == 0) return IntArray(0)
        if (previous == null || previous.size != count) {
            return IntArray(count) { idx -> (sampled[idx] and 0x00FFFFFF) or (0xFF shl 24) }
        }
        val out = IntArray(count)
        val alpha = blendAlpha.coerceIn(0.05f, 1.0f)
        for (i in 0 until count) {
            val prev = previous[i]
            val curr = sampled[i]

            val pr = (prev ushr 16) and 0xFF
            val pg = (prev ushr 8) and 0xFF
            val pb = prev and 0xFF

            val cr = (curr ushr 16) and 0xFF
            val cg = (curr ushr 8) and 0xFF
            val cb = curr and 0xFF

            val dr = cr - pr
            val dg = cg - pg
            val db = cb - pb

            val nr = if (abs(dr) <= deadband) pr else {
                val delta = (dr * alpha).roundToInt().coerceIn(-maxStepPerChannel, maxStepPerChannel)
                val stepped = if (delta == 0) (if (dr > 0) 1 else -1) else delta
                (pr + stepped).coerceIn(0, 255)
            }
            val ng = if (abs(dg) <= deadband) pg else {
                val delta = (dg * alpha).roundToInt().coerceIn(-maxStepPerChannel, maxStepPerChannel)
                val stepped = if (delta == 0) (if (dg > 0) 1 else -1) else delta
                (pg + stepped).coerceIn(0, 255)
            }
            val nb = if (abs(db) <= deadband) pb else {
                val delta = (db * alpha).roundToInt().coerceIn(-maxStepPerChannel, maxStepPerChannel)
                val stepped = if (delta == 0) (if (db > 0) 1 else -1) else delta
                (pb + stepped).coerceIn(0, 255)
            }

            out[i] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
        }
        return out
    }

    /**
     * Fast 2-pass separable [1, 2, 1]/4 blur on a tiny sampled frame so colors disperse organically
     * without heavy per-frame RenderEffect blur thrashing on the GPU.
     */
    fun blurSmallArgbGrid(pixels: IntArray, width: Int, height: Int, passes: Int = 2): IntArray {
        if (width <= 2 || height <= 2 || pixels.size != width * height) return pixels.copyOf()
        var src = pixels.copyOf()
        var dst = IntArray(pixels.size)

        repeat(passes.coerceIn(1, 4)) {
            // Horizontal pass
            for (y in 0 until height) {
                val row = y * width
                for (x in 0 until width) {
                    val pL = src[row + (x - 1).coerceAtLeast(0)]
                    val pC = src[row + x]
                    val pR = src[row + (x + 1).coerceAtMost(width - 1)]

                    val r = (((pL ushr 16) and 0xFF) + (((pC ushr 16) and 0xFF) shl 1) + ((pR ushr 16) and 0xFF)) ushr 2
                    val g = (((pL ushr 8) and 0xFF) + (((pC ushr 8) and 0xFF) shl 1) + ((pR ushr 8) and 0xFF)) ushr 2
                    val b = ((pL and 0xFF) + ((pC and 0xFF) shl 1) + (pR and 0xFF)) ushr 2
                    dst[row + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            // Vertical pass
            for (y in 0 until height) {
                val rU = ((y - 1).coerceAtLeast(0)) * width
                val rC = y * width
                val rD = ((y + 1).coerceAtMost(height - 1)) * width
                for (x in 0 until width) {
                    val pU = dst[rU + x]
                    val pC = dst[rC + x]
                    val pD = dst[rD + x]

                    val r = (((pU ushr 16) and 0xFF) + (((pC ushr 16) and 0xFF) shl 1) + ((pD ushr 16) and 0xFF)) ushr 2
                    val g = (((pU ushr 8) and 0xFF) + (((pC ushr 8) and 0xFF) shl 1) + ((pD ushr 8) and 0xFF)) ushr 2
                    val b = ((pU and 0xFF) + ((pC and 0xFF) shl 1) + (pD and 0xFF)) ushr 2
                    src[rC + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }
        return src
    }
}

/**
 * Flicker-free, cinematic Ambilight-style ambient backlighting.
 *
 * 1. Temporal EMA smoothing + step clamping on low-res grid eliminates strobe/action flicker.
 * 2. Offscreen composited crossfade (prev @ 1.0f underneath, curr @ cf on top) keeps total
 *    luminosity 100% constant during transitions — zero dip to black at midpoint.
 * 3. Sequential capture + fade cycle guarantees every fade finishes to 1.0f cleanly before the next sample.
 * 4. CPU pre-blur on tiny 36x20 grid paired with lightweight GPU blur removes RenderEffect stutter.
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
    val currentFrame = remember(playerView, mediaUrl) { mutableStateOf<ImageBitmap?>(null) }
    val previousFrame = remember(playerView, mediaUrl) { mutableStateOf<ImageBitmap?>(null) }
    val crossfadeAlpha = remember(playerView, mediaUrl) { Animatable(1f) }

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
            var smoothedPixels: IntArray? = null
            var sampledPixels = IntArray(0)

            while (isActive) {
                val player = playerView.player
                val playbackState = player?.playbackState ?: Player.STATE_IDLE
                val position = player?.currentPosition ?: 0L

                if (playbackState == Player.STATE_IDLE ||
                    (playbackState == Player.STATE_BUFFERING && currentFrame.value != null)
                ) {
                    delay(250L)
                    continue
                }
                if (!playing.value && currentFrame.value != null && position == lastPosition) {
                    delay(350L)
                    continue
                }
                withFrameNanos { }

                val videoSize = player?.videoSize
                val vw = videoSize?.width?.takeIf { it > 0 }
                    ?: (playerView.videoSurfaceView as? SurfaceView)?.width?.takeIf { it > 0 }
                    ?: 16
                val vh = videoSize?.height?.takeIf { it > 0 }
                    ?: (playerView.videoSurfaceView as? SurfaceView)?.height?.takeIf { it > 0 }
                    ?: 9

                val maxDim = 36
                val (sampleW, sampleH) = if (vw >= vh) {
                    maxDim to max(12, (maxDim * (vh.toFloat() / vw)).roundToInt())
                } else {
                    max(12, (maxDim * (vw.toFloat() / vh)).roundToInt()) to maxDim
                }

                if (reusableBitmap == null || reusableBitmap.width != sampleW || reusableBitmap.height != sampleH) {
                    reusableBitmap = Bitmap.createBitmap(sampleW, sampleH, Bitmap.Config.ARGB_8888)
                    smoothedPixels = null
                    sampledPixels = IntArray(sampleW * sampleH)
                }

                val target = reusableBitmap ?: break
                val captured = when (val surface = playerView.videoSurfaceView) {
                    is TextureView -> if (surface.isAvailable && surface.width > 0 && surface.height > 0) {
                        try {
                            surface.getBitmap(target)
                            true
                        } catch (_: Throwable) {
                            false
                        }
                    } else false
                    is SurfaceView -> if (surface.width > 0 && surface.height > 0) {
                        captureSurfaceFrame(surface, target, bgHandler)
                    } else false
                    else -> false
                }

                if (isActive && captured) {
                    val prevSmoothed = smoothedPixels
                    val (updatedSmoothed, nextImage) = withContext(Dispatchers.Default) {
                        target.getPixels(sampledPixels, 0, sampleW, 0, 0, sampleW, sampleH)
                        val smoothed = VideoAmbientMath.smoothAmbientPixels(
                            previous = prevSmoothed,
                            sampled = sampledPixels
                        )
                        val blurredPixels = VideoAmbientMath.blurSmallArgbGrid(
                            pixels = smoothed,
                            width = sampleW,
                            height = sampleH,
                            passes = 2
                        )
                        val frameBitmap = Bitmap.createBitmap(sampleW, sampleH, Bitmap.Config.ARGB_8888)
                        frameBitmap.setPixels(blurredPixels, 0, sampleW, 0, 0, sampleW, sampleH)
                        smoothed to frameBitmap.asImageBitmap()
                    }
                    smoothedPixels = updatedSmoothed

                    if (currentFrame.value == null) {
                        currentFrame.value = nextImage
                        crossfadeAlpha.snapTo(1f)
                    } else {
                        val oldFrame = currentFrame.value
                        previousFrame.value = oldFrame
                        crossfadeAlpha.snapTo(0f)
                        currentFrame.value = nextImage

                        val fadeDuration = if (saveBattery.value) BATTERY_SAVER_FADE_MS else ACTIVE_FADE_MS
                        crossfadeAlpha.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = fadeDuration, easing = LinearEasing)
                        )
                    }
                    lastPosition = position
                }

                val restMs = if (saveBattery.value) BATTERY_SAVER_REST_MS else ACTIVE_REST_MS
                delay(restMs)
            }
        }
    }

    val colorMatrix = remember(saturation) {
        ColorMatrix().apply {
            setToSaturation((1.85f * saturation.coerceIn(0.4f, 2.5f)).coerceIn(0.2f, 4.0f))
        }
    }
    val ambientColorFilter = remember(colorMatrix) { ColorFilter.colorMatrix(colorMatrix) }

    val ambientScale = 1.30f + (spread.coerceIn(0.1f, 1.5f) * 0.70f)
    val blurRadius = (24 + spread.coerceIn(0.1f, 1.5f) * 26).dp
    val effectiveGlow = glowStrength.coerceIn(0.2f, 2.0f)
    val layerAlpha = (effectiveGlow * 0.92f).coerceIn(0.18f, 1.0f)

    Box(Modifier.fillMaxSize()) {
        // 1. Flicker-free Offscreen-Composited Ambient Glow Layer
        // prev is always drawn opaque (1.0f) underneath and curr fades 0 -> 1 over it so luminosity never dips through black.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = layerAlpha
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .blur(blurRadius)
                .drawBehind {
                    val cf = crossfadeAlpha.value.coerceIn(0f, 1f)
                    val prev = previousFrame.value
                    val curr = currentFrame.value

                    fun DrawScope.renderAmbientImage(image: ImageBitmap, alphaRatio: Float) {
                        if (alphaRatio <= 0.005f) return
                        val screenW = size.width
                        val screenH = size.height
                        if (screenW <= 0f || screenH <= 0f) return

                        val imgW = image.width.toFloat().coerceAtLeast(1f)
                        val imgH = image.height.toFloat().coerceAtLeast(1f)
                        val imgAspect = imgW / imgH
                        val screenAspect = screenW / screenH

                        val (vidW, vidH) = if (screenAspect > imgAspect) {
                            (screenH * imgAspect) to screenH
                        } else {
                            screenW to (screenW / imgAspect)
                        }

                        val targetW = vidW * ambientScale
                        val targetH = vidH * ambientScale

                        val left = (screenW - targetW) / 2f
                        val top = (screenH - targetH) / 2f

                        drawImage(
                            image = image,
                            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                            dstSize = IntSize(targetW.roundToInt(), targetH.roundToInt()),
                            colorFilter = ambientColorFilter,
                            filterQuality = FilterQuality.Medium,
                            alpha = alphaRatio.coerceIn(0f, 1f)
                        )
                    }

                    if (prev != null && cf < 0.995f) {
                        renderAmbientImage(prev, 1f)
                    }
                    if (curr != null) {
                        renderAmbientImage(curr, if (prev != null) cf else 1f)
                    }
                }
        )

        // 2. Cinematic Edge Falloff
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
