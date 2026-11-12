package com.example.ymediaplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoZoomMathTest {

    @Test
    fun pinchZoomAnchorsFocalPointUnderFingers() {
        val initial = VideoZoomMath.ZoomState(scale = 1.0f, panX = 0f, panY = 0f)
        // 1000x2000 viewport, center = (500, 1000). Pinch centered at (700, 800) to 2.0x.
        val result = VideoZoomMath.applyPinchStep(
            current = initial,
            zoomFactor = 2.0f,
            previousCentroidX = 700f,
            previousCentroidY = 800f,
            currentCentroidX = 700f,
            currentCentroidY = 800f,
            viewportWidth = 1000f,
            viewportHeight = 2000f
        )

        assertEquals(2.0f, result.scale, 0.001f)
        // Point (700 - 500, 800 - 1000) = (+200, -200) stays stationary -> pan = (-200, +200).
        assertEquals(-200f, result.panX, 0.01f)
        assertEquals(200f, result.panY, 0.01f)
    }

    @Test
    fun pinchAndPanStayClampedWithinViewportBounds() {
        val zoomed = VideoZoomMath.ZoomState(scale = 2.0f, panX = 0f, panY = 0f)
        // Max pan at 2.0x on 1000x2000 is +/-500 horizontally, +/-1000 vertically.
        val panned = VideoZoomMath.applyPanStep(
            current = zoomed,
            deltaX = 999f,
            deltaY = -1500f,
            viewportWidth = 1000f,
            viewportHeight = 2000f
        )

        assertEquals(500f, panned.panX, 0.01f)
        assertEquals(-1000f, panned.panY, 0.01f)
    }

    @Test
    fun releaseBelowSnapThresholdResetsToFitZeroPan() {
        val pinchedDown = VideoZoomMath.ZoomState(scale = 0.88f, panX = 40f, panY = -20f)
        val settledBelow = VideoZoomMath.settleAfterRelease(pinchedDown, 1080f, 1920f)
        assertEquals(1.0f, settledBelow.scale, 0.001f)
        assertEquals(0f, settledBelow.panX, 0.001f)
        assertEquals(0f, settledBelow.panY, 0.001f)

        val nearFit = VideoZoomMath.ZoomState(scale = 1.03f, panX = 12f, panY = -8f)
        val settledNear = VideoZoomMath.settleAfterRelease(nearFit, 1080f, 1920f)
        assertEquals(1.0f, settledNear.scale, 0.001f)
        assertEquals(0f, settledNear.panX, 0.001f)
        assertEquals(0f, settledNear.panY, 0.001f)
    }

    @Test
    fun textOnlySubtitlesNeverHaveBackgroundCardEvenIfBgPreferenceIsTrue() {
        val customDarkBox = (165 shl 24)
        assertEquals(0, VideoZoomMath.resolveSubtitleBackgroundColor(design = 0, isBgEnabled = true, customBgColor = customDarkBox))
        assertEquals(0, VideoZoomMath.resolveSubtitleBackgroundColor(design = 0, isBgEnabled = false, customBgColor = customDarkBox))
        assertEquals(0, VideoZoomMath.resolveSubtitleBackgroundColor(design = 3, isBgEnabled = true, customBgColor = customDarkBox))
        assertEquals(0, VideoZoomMath.resolveSubtitleBackgroundColor(design = 3, isBgEnabled = false, customBgColor = customDarkBox))
        assertEquals(0, VideoZoomMath.resolveSubtitleBackgroundColor(design = 2, isBgEnabled = false, customBgColor = customDarkBox))
        assertEquals(customDarkBox, VideoZoomMath.resolveSubtitleBackgroundColor(design = 2, isBgEnabled = true, customBgColor = customDarkBox))
        assertEquals(true, VideoZoomMath.resolveSubtitleBackgroundColor(design = 1, isBgEnabled = false, customBgColor = customDarkBox) != 0)
    }

    @Test
    fun formatsConciseVideoResolutionFpsAndSize() {
        assertEquals("8K", VideoZoomMath.formatVideoResolutionLabel(7680, 4320))
        assertEquals("4K", VideoZoomMath.formatVideoResolutionLabel(3840, 2160))
        assertEquals("2K", VideoZoomMath.formatVideoResolutionLabel(2560, 1440))
        assertEquals("1080p", VideoZoomMath.formatVideoResolutionLabel(1920, 1080))
        assertEquals("1080p", VideoZoomMath.formatVideoResolutionLabel(1920, 800))
        assertEquals("720p", VideoZoomMath.formatVideoResolutionLabel(1280, 720))
        assertEquals("480p", VideoZoomMath.formatVideoResolutionLabel(854, 480))

        assertEquals("24fps", VideoZoomMath.formatVideoFpsLabel(23.976f))
        assertEquals("30fps", VideoZoomMath.formatVideoFpsLabel(29.97f))
        assertEquals("60fps", VideoZoomMath.formatVideoFpsLabel(59.94f))
        assertEquals(null, VideoZoomMath.formatVideoFpsLabel(-1f))

        assertEquals("10MB", VideoZoomMath.formatCompactVideoSize(10L * 1024 * 1024))
        assertEquals("2GB", VideoZoomMath.formatCompactVideoSize(2L * 1024 * 1024 * 1024))
        assertEquals("1.5GB", VideoZoomMath.formatCompactVideoSize((1.5 * 1024 * 1024 * 1024).toLong()))

        assertEquals(
            "1080p · 24fps · 2GB",
            VideoZoomMath.formatVideoSpecLine(1920, 1080, 23.976f, 2L * 1024 * 1024 * 1024)
        )
        assertEquals(
            "720p · 60fps · 10MB",
            VideoZoomMath.formatVideoSpecLine(1280, 720, 59.94f, 10L * 1024 * 1024)
        )
        assertEquals("4K", VideoZoomMath.formatVideoSpecLine(3840, 2160, 0f, 0L))
    }

    @Test
    fun computesElapsedAndRemainingTimestampCorrectly() {
        val elapsed = VideoZoomMath.computeDisplayPositionMs(
            positionMs = 3_600_000L,
            durationMs = 7_200_000L,
            showRemaining = false
        )
        assertEquals(false to 3_600_000L, elapsed)

        val remaining = VideoZoomMath.computeDisplayPositionMs(
            positionMs = 5_525_000L,
            durationMs = 11_840_000L,
            showRemaining = true
        )
        assertEquals(true to 6_315_000L, remaining)

        val clampedAtEnd = VideoZoomMath.computeDisplayPositionMs(
            positionMs = 10_000L,
            durationMs = 8_000L,
            showRemaining = true
        )
        assertEquals(true to 0L, clampedAtEnd)
    }

    @Test
    fun ambientSmoothingSuppressesFlickerSpikesAndMicroShimmer() {
        val base = IntArray(4) { (0xFF shl 24) or (100 shl 16) or (100 shl 8) or 100 }
        // Tiny 2-unit compression shimmer stays completely suppressed by deadband
        val jittered = IntArray(4) { (0xFF shl 24) or (102 shl 16) or (98 shl 8) or 101 }
        val steadyOut = VideoAmbientMath.smoothAmbientPixels(base, jittered)
        assertEquals(base.toList(), steadyOut.toList())

        // Sudden 255 white flash is damped by blendAlpha + maxStepPerChannel so it cannot strobe
        val flash = IntArray(4) { (0xFF shl 24) or (255 shl 16) or (255 shl 8) or 255 }
        val damped = VideoAmbientMath.smoothAmbientPixels(base, flash)
        val r = (damped[0] ushr 16) and 0xFF
        assertEquals(128, r)
    }

    @Test
    fun videoEqualizerCyclesThroughFourMusicModes() {
        var mode = VideoSoundMode.NORMAL
        assertEquals("Balanced", mode.shortName)
        mode = mode.nextEqualizerMode()
        assertEquals("Bass Punch", mode.shortName)
        mode = mode.nextEqualizerMode()
        assertEquals("Vocal Boost", mode.shortName)
        mode = mode.nextEqualizerMode()
        assertEquals("3D Spatial", mode.shortName)
        mode = mode.nextEqualizerMode()
        assertEquals(VideoSoundMode.NORMAL, mode)
    }
}
