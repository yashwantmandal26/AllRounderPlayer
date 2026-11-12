package com.example.ymediaplayer.ui

/**
 * Pure math helpers for gallery-style pinch-to-zoom, focal-point anchoring,
 * single-finger panning, clamping, and snap-to-fit settlement, plus subtitle
 * style background resolution.
 */
object VideoZoomMath {
    const val MIN_ZOOM_SCALE = 1.0f
    const val PINCH_MIN_SCALE = 0.85f
    const val MAX_ZOOM_SCALE = 5.0f
    const val SNAP_TO_FIT_THRESHOLD = 1.04f

    data class ZoomState(
        val scale: Float = MIN_ZOOM_SCALE,
        val panX: Float = 0f,
        val panY: Float = 0f
    )

    fun clampPan(
        scale: Float,
        panX: Float,
        panY: Float,
        viewportWidth: Float,
        viewportHeight: Float
    ): Pair<Float, Float> {
        if (scale <= MIN_ZOOM_SCALE || viewportWidth <= 0f || viewportHeight <= 0f) {
            return 0f to 0f
        }
        val maxPanX = ((scale - MIN_ZOOM_SCALE) * viewportWidth) / 2f
        val maxPanY = ((scale - MIN_ZOOM_SCALE) * viewportHeight) / 2f
        return panX.coerceIn(-maxPanX, maxPanX) to panY.coerceIn(-maxPanY, maxPanY)
    }

    /**
     * Zooms smoothly around the current two-finger centroid (just like a photo gallery)
     * while applying any simultaneous centroid pan translation and clamping pan to bounds.
     */
    fun applyPinchStep(
        current: ZoomState,
        zoomFactor: Float,
        previousCentroidX: Float,
        previousCentroidY: Float,
        currentCentroidX: Float,
        currentCentroidY: Float,
        viewportWidth: Float,
        viewportHeight: Float
    ): ZoomState {
        if (zoomFactor <= 0f || zoomFactor.isNaN()) return current
        val safeOldScale = current.scale.coerceAtLeast(0.01f)
        val newScale = (safeOldScale * zoomFactor).coerceIn(PINCH_MIN_SCALE, MAX_ZOOM_SCALE)
        if (newScale <= MIN_ZOOM_SCALE) {
            return ZoomState(scale = newScale, panX = 0f, panY = 0f)
        }

        val centerX = viewportWidth / 2f
        val centerY = viewportHeight / 2f
        val scaleRatio = newScale / safeOldScale

        val rawPanX = (currentCentroidX - centerX) +
            (current.panX - (previousCentroidX - centerX)) * scaleRatio
        val rawPanY = (currentCentroidY - centerY) +
            (current.panY - (previousCentroidY - centerY)) * scaleRatio

        val (clampedX, clampedY) = clampPan(newScale, rawPanX, rawPanY, viewportWidth, viewportHeight)
        return ZoomState(scale = newScale, panX = clampedX, panY = clampedY)
    }

    /**
     * Pans a zoomed video by [deltaX], [deltaY] while clamping inside valid bounds.
     */
    fun applyPanStep(
        current: ZoomState,
        deltaX: Float,
        deltaY: Float,
        viewportWidth: Float,
        viewportHeight: Float
    ): ZoomState {
        if (current.scale <= MIN_ZOOM_SCALE) {
            return ZoomState(scale = current.scale, panX = 0f, panY = 0f)
        }
        val (clampedX, clampedY) = clampPan(
            scale = current.scale,
            panX = current.panX + deltaX,
            panY = current.panY + deltaY,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight
        )
        return ZoomState(scale = current.scale, panX = clampedX, panY = clampedY)
    }

    /**
     * Snaps scale back to 1.0x fit when released at/below [SNAP_TO_FIT_THRESHOLD],
     * otherwise clamps pan offsets to valid bounds.
     */
    fun settleAfterRelease(
        current: ZoomState,
        viewportWidth: Float,
        viewportHeight: Float
    ): ZoomState {
        if (current.scale <= SNAP_TO_FIT_THRESHOLD) {
            return ZoomState(scale = MIN_ZOOM_SCALE, panX = 0f, panY = 0f)
        }
        val (clampedX, clampedY) = clampPan(
            scale = current.scale,
            panX = current.panX,
            panY = current.panY,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight
        )
        return ZoomState(scale = current.scale, panX = clampedX, panY = clampedY)
    }

    /**
     * Resolves the subtitle background ARGB color for a subtitle style design.
     * Designs 0 ("Classic Cinema") and 3 ("Dynamic Caption") are strictly text-only
     * (always TRANSPARENT / 0x00000000, never a background card).
     */
    fun resolveSubtitleBackgroundColor(
        design: Int,
        isBgEnabled: Boolean,
        customBgColor: Int
    ): Int = when (design) {
        0 -> 0x00000000 // Classic Cinema: never render a background card
        1 -> (215 shl 24) or (26 shl 16) or (27 shl 8) or 34 // Dark Box slate backing
        2 -> if (isBgEnabled) customBgColor else 0x00000000 // Golden Film: text+shadow unless bg enabled
        3 -> 0x00000000 // Dynamic Caption: never render a background card
        else -> if (isBgEnabled) customBgColor else 0x00000000
    }

    fun formatVideoResolutionLabel(width: Int, height: Int): String? {
        if (width <= 0 || height <= 0) return null
        val maxDim = maxOf(width, height)
        val minDim = minOf(width, height)
        return when {
            maxDim >= 7600 || minDim >= 4300 -> "8K"
            maxDim >= 3800 || minDim >= 2100 -> "4K"
            maxDim >= 2500 || minDim >= 1400 -> "2K"
            maxDim >= 1900 || minDim >= 1050 -> "1080p"
            maxDim >= 1200 || minDim >= 700 -> "720p"
            maxDim >= 800 || minDim >= 450 -> "480p"
            else -> "${minDim}p"
        }
    }

    fun formatVideoFpsLabel(frameRate: Float): String? {
        if (frameRate.isNaN() || frameRate <= 0f || frameRate > 240f) return null
        val rounded = kotlin.math.round(frameRate).toInt().coerceAtLeast(1)
        return "${rounded}fps"
    }

    fun formatCompactVideoSize(bytes: Long): String? {
        if (bytes <= 0L) return null
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> {
                val roundedTenth = kotlin.math.round(gb * 10.0) / 10.0
                if (roundedTenth % 1.0 == 0.0 || gb >= 10.0) {
                    "${kotlin.math.round(gb).toInt()}GB"
                } else {
                    String.format(java.util.Locale.US, "%.1fGB", roundedTenth)
                }
            }
            mb >= 1.0 -> {
                val roundedTenth = kotlin.math.round(mb * 10.0) / 10.0
                if (roundedTenth % 1.0 == 0.0 || mb >= 10.0) {
                    "${kotlin.math.round(mb).toInt()}MB"
                } else {
                    String.format(java.util.Locale.US, "%.1fMB", roundedTenth)
                }
            }
            kb >= 1.0 -> "${kotlin.math.round(kb).toInt()}KB"
            else -> "${bytes}B"
        }
    }

    fun formatVideoSpecLine(
        width: Int,
        height: Int,
        frameRate: Float,
        fileSizeBytes: Long
    ): String? {
        val parts = buildList {
            formatVideoResolutionLabel(width, height)?.let(::add)
            formatVideoFpsLabel(frameRate)?.let(::add)
            formatCompactVideoSize(fileSizeBytes)?.let(::add)
        }
        return if (parts.isEmpty()) null else parts.joinToString(" · ")
    }

    fun computeDisplayPositionMs(
        positionMs: Long,
        durationMs: Long,
        showRemaining: Boolean
    ): Pair<Boolean, Long> {
        val safePos = positionMs.coerceAtLeast(0L)
        if (!showRemaining) return false to safePos
        val safeDur = durationMs.coerceAtLeast(safePos)
        return true to (safeDur - safePos).coerceAtLeast(0L)
    }
}
