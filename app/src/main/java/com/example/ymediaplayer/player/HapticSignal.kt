package com.example.ymediaplayer.player

import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal enum class HapticShape { CLICK, PUNCH, GRAIN, RUMBLE, SWELL, MIXED }

/** A short, timestamped audio envelope. Arrays own their data; no decoder memory is retained. */
internal class HapticSignal(
    val impact: CinemaImpact,
    val shape: HapticShape,
    val timingsMs: LongArray,
    val amplitudes: IntArray,
    val scalable: Boolean = true
) {
    init {
        require(timingsMs.isNotEmpty() && timingsMs.size == amplitudes.size)
        require(timingsMs.all { it > 0 } && amplitudes.all { it in 0..255 })
    }
    val durationMs get() = timingsMs.sum()
    val texture get() = impact == CinemaImpact.RUMBLE || impact == CinemaImpact.SWELL || impact == CinemaImpact.TEXTURE
    // Full-amplitude-equivalent exposure, not a claim about electrical motor power.
    val exposureMs get() = timingsMs.indices.sumOf { timingsMs[it] * (amplitudes[it] / 255.0).pow(2) }

    fun scaled(gain: Double) = HapticSignal(impact, shape, timingsMs,
        IntArray(amplitudes.size) { (amplitudes[it] * gain).toInt().coerceIn(0, 255) }, scalable)

    fun appendTexture(next: HapticSignal): HapticSignal {
        require(texture && next.texture)
        return HapticSignal(if (impact == next.impact) impact else CinemaImpact.TEXTURE,
            if (shape == next.shape) shape else HapticShape.MIXED,
            timingsMs + next.timingsMs, amplitudes + next.amplitudes)
    }

    fun atSpeed(speed: Float): HapticSignal {
        val safeSpeed = speed.coerceIn(0.1f, 8f)
        val times = ArrayList<Long>()
        val levels = ArrayList<Int>()
        var mediaMs = 0L
        var wallMs = 0L
        timingsMs.indices.forEach { i ->
            mediaMs += timingsMs[i]
            val end = (mediaMs / safeSpeed.toDouble()).roundToInt().toLong()
            if (end > wallMs) {
                times.add(end - wallMs); levels.add(amplitudes[i]); wallMs = end
            } else if (levels.isNotEmpty()) levels[levels.lastIndex] = amplitudes[i]
        }
        if (times.isEmpty()) return HapticSignal(impact, shape, longArrayOf(1), intArrayOf(amplitudes.last()), scalable)
        return HapticSignal(impact, shape, times.toLongArray(), levels.toIntArray(), scalable)
    }

    /** Remove already-audible texture samples, so a late packet cannot replay an old envelope. */
    fun trimStart(elapsedMs: Long): HapticSignal? {
        if (elapsedMs <= 0) return this
        var remaining = elapsedMs
        var first = 0
        while (first < timingsMs.size && remaining >= timingsMs[first]) remaining -= timingsMs[first++]
        if (first == timingsMs.size) return null
        val times = timingsMs.copyOfRange(first, timingsMs.size)
        times[0] -= remaining
        return HapticSignal(impact, shape, times, amplitudes.copyOfRange(first, amplitudes.size), scalable)
    }

    companion object {
        /** Conservative convenience for effects with no measured envelope (also used by clock probes). */
        fun fixed(impact: CinemaImpact) = HapticSignal(impact, HapticShape.CLICK,
            longArrayOf(impact.durationMs), intArrayOf(255), scalable = false)

        fun impact(impact: CinemaImpact, power: Double, bassRatio: Double, crest: Double): HapticSignal {
            val strength = (0.16 + 0.78 * (sqrt(power.coerceAtLeast(0.0)) / 0.28)
                .coerceIn(0.0, 1.0).pow(0.65)).coerceIn(0.16, 0.94)
            val shape = when {
                bassRatio > 0.4 -> HapticShape.PUNCH
                crest > 3.5 -> HapticShape.GRAIN
                else -> HapticShape.CLICK
            }
            val times: LongArray
            val levels: DoubleArray
            when (shape) {
                HapticShape.PUNCH -> {
                    times = longArrayOf(4, 6, 10, 10, 10, 8)
                    levels = doubleArrayOf(0.12, 0.65, 1.0, 0.72, 0.35, 0.0)
                }
                HapticShape.GRAIN -> {
                    times = longArrayOf(4, 5, 4, 6, 5, 6)
                    levels = doubleArrayOf(0.85, 0.16, 0.68, 0.08, 0.38, 0.0)
                }
                else -> {
                    times = longArrayOf(3, 5, 5, 5)
                    levels = doubleArrayOf(1.0, 0.65, 0.22, 0.0)
                }
            }
            return HapticSignal(impact, shape, times,
                IntArray(levels.size) { (255 * strength * levels[it]).roundToInt() })
        }

        fun texture(levels: FloatArray, grain: Boolean = false): HapticSignal {
            val rise = levels.last() - levels.first()
            val roughness = (1 until levels.size).sumOf { kotlin.math.abs(levels[it] - levels[it - 1]).toDouble() } / levels.size
            val shape = when {
                rise > 0.045f -> HapticShape.SWELL
                grain || roughness > 0.022 -> HapticShape.GRAIN
                else -> HapticShape.RUMBLE
            }
            val impact = when (shape) {
                HapticShape.SWELL -> CinemaImpact.SWELL
                HapticShape.GRAIN -> CinemaImpact.TEXTURE
                else -> CinemaImpact.RUMBLE
            }
            return HapticSignal(impact, shape, LongArray(levels.size) { 5 }, IntArray(levels.size) { i ->
                // Grain is amplitude modulation, not unsupported hardware frequency control.
                val grain = if (shape == HapticShape.GRAIN && i % 3 == 1) 0.45 else 1.0
                (levels[i] * 255 * grain).roundToInt().coerceIn(0, 140)
            })
        }
    }
}
