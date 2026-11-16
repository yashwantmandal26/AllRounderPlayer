@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ymediaplayer.player

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.test.platform.app.InstrumentationRegistry
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class MusicHapticsPlaybackTest {
    @Test fun bassPulsesFollowRealAudioClockAndPauseToggleSeekReleaseCancelOutput() {
        val thread = android.os.HandlerThread("MusicHapticsProbe").apply { start() }
        val done = java.util.concurrent.CountDownLatch(1)
        var failure: Throwable? = null
        android.os.Handler(thread.looper).post {
            try { runAudioClockProbe() } catch (error: Throwable) { failure = error }
            finally { done.countDown() }
        }
        try {
            assertTrue("Audio clock probe timed out", done.await(15, java.util.concurrent.TimeUnit.SECONDS))
            failure?.let { throw it }
        } finally { thread.quitSafely() }
    }

    private fun runAudioClockProbe() {
        val ins = InstrumentationRegistry.getInstrumentation()
        val events = CopyOnWriteArrayList<HapticSignal>()
        val physicalOutput = if (InstrumentationRegistry.getArguments().getString("physicalHaptics") == "true") {
            AndroidCinemaHapticOutput(ins.targetContext)
        } else null
        val audiblePosition = java.util.concurrent.atomic.AtomicLong(-1L)
        val dispatchedAt = CopyOnWriteArrayList<Long>()
        val output = object : CinemaHapticOutput {
            override val available = true
            override fun play(impact: CinemaImpact) { error("Expected shaped music pulse") }
            override fun play(signal: HapticSignal) {
                dispatchedAt.add(audiblePosition.get())
                events.add(signal)
                physicalOutput?.play(signal)
            }
            override fun cancel() { physicalOutput?.cancel() }
        }
        lateinit var haptics: VideoHaptics
        ins.runOnMainSync {
            haptics = VideoHaptics(ins.targetContext, output, musicMode = true)
            haptics.setEnabled(true); haptics.setPlaying(true)
        }
        val sink = HapticAudioSink(DefaultAudioSink.Builder(ins.targetContext).build(), haptics, musicMode = true)
        sink.setListener(object : AudioSink.Listener {
            override fun onPositionDiscontinuity() {}
            override fun onUnderrun(bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) {}
            override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) {}
        })
        try {
            val format = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW)
                .setSampleRate(48000).setChannelCount(2).setPcmEncoding(C.ENCODING_PCM_16BIT).build()
            sink.getFormatSupport(format) // Initializes the real sink's capability receiver before release.
            sink.configure(AudioSink.AudioSinkConfig.Builder(format).build())
            ins.waitForIdleSync()
            sink.setVolume(0f) // Real AudioTrack clock, without disturbing the user's music volume.
            sink.play()
            repeat(200) { chunk ->
                val buffer = ByteBuffer.allocateDirect(480 * 4).order(ByteOrder.LITTLE_ENDIAN)
                repeat(480) { frame ->
                    val t = (chunk * 480 + frame) / 48000.0
                    val age = t - (0.4 + ((t - 0.4) / 0.4).toInt() * 0.4)
                    val gain = when { t < 0.8 -> 0.18; t < 1.2 -> 0.35; t < 1.6 -> 0.65; else -> 1.0 }
                    val value = if (t >= 0.4 && age in 0.0..0.16)
                        0.65 * gain * exp(-age * 22) * sin(2 * PI * 65 * age) else 0.0
                    val pcm = (value * 32767).toInt().toShort()
                    buffer.putShort(pcm); buffer.putShort((-pcm).toShort())
                }
                buffer.flip()
                val deadline = SystemClock.elapsedRealtime() + 5000
                while (!sink.handleBuffer(buffer, chunk * 10_000L, 1)) {
                    audiblePosition.set(sink.getCurrentPositionUs(false))
                    check(SystemClock.elapsedRealtime() < deadline) { "Audio sink stalled" }
                    Thread.sleep(5)
                }
                audiblePosition.set(sink.getCurrentPositionUs(false))
            }
            val deadline = SystemClock.elapsedRealtime() + 4000
            while (!sink.isEnded && SystemClock.elapsedRealtime() < deadline) {
                sink.playToEndOfStream(); audiblePosition.set(sink.getCurrentPositionUs(true)); Thread.sleep(5)
            }
            assertEquals("Four bass beats should reach the output", 4, events.size)
            assertTrue("Real PCM must produce increasing pulse strengths",
                events.map { it.amplitudes.max() }.zipWithNext().all { (a, b) -> b > a })
            dispatchedAt.forEachIndexed { index, position ->
                val onset = 400_000L + index * 400_000L
                assertTrue("Beat $index dispatched at $position for onset $onset", position - onset in -55_000L..10_000L)
            }
            assertTrue(events.all { !it.texture && it.durationMs == 28L && it.amplitudes.max() <= 218 })
            for (transition in listOf<() -> Unit>(
                { haptics.setPlaying(false) }, { haptics.setEnabled(false) },
                { haptics.invalidate() }, { haptics.release() }
            )) {
                ins.runOnMainSync {
                    haptics.setPlaying(true); haptics.setEnabled(true)
                    haptics.timeline.offer(0L, CinemaImpact.THUMP, haptics.timeline.generation)
                    transition()
                }
                assertFalse(haptics.timeline.hasPendingEvents())
                val count = events.size
                Thread.sleep(80)
                assertEquals(count, events.size)
            }
        } finally {
            sink.reset(); sink.release()
            ins.runOnMainSync { haptics.release() }
        }
    }
}
