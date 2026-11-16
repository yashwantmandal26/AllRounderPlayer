@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ymediaplayer.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class MusicSoundProcessorTest {
    private fun processor(mode: MusicProcessingMode, rate: Int = 48000, channels: Int = 2) = MusicSoundProcessor().apply {
        this.mode = mode
        configure(AudioProcessor.AudioFormat(rate, channels, C.ENCODING_PCM_16BIT))
        flush()
    }
    private fun pcm(rate: Int = 48000, channels: Int = 2, sample: (Double, Int) -> Double): ByteBuffer {
        return ByteBuffer.allocateDirect(rate * channels * 2).order(ByteOrder.nativeOrder()).apply {
            repeat(rate) { frame -> repeat(channels) { channel ->
                putShort((sample(frame.toDouble() / rate, channel) * 32768).roundToInt().coerceIn(-32768, 32767).toShort())
            } }
            flip()
        }
    }
    private fun render(mode: MusicProcessingMode, frequency: Double, amplitude: Double = 0.08, rate: Int = 48000): DoubleArray {
        val dsp = processor(mode, rate)
        dsp.queueInput(pcm(rate) { t, _ -> amplitude * sin(2 * PI * frequency * t) })
        val output = dsp.output
        return DoubleArray(rate) { val value = output.short / 32768.0; output.short; value }
    }
    private fun amplitude(samples: DoubleArray, frequency: Double, rate: Int = 48000): Double {
        var real = 0.0; var imaginary = 0.0
        val start = samples.size * 3 / 5 // 0.4 seconds: whole periods for all fixture frequencies.
        for (frame in start until samples.size) {
            real += samples[frame] * cos(2 * PI * frequency * frame / rate)
            imaginary += samples[frame] * sin(2 * PI * frequency * frame / rate)
        }
        return 2 * sqrt(real * real + imaginary * imaginary) / (samples.size - start)
    }

    @Test fun originalPreservesEveryPcmByteAtAllSupportedChannelCounts() {
        for (channels in listOf(1, 2, 6, 8)) {
            val input = pcm(channels = channels) { t, ch -> sin(t * 1300 + ch) }
            val expected = ByteArray(input.remaining()).also { input.duplicate().get(it) }
            val dsp = processor(MusicProcessingMode.ORIGINAL, channels = channels)
            dsp.queueInput(input)
            assertFalse(input.hasRemaining())
            val output = dsp.output
            assertArrayEquals(expected, ByteArray(output.remaining()).also { output.get(it) })
        }
    }

    @Test fun modesHaveDistinctAudibleSpeakerBandResponsesAcrossSampleRates() {
        for (rate in listOf(16000, 44100, 48000, 96000)) {
            fun response(mode: MusicProcessingMode, frequency: Double) = amplitude(render(mode, frequency, rate = rate), frequency, rate) / 0.08
            assertTrue(response(MusicProcessingMode.BASS_PUNCH, 220.0) > 1.4)
            assertTrue(response(MusicProcessingMode.VOCAL_BOOST, 1800.0) > 1.5)
            assertTrue(response(MusicProcessingMode.VOCAL_BOOST, 220.0) < 0.65)
            assertTrue(response(MusicProcessingMode.LIVE_STAGE, 3200.0) > 1.25)
            assertTrue(response(MusicProcessingMode.LIVE_STAGE, 900.0) < 0.75)
        }
    }

    @Test fun bassPunchCreatesUpperHarmonicsThatSmallSpeakersCanReproduce() {
        val original = render(MusicProcessingMode.ORIGINAL, 65.0, 0.6)
        val punch = render(MusicProcessingMode.BASS_PUNCH, 65.0, 0.6)
        assertTrue(amplitude(punch, 130.0) > 0.05)
        assertTrue(amplitude(punch, 195.0) > 0.02)
        assertTrue(amplitude(original, 130.0) < 0.0001)
    }

    @Test fun loudInputsDoNotClipAndSilenceDoesNotGenerateNoise() {
        for (mode in MusicProcessingMode.entries.filter { it != MusicProcessingMode.ORIGINAL }) {
            val dsp = processor(mode)
            dsp.queueInput(pcm { t, ch -> 0.9 * sin(2 * PI * (if (ch == 0) 220 else 1800) * t) })
            val output = dsp.output
            while (output.hasRemaining()) assertTrue(abs(output.short.toInt()) <= 31785)
            dsp.flush()
            dsp.queueInput(pcm { _, _ -> 0.0 })
            val silence = dsp.output
            while (silence.hasRemaining()) assertEquals(0, silence.short.toInt())
        }
    }

    @Test fun liveChangesKeepAudioLengthAndOriginalReturnsToExactBypass() {
        val dsp = processor(MusicProcessingMode.BASS_PUNCH)
        dsp.queueInput(pcm { t, _ -> 0.25 * sin(2 * PI * 220 * t) }); dsp.output
        dsp.mode = MusicProcessingMode.ORIGINAL
        val source = pcm { t, _ -> 0.4 * sin(2 * PI * 1800 * t) }
        val expected = ByteArray(source.remaining()).also { source.duplicate().get(it) }
        dsp.queueInput(source)
        val output = dsp.output
        val actual = ByteArray(output.remaining()).also { output.get(it) }
        assertEquals(expected.size, actual.size)
        assertArrayEquals(expected.copyOfRange(4800, expected.size), actual.copyOfRange(4800, actual.size))
        dsp.flush()
        dsp.queueInput(pcm { _, _ -> 0.0 })
        val silence = dsp.output
        while (silence.hasRemaining()) assertEquals(0, silence.short.toInt())
    }
}
