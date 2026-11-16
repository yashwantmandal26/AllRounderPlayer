@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ymediaplayer.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.*

internal enum class MusicProcessingMode { ORIGINAL, BASS_PUNCH, VOCAL_BOOST, LIVE_STAGE }

/** Speaker-audible tone shaping in the PCM path, independent of OEM AudioFX support.
 * Original copies bytes unchanged. Live changes crossfade for 20 ms, without flushing playback.
 * A channel-linked peak limiter leaves headroom and preserves the stereo balance.
 */
internal class MusicSoundProcessor : BaseAudioProcessor() {
    @Volatile var mode = MusicProcessingMode.ORIGINAL
    private var bank: ToneBank? = null
    private var previous: ToneBank? = null
    private var transitionFrames = 0
    private var transitionLength = 1
    private var limiter = 1.0
    private var release = 0.0
    private var samples = DoubleArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT &&
            inputAudioFormat.channelCount in 1..8 && inputAudioFormat.sampleRate > 0) inputAudioFormat
        else AudioProcessor.AudioFormat.NOT_SET
    }

    override fun onFlush() {
        bank = null; previous = null; transitionFrames = 0; limiter = 1.0
        samples = DoubleArray(inputAudioFormat.channelCount.coerceAtLeast(0))
        transitionLength = (inputAudioFormat.sampleRate / 50).coerceAtLeast(1)
        release = exp(-1.0 / (inputAudioFormat.sampleRate.coerceAtLeast(1) * 0.08))
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val requested = mode
        val channels = inputAudioFormat.channelCount
        if (bank?.mode != requested) {
            previous = bank
            bank = ToneBank(requested, inputAudioFormat.sampleRate, channels)
            transitionFrames = if (previous == null) 0 else transitionLength
        }
        val current = requireNotNull(bank)
        val output = replaceOutputBuffer(inputBuffer.remaining())
        if (requested == MusicProcessingMode.ORIGINAL && transitionFrames == 0) {
            limiter = 1.0
            output.put(inputBuffer)
        } else {
            while (inputBuffer.remaining() >= channels * 2) {
                val mix = if (transitionFrames > 0) 1.0 - transitionFrames.toDouble() / transitionLength else 1.0
                var peak = 0.0
                for (channel in 0 until channels) {
                    val x = inputBuffer.short / 32768.0
                    val processed = current.sample(x, channel)
                    val y = if (transitionFrames > 0) {
                        val old = previous?.sample(x, channel) ?: x
                        old + mix * (processed - old)
                    } else processed
                    samples[channel] = y
                    peak = maxOf(peak, abs(y))
                }
                val target = if (peak > 0.97) 0.97 / peak else 1.0
                limiter = minOf(target, release * limiter + (1 - release))
                for (channel in 0 until channels) {
                    // Original becomes bit-transparent after the crossfade, even within this buffer.
                    val gain = if (requested == MusicProcessingMode.ORIGINAL) {
                        // Restore unity during the fade so a previously limited mode cannot jump at its end.
                        val restored = limiter + (1 - limiter) * mix
                        if (peak > 1.0) minOf(restored, 1.0 / peak) else restored
                    } else limiter
                    output.putShort((samples[channel] * gain * 32768).roundToInt().coerceIn(-32768, 32767).toShort())
                }
                if (transitionFrames > 0 && --transitionFrames == 0) previous = null
            }
            output.put(inputBuffer)
        }
        output.flip()
    }

    private class ToneBank(val mode: MusicProcessingMode, rate: Int, channels: Int) {
        private val bands = when (mode) {
            MusicProcessingMode.BASS_PUNCH -> arrayOf(
                Biquad.peak(220.0, 7.0, 0.85, rate, channels),
                Biquad.peak(700.0, -2.5, 0.9, rate, channels),
                Biquad.peak(3000.0, 1.5, 0.8, rate, channels))
            MusicProcessingMode.VOCAL_BOOST -> arrayOf(
                Biquad.peak(220.0, -4.0, 0.8, rate, channels),
                Biquad.peak(1800.0, 6.0, 0.8, rate, channels),
                Biquad.peak(4000.0, 2.5, 0.9, rate, channels))
            MusicProcessingMode.LIVE_STAGE -> arrayOf(
                Biquad.peak(220.0, 2.5, 0.8, rate, channels),
                Biquad.peak(900.0, -3.0, 0.85, rate, channels),
                Biquad.peak(3200.0, 4.5, 0.8, rate, channels),
                Biquad.peak(7500.0, 2.0, 0.8, rate, channels))
            MusicProcessingMode.ORIGINAL -> emptyArray()
        }
        private val bass = if (mode == MusicProcessingMode.BASS_PUNCH) Biquad.lowPass(110.0, rate, channels) else null
        private val rectifiedDc = DoubleArray(channels)
        private val dcAlpha = 1 - exp(-2 * PI * 20 / rate)
        private val preamp = when (mode) {
            MusicProcessingMode.ORIGINAL -> 1.0
            MusicProcessingMode.BASS_PUNCH -> 0.72
            MusicProcessingMode.VOCAL_BOOST -> 0.80
            MusicProcessingMode.LIVE_STAGE -> 0.78
        }
        fun sample(x: Double, channel: Int): Double {
            var y = x
            for (filter in bands) y = filter.sample(y, channel)
            bass?.let {
                val low = it.sample(x, channel)
                val rectified = abs(low)
                rectifiedDc[channel] += dcAlpha * (rectified - rectifiedDc[channel])
                // Even and odd upper harmonics make low notes perceptible on small speakers.
                y += 0.65 * (rectified - rectifiedDc[channel]) + 1.8 * low * low * low
            }
            return y * preamp
        }
    }

    private class Biquad(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double, channels: Int) {
        private val b0 = b0 / a0
        private val b1 = b1 / a0
        private val b2 = b2 / a0
        private val a1 = a1 / a0
        private val a2 = a2 / a0
        private val z1 = DoubleArray(channels)
        private val z2 = DoubleArray(channels)
        fun sample(x: Double, channel: Int): Double {
            val y = b0 * x + z1[channel]
            z1[channel] = b1 * x - a1 * y + z2[channel]
            z2[channel] = b2 * x - a2 * y
            return y
        }
        companion object {
            fun peak(frequency: Double, db: Double, q: Double, rate: Int, channels: Int): Biquad {
                val w = 2 * PI * frequency.coerceAtMost(rate * 0.42) / rate
                val a = 10.0.pow(db / 40)
                val alpha = sin(w) / (2 * q)
                return Biquad(1 + alpha * a, -2 * cos(w), 1 - alpha * a,
                    1 + alpha / a, -2 * cos(w), 1 - alpha / a, channels)
            }
            fun lowPass(frequency: Double, rate: Int, channels: Int): Biquad {
                val w = 2 * PI * frequency / rate
                val alpha = sin(w) / sqrt(2.0)
                return Biquad((1 - cos(w)) / 2, 1 - cos(w), (1 - cos(w)) / 2,
                    1 + alpha, -2 * cos(w), 1 - alpha, channels)
            }
        }
    }
}
