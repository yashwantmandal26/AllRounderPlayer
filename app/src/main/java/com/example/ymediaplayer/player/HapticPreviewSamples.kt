package com.example.ymediaplayer.player

import kotlin.math.PI
import kotlin.math.sin

internal enum class HapticPreviewType(val label: String) {
    MIX("Mix"), IMPACT("Impacts"), RUMBLE("Rumble"), SWELL("Swell"), GRAIN("Grain"), TOUCH("Touch")
}

internal object HapticPreviewSamples {
    fun create(type: HapticPreviewType, value: HapticTuning, touchStrength: Float = 1f, compact: Boolean = false): HapticSignal {
        val tuning = value.bounded()
        if (type == HapticPreviewType.TOUCH) return HapticSignal(CinemaImpact.TAP, HapticShape.CLICK,
            longArrayOf(26), intArrayOf((180 * touchStrength).toInt().coerceIn(1, 255)))
        val span = if (compact) 240 else when (type) {
            HapticPreviewType.MIX -> 1040
            HapticPreviewType.IMPACT -> 600
            else -> 640
        }
        val levels = IntArray(span / 5)
        fun add(startMs: Int, raw: HapticSignal) {
            val rendered = tuning.render(raw) ?: return
            var elapsed = 0L
            for (i in rendered.timingsMs.indices) {
                val end = elapsed + rendered.timingsMs[i]
                for (frame in levels.indices) {
                    val time = frame * 5L - startMs
                    if (time >= elapsed && time < end) levels[frame] = maxOf(levels[frame], rendered.amplitudes[i])
                }
                elapsed = end
            }
        }
        fun impact(at: Int, power: Double) {
            if (power >= 0.0025 / tuning.sensitivity) add(at,
                HapticSignal.impact(CinemaImpact.THUMP, power, 0.8, 2.0))
        }
        fun texture(at: Int, duration: Int, shape: HapticShape) {
            val count = duration / 5
            val amps = IntArray(count) { i ->
                val phase = i.toDouble() / (count - 1).coerceAtLeast(1)
                val level = when (shape) {
                    HapticShape.SWELL -> 0.06 + 0.38 * sin(PI * phase)
                    HapticShape.GRAIN -> if (i % 3 == 1) 0.07 else 0.32 + 0.04 * sin(i.toDouble())
                    else -> 0.24 + 0.045 * sin(2 * PI * phase)
                }
                (level * 255).toInt().coerceIn(0, 255)
            }
            add(at, HapticSignal(if (shape == HapticShape.SWELL) CinemaImpact.SWELL else CinemaImpact.RUMBLE,
                shape, LongArray(count) { 5 }, amps))
        }
        when (type) {
            HapticPreviewType.IMPACT -> {
                impact(0, 0.002); impact(if (compact) 120 else 200, 0.01)
                if (!compact) impact(400, 0.05)
            }
            HapticPreviewType.RUMBLE -> texture(0, span, HapticShape.RUMBLE)
            HapticPreviewType.SWELL -> texture(0, span, HapticShape.SWELL)
            HapticPreviewType.GRAIN -> texture(0, span, HapticShape.GRAIN)
            else -> if (compact) {
                impact(0, 0.02); texture(80, 160, HapticShape.RUMBLE)
            } else {
                impact(0, 0.02); texture(160, 240, HapticShape.RUMBLE)
                texture(440, 240, HapticShape.SWELL); texture(720, 240, HapticShape.GRAIN)
                impact(960, 0.05)
            }
        }
        return HapticSignal(CinemaImpact.TEXTURE,
            when (type) { HapticPreviewType.RUMBLE -> HapticShape.RUMBLE; HapticPreviewType.SWELL -> HapticShape.SWELL
                HapticPreviewType.GRAIN -> HapticShape.GRAIN; else -> HapticShape.MIXED },
            LongArray(levels.size) { 5 }, levels)
    }
}
