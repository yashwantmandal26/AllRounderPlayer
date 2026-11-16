package com.example.ymediaplayer.player

import kotlin.math.pow
import kotlin.math.roundToInt

internal data class HapticTuning(
    val strength: Float = 1f,
    val impactStrength: Float = 1f,
    val textureStrength: Float = 1f,
    val detail: Float = 1f,
    val dynamics: Float = 1f / 3f,
    val sensitivity: Float = 1f,
    val syncMs: Long = 0,
    val impacts: Boolean = true,
    val textures: Boolean = true
) {
    fun bounded() = copy(
        strength = finite(strength, 1f, 0.25f, 1.5f),
        impactStrength = finite(impactStrength, 1f, 0f, 1.5f),
        textureStrength = finite(textureStrength, 1f, 0f, 1.5f),
        detail = finite(detail, 1f, 0f, 1f),
        dynamics = finite(dynamics, 1f / 3f, 0f, 1f),
        sensitivity = finite(sensitivity, 1f, 0.5f, 1.5f),
        syncMs = syncMs.coerceIn(-250L, 250L)
    )

    fun render(signal: HapticSignal): HapticSignal? {
        val settings = bounded()
        if (signal.texture && !settings.textures || !signal.texture && !settings.impacts) return null
        val gain = settings.strength * if (signal.texture) settings.textureStrength else settings.impactStrength
        if (gain <= 0f) return null
        val requestedCurve = 0.75 + settings.dynamics * 0.75
        val curve = if (kotlin.math.abs(requestedCurve - 1.0) < 0.000001) 1.0 else requestedCurve
        val levels = IntArray(signal.amplitudes.size) { index ->
            val original = signal.amplitudes[index] / 255.0
            val smooth = if (signal.shape == HapticShape.GRAIN) {
                val first = (index - 1).coerceAtLeast(0)
                val last = (index + 1).coerceAtMost(signal.amplitudes.lastIndex)
                (first..last).sumOf { signal.amplitudes[it] / 255.0 } / (last - first + 1)
            } else original
            val level = smooth + (original - smooth) * settings.detail
            (255 * level.coerceIn(0.0, 1.0).pow(curve) * gain).roundToInt().coerceIn(0, 255)
        }
        return HapticSignal(signal.impact, signal.shape, signal.timingsMs, levels, signal.scalable)
    }

    companion object {
        private fun finite(value: Float, fallback: Float, low: Float, high: Float) =
            (if (value.isFinite()) value else fallback).coerceIn(low, high)

        fun preset(name: String): HapticTuning = when (name) {
            "Subtle" -> HapticTuning(strength = 0.65f, impactStrength = 0.8f, textureStrength = 0.7f, detail = 0.3f, dynamics = 0.15f, sensitivity = 0.85f)
            "Cinematic" -> HapticTuning(strength = 1.1f, impactStrength = 1.2f, textureStrength = 1.05f, detail = 1f, dynamics = 0.6f, sensitivity = 1.1f)
            else -> HapticTuning()
        }
    }
}
