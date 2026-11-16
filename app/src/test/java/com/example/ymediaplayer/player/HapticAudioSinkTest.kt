@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ymediaplayer.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.AudioSink
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class HapticAudioSinkTest {
    @Test fun musicPcmIsPreservedAndOnlyBassOnsetsAreQueuedOnceAcrossRetries() {
        for (encoding in listOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT)) {
            val delegate = mock(AudioSink::class.java)
            val haptics = mock(VideoHaptics::class.java)
            val timeline = HapticTimeline()
            `when`(haptics.active).thenReturn(true)
            `when`(haptics.tuning).thenReturn(HapticTuning(textures = false))
            `when`(haptics.timeline).thenReturn(timeline)
            val sink = HapticAudioSink(delegate, haptics, musicMode = true)
            sink.configure(AudioSink.AudioSinkConfig.Builder(format(encoding)).build())
            val bytes = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
            val buffer = ByteBuffer.allocateDirect(48000 * 2 * bytes).order(ByteOrder.LITTLE_ENDIAN)
            repeat(48000) { frame -> repeat(2) { channel ->
                val age = frame / 48000.0 - 0.4
                val value = if (age in 0.0..0.16) 0.65 * kotlin.math.exp(-age * 22) *
                    kotlin.math.sin(2 * kotlin.math.PI * 65 * age) * (if (channel == 0) 1 else -1) else 0.0
                if (bytes == 4) buffer.putFloat(value.toFloat()) else buffer.putShort((value * 32767).toInt().toShort())
            } }
            buffer.flip()
            val original = ByteArray(buffer.remaining()).also { buffer.duplicate().get(it) }
            buffer.order(ByteOrder.BIG_ENDIAN)
            `when`(delegate.handleBuffer(buffer, 0, 1)).thenReturn(false, true)
            assertFalse(sink.handleBuffer(buffer, 0, 1))
            assertTrue(sink.handleBuffer(buffer, 0, 1))
            assertEquals(ByteOrder.BIG_ENDIAN, buffer.order())
            assertEquals(0, buffer.position())
            assertArrayEquals(original, ByteArray(buffer.remaining()).also { buffer.duplicate().get(it) })
            assertEquals(CinemaImpact.SOFT_THUMP, timeline.due(420000, 1f, 0))
            assertNull(timeline.due(420000, 1f, 200))
            verify(haptics, times(1)).onImpactQueued()
        }
    }

    private fun format(encoding: Int, mime: String = MimeTypes.AUDIO_RAW) = Format.Builder()
        .setSampleMimeType(mime).setSampleRate(48000).setChannelCount(2).setPcmEncoding(encoding).build()

    @Test fun pcmIsUntouchedAndDelegateRetriesDoNotDuplicateEvents() {
        for (encoding in listOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT)) {
            val delegate = mock(AudioSink::class.java)
            val haptics = mock(VideoHaptics::class.java)
            val timeline = HapticTimeline()
            `when`(haptics.active).thenReturn(true)
            `when`(haptics.tuning).thenReturn(HapticTuning())
            `when`(haptics.timeline).thenReturn(timeline)
            val sink = HapticAudioSink(delegate, haptics)
            sink.configure(AudioSink.AudioSinkConfig.Builder(format(encoding)).build())
            val bytes = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
            val buffer = ByteBuffer.allocateDirect(30000 * 2 * bytes).order(ByteOrder.LITTLE_ENDIAN)
            repeat(30000) { frame -> repeat(2) { channel ->
                val sample = if (frame in 24000..24479) (if ((frame + channel) % 2 == 0) 0.8 else -0.8) else 0.0
                if (bytes == 4) buffer.putFloat(sample.toFloat()) else buffer.putShort((sample * 32768).toInt().toShort())
            } }
            buffer.flip()
            val original = ByteArray(buffer.remaining()).also { buffer.duplicate().get(it) }
            buffer.order(ByteOrder.BIG_ENDIAN)
            `when`(delegate.handleBuffer(buffer, 0, 1)).thenReturn(false, true)
            assertFalse(sink.handleBuffer(buffer, 0, 1))
            assertTrue(sink.handleBuffer(buffer, 0, 1))
            assertEquals(0, buffer.position()) // wrapper never advances or modifies input
            assertEquals(ByteOrder.BIG_ENDIAN, buffer.order())
            val after = ByteArray(buffer.remaining()).also { buffer.duplicate().get(it) }
            assertArrayEquals(original, after)
            assertEquals(CinemaImpact.TAP, timeline.due(505000, 1f, 0))
            assertNull(timeline.due(505000, 1f, 200))
            verify(haptics, times(1)).onImpactQueued()
        }
    }

    @Test fun compressedAndUnsupportedPcmAreForwardedWithoutAnalysis() {
        for (format in listOf(format(C.ENCODING_PCM_24BIT), format(C.ENCODING_INVALID, MimeTypes.AUDIO_AC3))) {
            val delegate = mock(AudioSink::class.java)
            val haptics = mock(VideoHaptics::class.java)
            val timeline = HapticTimeline()
            `when`(haptics.timeline).thenReturn(timeline)
            `when`(haptics.active).thenReturn(true)
            `when`(haptics.tuning).thenReturn(HapticTuning())
            val sink = HapticAudioSink(delegate, haptics)
            sink.configure(AudioSink.AudioSinkConfig.Builder(format).build())
            val buffer = ByteBuffer.allocateDirect(32)
            sink.handleBuffer(buffer, 100, 1)
            verify(delegate).handleBuffer(buffer, 100, 1)
            assertNull(timeline.due(Long.MAX_VALUE, 1f, 100))
        }
    }

    @Test fun unavailableOutputNeverPlaysAndLifecycleCancelsIt() {
        val context = mock(Context::class.java)
        val output = mock(CinemaHapticOutput::class.java)
        `when`(output.available).thenReturn(false)
        val controller = VideoHaptics(context, output)
        controller.setEnabled(true)
        controller.setPlaying(true)
        assertFalse(controller.active)
        controller.setPlaying(false)
        controller.setEnabled(false)
        controller.release()
        verify(output, never()).play(CinemaImpact.TAP)
        verify(output, never()).play(CinemaImpact.THUMP)
        verify(output, atLeastOnce()).cancel()
    }
}
