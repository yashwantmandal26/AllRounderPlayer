package com.example.ymediaplayer.player

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

internal enum class CinemaImpact(val durationMs: Long, val scale: Float) {
    TICK(12, 0.22f), TAP(20, 0.45f), SOFT_THUMP(24, 0.35f), THUMP(32, 0.65f), HEAVY_THUMP(40, 0.9f),
    RUMBLE(80, 0.3f), SWELL(80, 0.4f), TEXTURE(80, 0.3f)
}

internal interface PcmHapticDetector {
    val needsTimestamp: Boolean
    val signal: HapticSignal?
    val impactTimeUs: Long
    fun configure(sampleRate: Int, channelCount: Int)
    fun reset()
    fun setSensitivity(value: Float)
    fun sample(value: Double, channel: Int, timeUs: Long): CinemaImpact?
}

/** Separate bass and broadband onsets; a short confirmation window shapes each impact. */
internal class HapticDetector : PcmHapticDetector {
    private var rate = 48000
    private var channels = 2
    private var low = DoubleArray(2)
    private var mid = DoubleArray(2)
    private var dc = DoubleArray(2)
    private var alpha = 0.0
    private var midAlpha = 0.0
    private var dcAlpha = 0.0
    private var midPower = 0.0
    private var filteredEnergy = 0.0
    private var filteredBass = 0.0
    private var filteredMid = 0.0
    private var previousMid = 0.0
    private var midMotion = 0.0
    private var frames = 0
    private var power = 0.0
    private var bass = 0.0
    private var baseline = 0.0
    private var previous = 0.0
    private var bassBaseline = 0.0
    private var previousBass = 0.0
    private var windowStartUs = 0L
    private var candidateWindows = 0
    private var candidatePower = 0.0
    private var candidateBass = 0.0
    private var candidatePeak = 0.0
    private var samplePeak = 0.0
    private var candidateSamplePeak = 0.0
    private var candidateTimeUs = 0L
    private val textureLevels = FloatArray(16)
    private var textureIndex = 0
    private var textureWindows = 0
    private var sustainedWindows = 0
    private var textureEnvelope = 0.0
    override var signal: HapticSignal? = null
        private set
    override var impactTimeUs = 0L
        private set
    private var armed = true
    private var lastEventUs = Long.MIN_VALUE / 2
    private var warmup = 0
    private var thresholdScale = 1.0
    override val needsTimestamp get() = frames == 0
    override fun setSensitivity(value: Float) {
        thresholdScale = 1.0 / (if (value.isFinite()) value else 1f).coerceIn(0.5f, 1.5f)
    }

    override fun configure(sampleRate: Int, channelCount: Int) {
        rate = sampleRate
        channels = channelCount
        low = DoubleArray(channels)
        mid = DoubleArray(channels)
        dc = DoubleArray(channels)
        alpha = 1 - exp(-2 * PI * 150 / rate)
        midAlpha = 1 - exp(-2 * PI * 900 / rate)
        dcAlpha = 1 - exp(-2 * PI * 25 / rate)
        reset()
    }

    override fun reset() {
        low.fill(0.0)
        mid.fill(0.0)
        dc.fill(0.0)
        midPower = 0.0; filteredEnergy = 0.0; filteredBass = 0.0; filteredMid = 0.0
        previousMid = 0.0; midMotion = 0.0
        frames = 0; power = 0.0; bass = 0.0; baseline = 0.0; previous = 0.0
        bassBaseline = 0.0; previousBass = 0.0; candidateWindows = 0
        candidatePower = 0.0; candidateBass = 0.0; candidatePeak = 0.0
        samplePeak = 0.0; candidateSamplePeak = 0.0
        textureLevels.fill(0f); textureIndex = 0; textureWindows = 0; sustainedWindows = 0
        textureEnvelope = 0.0; signal = null
        armed = true; warmup = 0; lastEventUs = Long.MIN_VALUE / 2
    }

    // Sum channel powers rather than samples: opposite-phase channels cannot cancel.
    override fun sample(value: Double, channel: Int, timeUs: Long): CinemaImpact? {
        if (frames == 0 && channel == 0) windowStartUs = timeUs
        val x = if (value.isFinite()) value.coerceIn(-1.0, 1.0) else 0.0
        dc[channel] += dcAlpha * (x - dc[channel])
        val audible = x - dc[channel]
        low[channel] += alpha * (audible - low[channel])
        mid[channel] += midAlpha * (audible - mid[channel])
        val band = mid[channel] - low[channel]
        midPower += band * band
        power += x * x
        samplePeak = maxOf(samplePeak, x * x)
        bass += low[channel] * low[channel]
        if (channel != channels - 1 || ++frames < rate / 200) return null
        val energy = power / (frames * channels)
        val bassEnergy = bass / (frames * channels)
        val bassRatio = bass / power.coerceAtLeast(1e-9)
        val windowPeak = samplePeak
        samplePeak = 0.0
        val midEnergy = midPower / (frames * channels)
        midPower = 0.0
        filteredEnergy += 0.15 * (energy - filteredEnergy)
        filteredBass += 0.15 * (bassEnergy - filteredBass)
        filteredMid += 0.15 * (midEnergy - filteredMid)
        midMotion += 0.08 * ((kotlin.math.abs(midEnergy - previousMid) / midEnergy.coerceAtLeast(0.001)).coerceAtMost(2.0) - midMotion)
        previousMid = midEnergy
        val bassTexture = filteredBass / filteredEnergy.coerceAtLeast(1e-9) > 0.2 && filteredBass > 0.0006 * thresholdScale
        val grainTexture = filteredMid / filteredEnergy.coerceAtLeast(1e-9) > 0.25 && filteredMid > 0.0015 * thresholdScale && midMotion > 0.12
        val textured = (bassTexture || grainTexture) && filteredEnergy > 0.0011 * thresholdScale
        sustainedWindows = if (textured) (sustainedWindows + 1).coerceAtMost(40) else (sustainedWindows - 4).coerceAtLeast(0)
        val target = if (textured) (0.10 + 0.40 * (sqrt(filteredBass + 0.35 * filteredMid) / 0.22).coerceIn(0.0, 1.0).pow(0.75)) else 0.0
        textureEnvelope += (if (target > textureEnvelope) 0.35 else 0.18) * (target - textureEnvelope)
        textureLevels[textureIndex] = textureEnvelope.toFloat()
        textureIndex = (textureIndex + 1) % textureLevels.size
        textureWindows++
        val texture = if (warmup >= 20 && sustainedWindows >= 32 && energy > 0.0006 && textureWindows % 16 == 0) {
            HapticSignal.texture(FloatArray(16) { textureLevels[(textureIndex + it) % 16] }, grainTexture && !bassTexture)
        } else null
        val novelty = energy / baseline.coerceAtLeast(NOISE_POWER)
        val bassNovelty = bassEnergy / bassBaseline.coerceAtLeast(NOISE_POWER)
        if (energy < baseline * 1.4 || energy < NOISE_POWER ||
            (bassEnergy < bassBaseline * 1.3 && energy < baseline * 2)) armed = true
        val attack = energy > previous * 1.8
        val broadbandOnset = attack && novelty > 4.0 * sqrt(thresholdScale) && energy > MIN_IMPACT_POWER * thresholdScale
        // A kick/impact can rise above a busy soundtrack without quadrupling its total loudness.
        val bassOnset = bassRatio > 0.35 && bassNovelty > 3.0 * sqrt(thresholdScale) &&
            bassEnergy > previousBass * 1.6 && bassEnergy > 0.0015 * thresholdScale && energy > 0.0025 * thresholdScale
        val fire = candidateWindows == 0 && warmup >= 20 && armed &&
            (broadbandOnset || bassOnset) && windowStartUs - lastEventUs >= COOLDOWN_US
        baseline += (if (energy > baseline) 0.06 else 0.025) * (energy - baseline)
        bassBaseline += (if (bassEnergy > bassBaseline) 0.06 else 0.025) * (bassEnergy - bassBaseline)
        previous = energy
        previousBass = bassEnergy
        warmup++
        frames = 0; power = 0.0; bass = 0.0
        if (fire) {
            armed = false
            lastEventUs = windowStartUs
            candidateTimeUs = windowStartUs
            candidateWindows = 3
            candidatePower = 0.0; candidateBass = 0.0; candidatePeak = 0.0; candidateSamplePeak = 0.0
        }
        if (candidateWindows == 0) {
            if (texture != null) {
                signal = texture
                impactTimeUs = windowStartUs - (15L * (rate / 200) * 1_000_000 / rate)
            }
            return texture?.impact
        }
        candidatePower += energy
        candidateBass += bassEnergy
        candidatePeak = maxOf(candidatePeak, energy)
        candidateSamplePeak = maxOf(candidateSamplePeak, windowPeak)
        if (--candidateWindows > 0) return null
        val impact = when {
            candidateBass / candidatePower.coerceAtLeast(1e-9) > 0.4 -> when {
                // Window RMS, not full-scale sample peaks: mastered film audio rarely approaches 0 dBFS RMS.
                candidatePeak < 0.008 -> CinemaImpact.SOFT_THUMP
                candidatePeak >= 0.025 -> CinemaImpact.HEAVY_THUMP
                else -> CinemaImpact.THUMP
            }
            candidatePeak < 0.035 -> CinemaImpact.TICK
            else -> CinemaImpact.TAP
        }
        impactTimeUs = candidateTimeUs
        signal = HapticSignal.impact(impact, candidatePeak,
            candidateBass / candidatePower.coerceAtLeast(1e-9), candidateSamplePeak / candidatePeak.coerceAtLeast(1e-9))
        return impact
    }

    companion object {
        const val COOLDOWN_US = 180_000L
        private const val NOISE_POWER = 0.0001
        private const val MIN_IMPACT_POWER = 0.008
    }
}

/** Thread-safe bounded event mailbox. Generation invalidation also rejects in-flight producers. */
internal class HapticTimeline(private val impactCooldownMs: Long = 180L) {
    data class Event(val positionUs: Long, val signal: HapticSignal, val generation: Long)
    private val pending = java.util.PriorityQueue<Event>(compareBy<Event> { it.positionUs }.thenBy { it.signal.texture })
    private val played = java.util.ArrayDeque<Pair<Long, Double>>()
    private var lastImpactMs = Long.MIN_VALUE / 2
    private var impactEndMs = Long.MIN_VALUE / 2
    @Volatile var generation = 0L
        private set

    @Synchronized fun invalidate() { generation++; pending.clear() }
    @Synchronized fun hasPendingEvents(): Boolean = pending.isNotEmpty()

    /** True only when an empty mailbox becomes nonempty, so the worker can be woken once. */
    fun offer(positionUs: Long, impact: CinemaImpact, token: Long): Boolean =
        offer(positionUs, HapticSignal.fixed(impact), token)

    @Synchronized fun offer(positionUs: Long, signal: HapticSignal, token: Long): Boolean {
        if (token != generation || pending.size >= 32) return false
        val head = pending.peek()
        val wake = head == null || positionUs < head.positionUs
        pending.add(Event(positionUs, signal, token))
        return wake
    }

    @Synchronized fun nextDelayMs(positionUs: Long, speed: Float): Long {
        val next = pending.peek() ?: return 40L
        return ((next.positionUs - positionUs) / (1000.0 * speed.coerceAtLeast(0.1f)))
            .toLong().coerceIn(1L, 40L)
    }

    fun due(positionUs: Long, speed: Float, nowMs: Long): CinemaImpact? = dueSignal(positionUs, speed, nowMs)?.impact

    @Synchronized fun dueSignal(positionUs: Long, speed: Float, nowMs: Long, intensity: Float = 1f, tuning: HapticTuning? = null): HapticSignal? {
        while (played.isNotEmpty() && nowMs - played.first.first >= 5000) played.removeFirst()
        while (pending.isNotEmpty()) {
            val next = pending.peek() ?: break
            val lateMs = (positionUs - next.positionUs) / (1000.0 * speed.coerceAtLeast(0.1f))
            if (lateMs < 0) return null
            pending.remove()
            if (next.generation != generation) continue
            var signal = (if (tuning != null) tuning.render(next.signal) else next.signal.scaled(intensity.toDouble().coerceIn(0.0, 1.5)))
                ?.atSpeed(speed) ?: continue
            if (signal.texture) {
                if (nowMs < impactEndMs) continue
                signal = signal.trimStart(lateMs.toLong()) ?: continue
                // Submit already-known adjacent audio in one waveform to avoid restarting the motor
                // every 80 ms. Keep future impacts queued so they can interrupt on their own deadline.
                while (true) {
                    val following = pending.peek() ?: break
                    if (following.generation != generation) { pending.remove(); continue }
                    if (!following.signal.texture) break
                    val nextStartMs = (following.positionUs - positionUs) / (1000.0 * speed.coerceAtLeast(0.1f))
                    val nextSignal = (if (tuning != null) tuning.render(following.signal) else following.signal.scaled(intensity.toDouble().coerceIn(0.0, 1.5)))
                        ?.atSpeed(speed)
                    if (nextSignal == null) { pending.remove(); continue }
                    if (kotlin.math.abs(nextStartMs - signal.durationMs) > 2 ||
                        signal.durationMs + nextSignal.durationMs > 240) break
                    pending.remove()
                    signal = signal.appendTexture(nextSignal)
                }
            } else {
                if (lateMs > 60 || nowMs - lastImpactMs < impactCooldownMs) continue
            }
            val remaining = 400.0 - played.sumOf { it.second }
            if (remaining <= 0) continue
            val cost = signal.exposureMs
            if (cost > remaining) {
                if (!signal.scalable) continue
                val gain = sqrt(remaining / cost)
                if (gain < 0.15) continue
                signal = signal.scaled(gain)
            }
            played.add(nowMs to signal.exposureMs)
            if (!signal.texture) {
                lastImpactMs = nowMs
                impactEndMs = nowMs + signal.durationMs
            }
            return signal
        }
        return null
    }
}
