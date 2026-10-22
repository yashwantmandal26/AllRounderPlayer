@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ymediaplayer.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/** Linked-channel gain control keeps the stereo image intact and never boosts silence. */
internal class VideoDynamics {
    private var power = 0.0
    private var gain = 1.0
    private var envelopeAttack = 0.0
    private var envelopeRelease = 0.0
    private var gainAttack = 0.0
    private var gainRelease = 0.0

    init { configure(48000) }

    fun configure(rate: Int) {
        val sampleRate = rate.coerceAtLeast(1)
        envelopeAttack = exp(-1.0 / (sampleRate * 0.015))
        envelopeRelease = exp(-1.0 / (sampleRate * 0.4))
        gainAttack = exp(-1.0 / (sampleRate * 0.003))
        gainRelease = exp(-1.0 / (sampleRate * 0.6))
        reset()
    }

    fun reset() { power = 0.0; gain = 1.0 }

    fun frameGain(peak: Float, night: Float, leveling: Boolean): Float {
        val inputPower = peak * peak.toDouble()
        val envelope = if (inputPower > power) envelopeAttack else envelopeRelease
        power = envelope * power + (1.0 - envelope) * inputPower
        val rms = sqrt(power)
        var target = if (leveling && rms > 0.015) (0.13 / rms).coerceIn(0.33, 3.0) else 1.0
        if (night > 0f) {
            val compression = if (rms > 0.12) (0.12 / rms).pow(0.75) else 1.0
            target *= 1.0 + night * (compression * 1.35 - 1.0)
        }
        val coefficient = if (target < gain) gainAttack else gainRelease
        gain = coefficient * gain + (1.0 - coefficient) * target
        return if (peak > 0f) gain.coerceAtMost(0.95 / peak).toFloat() else gain.toFloat()
    }
}

internal class VideoDynamicsProcessor : BaseAudioProcessor() {
    data class Settings(val night: Float = 0f, val leveling: Boolean = false)
    @Volatile var settings = Settings()
    private val dynamics = VideoDynamics()

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return AudioProcessor.AudioFormat.NOT_SET
        dynamics.configure(inputAudioFormat.sampleRate)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        // Media3 polls the pipeline with its shared EMPTY_BUFFER before PCM arrives.
        if (!inputBuffer.hasRemaining()) return
        val output = replaceOutputBuffer(inputBuffer.remaining())
        val config = settings
        if (config.night == 0f && !config.leveling) {
            output.put(inputBuffer)
        } else {
            val channels = inputAudioFormat.channelCount
            val bytesPerFrame = channels * 2
            while (inputBuffer.remaining() >= bytesPerFrame) {
                val position = inputBuffer.position()
                var peak = 0f
                for (channel in 0 until channels) {
                    peak = maxOf(peak, abs(inputBuffer.getShort(position + channel * 2).toInt()) / 32768f)
                }
                val gain = dynamics.frameGain(peak, config.night, config.leveling)
                repeat(channels) {
                    output.putShort((inputBuffer.short * gain).toInt().coerceIn(-32768, 32767).toShort())
                }
            }
            output.put(inputBuffer)
        }
        output.flip()
    }

    override fun onFlush() { dynamics.reset() }
}
