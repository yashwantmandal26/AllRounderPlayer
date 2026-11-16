package com.example.ymediaplayer.player

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class HapticPreviewTest {
    @Test fun soundCueDeliversEnvelopeAndCloseCancelsPendingPreview() {
        val ins = InstrumentationRegistry.getInstrumentation()
        val played = CountDownLatch(1)
        val calls = AtomicInteger()
        val output = object : CinemaHapticOutput {
            override val available = true
            override val supportsTextures = true
            override fun play(impact: CinemaImpact) { fail("Expected shaped envelope") }
            override fun play(signal: HapticSignal) { calls.incrementAndGet(); played.countDown() }
            override fun cancel() {}
        }
        val preview = HapticPreviewController(ins.targetContext, {}, output, output)
        val sample = HapticPreviewSamples.create(HapticPreviewType.MIX, HapticTuning(), compact = true)
        try {
            preview.preview(sample, soundCue = true)
            assertTrue("Audio-clock preview must reach the output", played.await(5, TimeUnit.SECONDS))
            preview.preview(sample, soundCue = true)
            preview.close()
            Thread.sleep(500)
            assertEquals("Closing must prevent a pending cue", 1, calls.get())
        } finally { preview.close() }
    }
}
