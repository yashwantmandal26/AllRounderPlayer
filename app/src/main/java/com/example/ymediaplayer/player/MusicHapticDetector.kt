package com.example.ymediaplayer.player

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.pow

/** Conservative low-bass onsets, never broadband/vocal textures. No audio is modified.
 * This is a spectral heuristic, not vocal stem separation: very low sung notes can overlap bass.
 */
internal class MusicHapticDetector : PcmHapticDetector {
    private var rate = 48000
    private var channels = 2
    private var highPass = Filter(35.0, true, rate, channels)
    private var lowPass = Filter(105.0, false, rate, channels)
    private var deepPass = Filter(75.0, false, rate, channels)
    private var voicePass = Filter(900.0, false, rate, channels)
    private var frames = 0
    private var power = 0.0
    private var bassPower = 0.0
    private var deepPower = 0.0
    private var voicePower = 0.0
    private var voiceBaseline = 0.0
    private var baseline = 0.0
    private var previousBass = 0.0
    private var windowStartUs = 0L
    private var warmup = 0
    private var lastBeatUs = Long.MIN_VALUE / 2
    private var threshold = 1.0
    private var confirmation = 0
    private var candidateBass = 0.0
    private var candidatePower = 0.0
    private var candidateDeep = 0.0
    private var candidateVoice = 0.0
    private var candidateVoiceBaseline = 0.0
    private var candidateBaseline = 0.0
    private var armed = true
    private var beatPeak = 0.0
    private var candidateTimeUs = 0L
    override var signal: HapticSignal? = null
        private set
    override var impactTimeUs = 0L
        private set
    override val needsTimestamp get() = frames == 0

    override fun configure(sampleRate: Int, channelCount: Int) {
        require(sampleRate > 0 && channelCount in 1..8)
        rate = sampleRate
        channels = channelCount
        highPass = Filter(35.0, true, rate, channels)
        lowPass = Filter(105.0, false, rate, channels)
        deepPass = Filter(75.0, false, rate, channels)
        voicePass = Filter(900.0, false, rate, channels)
        reset()
    }

    override fun reset() {
        highPass.reset(); lowPass.reset(); deepPass.reset(); voicePass.reset()
        frames = 0; power = 0.0; bassPower = 0.0
        baseline = 0.0; previousBass = 0.0; warmup = 0
        deepPower = 0.0; voicePower = 0.0; voiceBaseline = 0.0
        candidateDeep = 0.0; candidateVoice = 0.0; candidateVoiceBaseline = 0.0; candidateBaseline = 0.0
        armed = true; beatPeak = 0.0
        confirmation = 0; candidateBass = 0.0; candidatePower = 0.0
        lastBeatUs = Long.MIN_VALUE / 2; signal = null; impactTimeUs = 0L
    }

    override fun setSensitivity(value: Float) {
        threshold = 1.0 / (if (value.isFinite()) value else 1f).coerceIn(0.5f, 1.5f)
    }

    override fun sample(value: Double, channel: Int, timeUs: Long): CinemaImpact? {
        if (frames == 0 && channel == 0) windowStartUs = timeUs
        val x = if (value.isFinite()) value.coerceIn(-1.0, 1.0) else 0.0
        val audible = highPass.sample(x, channel)
        val band = lowPass.sample(audible, channel)
        val deep = deepPass.sample(audible, channel)
        val voice = voicePass.sample(audible, channel) - band
        power += x * x
        // Independent channel powers preserve bass in opposite-phase stereo recordings.
        bassPower += band * band
        deepPower += deep * deep
        voicePower += voice * voice
        if (channel != channels - 1 || ++frames < rate / 100) return null
        val energy = power / (frames * channels)
        val bass = bassPower / (frames * channels)
        val deepEnergy = deepPower / (frames * channels)
        val voiceEnergy = voicePower / (frames * channels)
        if (bass < baseline * 1.05 || bass < beatPeak * 0.40 || bass < 0.00006) armed = true
        // Detect a bass rise against its own floor, not the whole mix's loudness.
        // A vocal or snare onset rises more in the voice band and fails the confirmation below.
        val onset = warmup >= 12 && confirmation == 0 && armed &&
            windowStartUs - lastBeatUs >= 110_000 &&
            bass > 0.00012 * threshold && bass > baseline.coerceAtLeast(0.000015) * 1.65 &&
            bass > previousBass * 1.18 && bass / energy.coerceAtLeast(1e-9) > 0.045
        val oldBaseline = baseline
        val oldVoiceBaseline = voiceBaseline
        baseline += (if (bass > baseline) 0.045 else 0.10) * (bass - baseline)
        voiceBaseline += (if (voiceEnergy > voiceBaseline) 0.045 else 0.10) * (voiceEnergy - voiceBaseline)
        previousBass = bass
        frames = 0; power = 0.0; bassPower = 0.0; deepPower = 0.0; voicePower = 0.0; warmup++
        if (onset) {
            confirmation = 2
            candidateBass = 0.0; candidatePower = 0.0
            candidateDeep = 0.0; candidateVoice = 0.0
            candidateBaseline = oldBaseline; candidateVoiceBaseline = oldVoiceBaseline
            // Backdate the band-filter/window delay. Confirmation still happens on queued PCM.
            candidateTimeUs = windowStartUs - 10_000L
        }
        if (confirmation == 0) return null
        candidateBass += bass; candidatePower += energy
        candidateDeep += deepEnergy; candidateVoice += voiceEnergy
        if (--confirmation > 0) return null
        val bassNovelty = candidateBass / (2 * candidateBaseline).coerceAtLeast(0.00003)
        val voiceNovelty = candidateVoice / (2 * candidateVoiceBaseline).coerceAtLeast(0.00008)
        // Deep-band support and relative novelty reject vocal attacks, without requiring bass
        // to dominate a mastered song. Mixed vocals can stay much louder than the kick.
        val bassShare = candidateBass / candidatePower.coerceAtLeast(1e-9)
        val deepShare = candidateDeep / candidateBass.coerceAtLeast(1e-9)
        val cleanBassAttack = bassShare >= 0.42 && deepShare >= 0.28
        val bassUnderSteadyVoice = deepShare >= 0.28 && voiceNovelty < 1.4 && bassNovelty > voiceNovelty * 4
        if ((!cleanBassAttack && !bassUnderSteadyVoice && deepShare < 0.55) ||
            (bassShare < 0.30 && bassNovelty < voiceNovelty * 2.0) ||
            bassShare < 0.045 || bassShare > 1.4 || candidatePower < 0.00010 * threshold) return null
        armed = false
        beatPeak = candidateBass / 2
        lastBeatUs = candidateTimeUs
        impactTimeUs = candidateTimeUs
        // Compress the acoustic range into useful motor amplitudes; quiet beats stay tactile,
        // while strong beats retain a clearly stronger hit. No fixed-intensity song pulse.
        val level = (sqrt(candidateBass / 2) / 0.25).coerceIn(0.0, 1.0).pow(0.65)
        val peak = (48 + 170 * level).toInt()
        signal = HapticSignal(
            CinemaImpact.SOFT_THUMP, HapticShape.PUNCH,
            longArrayOf(4, 8, 10, 6),
            intArrayOf(peak, peak * 4 / 5, peak * 2 / 5, 0)
        )
        return CinemaImpact.SOFT_THUMP
    }

    /** Two biquads give a 35–105 Hz band with steep enough rejection of vocal harmonics. */
    private class Filter(cutoff: Double, highPass: Boolean, rate: Int, channels: Int) {
        private val z1 = DoubleArray(channels)
        private val z2 = DoubleArray(channels)
        private val omega = 2 * PI * cutoff / rate
        private val alpha = sin(omega) / sqrt(2.0)
        private val a0 = 1 + alpha
        private val b0 = (if (highPass) 1 + cos(omega) else 1 - cos(omega)) / (2 * a0)
        private val b1 = (if (highPass) -(1 + cos(omega)) else 1 - cos(omega)) / a0
        private val b2 = b0
        private val a1 = -2 * cos(omega) / a0
        private val a2 = (1 - alpha) / a0
        fun sample(x: Double, channel: Int): Double {
            val y = b0 * x + z1[channel]
            z1[channel] = b1 * x - a1 * y + z2[channel]
            z2[channel] = b2 * x - a2 * y
            return y
        }
        fun reset() { z1.fill(0.0); z2.fill(0.0) }
    }
}
