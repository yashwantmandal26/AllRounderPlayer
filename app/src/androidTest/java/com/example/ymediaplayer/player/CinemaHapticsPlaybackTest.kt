@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ymediaplayer.player

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real AudioTrack/sink clock, with recorded output instead of physically vibrating the phone. */
class CinemaHapticsPlaybackTest {
    @Test fun dynamicPacketsFollowTheClockWithSavedOffsetAndWithoutImpactCooldown() {
        val ins = InstrumentationRegistry.getInstrumentation()
        val delivered = CopyOnWriteArrayList<Pair<Long, HapticSignal>>()
        val output = object : CinemaHapticOutput {
            override val available = true
            override fun play(impact: CinemaImpact) { error("Expected an envelope") }
            override fun play(signal: HapticSignal) { delivered.add(SystemClock.elapsedRealtime() to signal) }
            override fun cancel() {}
        }
        lateinit var haptics: VideoHaptics
        ins.runOnMainSync {
            haptics = VideoHaptics(ins.targetContext, output)
            haptics.setEnabled(true); haptics.setPlaying(true); haptics.setFormatSupported(true)
        }
        ins.waitForIdleSync()
        ins.runOnMainSync { haptics.setTuning(1f, 20) }
        try {
            val token = haptics.timeline.generation
            val start = SystemClock.elapsedRealtime()
            listOf(0.1f, 0.2f, 0.4f).forEachIndexed { index, amplitude ->
                haptics.timeline.offer(40000L + index * 80000L, HapticSignal.texture(FloatArray(16) { amplitude }), token)
            }
            haptics.publishPosition(0, 1f, token)
            haptics.onImpactQueued()
            while (SystemClock.elapsedRealtime() - start < 300) {
                haptics.publishPosition((SystemClock.elapsedRealtime() - start) * 1000, 1f, token)
                Thread.sleep(5)
            }
            assertEquals(1, delivered.size)
            val (time, signal) = delivered.single()
            assertTrue("Dispatch error: ${time - start - 60} ms", time - start - 60 in -2L..25L)
            assertTrue(signal.texture)
            assertTrue(signal.durationMs in 215L..240L)
            assertTrue("Batched envelope must end on its audio deadline", time - start + signal.durationMs in 297L..305L)
            assertTrue(signal.amplitudes.last() > signal.amplitudes.first() * 3)
        } finally {
            ins.runOnMainSync { haptics.release() }
        }
    }

    @Test fun hapticDeadlineSurvivesBusyUiAndReleaseStopsOutput() {
        val ins = InstrumentationRegistry.getInstrumentation()
        val played = CountDownLatch(1)
        val events = CopyOnWriteArrayList<Long>()
        var playedOnMain = false
        val output = object : CinemaHapticOutput {
            override val available = true
            override fun play(impact: CinemaImpact) {
                playedOnMain = android.os.Looper.myLooper() == android.os.Looper.getMainLooper()
                events.add(SystemClock.elapsedRealtime())
                played.countDown()
            }
            override fun cancel() {}
        }
        lateinit var haptics: VideoHaptics
        ins.runOnMainSync {
            haptics = VideoHaptics(ins.targetContext, output)
            haptics.setEnabled(true)
            haptics.setPlaying(true)
            haptics.setFormatSupported(true)
        }
        ins.waitForIdleSync()
        try {
            val start = SystemClock.elapsedRealtime()
            val token = haptics.timeline.generation
            haptics.timeline.offer(30000, CinemaImpact.THUMP, token)
            haptics.publishPosition(0, 1f, token)
            haptics.onImpactQueued()
            ins.runOnMainSync { Thread.sleep(100) }
            assertTrue("Worker must dispatch while UI is busy", played.await(1, TimeUnit.SECONDS))
            assertFalse("Vibrator output must not run on UI thread", playedOnMain)
            assertTrue("Impact should precede the end of the UI stall", events.single() - start < 90)
            ins.runOnMainSync { haptics.release() }
            haptics.timeline.offer(30000, CinemaImpact.TAP, haptics.timeline.generation)
            haptics.publishPosition(30000, 1f, haptics.timeline.generation)
            haptics.onImpactQueued()
            Thread.sleep(120)
            assertEquals(1, events.size)
        } finally {
            ins.runOnMainSync { haptics.release() }
        }
    }

    @Test fun realSinkClockDeliversImpactAndDisableStopsScheduling() {
        if (android.os.Looper.myLooper() == null) android.os.Looper.prepare()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val events = CopyOnWriteArrayList<CinemaImpact>()
        val output = object : CinemaHapticOutput {
            override val available = true
            override fun play(impact: CinemaImpact) { events.add(impact) }
            override fun cancel() {}
        }
        lateinit var haptics: VideoHaptics
        instrumentation.runOnMainSync {
            haptics = VideoHaptics(instrumentation.targetContext, output)
            haptics.setEnabled(true)
            haptics.setPlaying(true)
        }
        val sink = HapticAudioSink(DefaultAudioSink.Builder(instrumentation.targetContext).build(), haptics)
        sink.setListener(object : AudioSink.Listener {
            override fun onPositionDiscontinuity() {}
            override fun onUnderrun(bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) {}
            override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) {}
        })
        try {
            val format = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW)
                .setSampleRate(48000).setChannelCount(2).setPcmEncoding(C.ENCODING_PCM_16BIT).build()
            sink.configure(AudioSink.AudioSinkConfig.Builder(format).build())
            instrumentation.waitForIdleSync()
            sink.setVolume(0f)
            sink.play()
            repeat(150) { chunk ->
                val buffer = ByteBuffer.allocateDirect(480 * 4).order(ByteOrder.LITTLE_ENDIAN)
                repeat(480) { frame ->
                    val sample = if (chunk in 40..41 || chunk in 100..101)
                        (if (frame % 2 == 0) 26000 else -26000).toShort() else 0.toShort()
                    buffer.putShort(sample); buffer.putShort((-sample).toShort())
                }
                buffer.flip()
                val deadline = SystemClock.elapsedRealtime() + 5000
                while (!sink.handleBuffer(buffer, chunk * 10000L, 1)) {
                    sink.getCurrentPositionUs(false)
                    check(SystemClock.elapsedRealtime() < deadline) { "Audio sink stalled" }
                    Thread.sleep(5)
                }
                sink.getCurrentPositionUs(false)
            }
            val deadline = SystemClock.elapsedRealtime() + 4000
            while (!sink.isEnded && SystemClock.elapsedRealtime() < deadline) {
                sink.playToEndOfStream()
                sink.getCurrentPositionUs(true)
                Thread.sleep(5)
            }
            assertTrue("Expected at least one scheduled PCM impact", events.isNotEmpty())
            assertTrue(events.size <= 2)
            instrumentation.runOnMainSync { haptics.setEnabled(false) }
            assertFalse(haptics.active)
            val count = events.size
            Thread.sleep(120)
            assertEquals(count, events.size)
        } finally {
            sink.reset()
            sink.release()
            instrumentation.runOnMainSync { haptics.release() }
        }
    }
}
