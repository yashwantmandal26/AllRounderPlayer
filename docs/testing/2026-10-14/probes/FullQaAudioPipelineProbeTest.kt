@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ymediaplayer.player

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessingPipeline
import androidx.media3.common.audio.AudioProcessor
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.collect.ImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Temporary QA probes. Production code is intentionally unchanged. */
@RunWith(AndroidJUnit4::class)
class FullQaAudioPipelineProbeTest {
    private val format = AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT)

    private fun processor(leveling: Boolean) = VideoDynamicsProcessor().apply {
        settings = VideoDynamicsProcessor.Settings(leveling = leveling)
        configure(format)
        flush()
    }

    private fun chunk(index: Int): ByteBuffer = ByteBuffer.allocateDirect(256 * 4)
        .order(ByteOrder.nativeOrder()).apply {
            repeat(256) { frame ->
                putShort(((frame + index * 13) % 2000 - 1000).toShort())
                putShort(((frame + index * 17) % 4000 - 2000).toShort())
            }
            flip()
        }

    private fun assertSequentialDirectChunks(leveling: Boolean) {
        val processor = processor(leveling)
        try {
            repeat(12) { index ->
                val input = chunk(index)
                val expected = ByteArray(input.remaining()).also { input.duplicate().get(it) }
                Log.i(TAG, "direct leveling=$leveling chunk=$index inputBytes=${input.remaining()}")
                processor.queueInput(input)
                assertFalse("All input should be consumed", input.hasRemaining())
                val output = processor.output
                assertEquals(expected.size, output.remaining())
                val actual = ByteArray(output.remaining()).also { output.get(it) }
                if (!leveling) assertTrue("Normal must preserve PCM bytes", expected.contentEquals(actual))
                assertFalse("Output should be consumed before the next chunk", output.hasRemaining())
                assertFalse("A second getOutput should be empty", processor.output.hasRemaining())
            }
        } finally {
            processor.reset()
        }
    }

    private fun assertPipelineStartupAndSequentialChunks(leveling: Boolean) {
        val processor = VideoDynamicsProcessor().apply {
            settings = VideoDynamicsProcessor.Settings(leveling = leveling)
        }
        val pipeline = AudioProcessingPipeline(ImmutableList.of<AudioProcessor>(processor))
        pipeline.configure(format)
        pipeline.flush()
        try {
            // DefaultAudioSink.processBuffers checks pipeline output BEFORE feeding its
            // first input. AudioProcessingPipeline.getOutput internally queues EMPTY_BUFFER.
            Log.i(TAG, "pipeline leveling=$leveling getOutput BEFORE first input")
            assertFalse("Fresh pipeline has no PCM output", pipeline.output.hasRemaining())
            repeat(12) { index ->
                val input = chunk(index)
                val expectedBytes = input.remaining()
                Log.i(TAG, "pipeline leveling=$leveling queue chunk=$index bytes=$expectedBytes")
                pipeline.queueInput(input)
                assertFalse("Pipeline should consume each input", input.hasRemaining())
                val output = pipeline.output
                assertEquals(expectedBytes, output.remaining())
                output.position(output.limit())
                assertFalse("A drained pipeline should have no extra bytes", pipeline.output.hasRemaining())
            }
            pipeline.queueEndOfStream()
            assertFalse(pipeline.output.hasRemaining())
            assertTrue(pipeline.isEnded)
        } catch (failure: Throwable) {
            Log.e(TAG, "pipeline leveling=$leveling failed: ${failure.javaClass.name}: ${failure.message}", failure)
            throw failure
        } finally {
            pipeline.reset()
        }
    }

    @Test fun normalSequentialPcm16StereoChunks() = assertSequentialDirectChunks(leveling = false)

    @Test fun volumeLevelingSequentialPcm16StereoChunks() = assertSequentialDirectChunks(leveling = true)

    @Test fun normalPipelineStartupEmptyDrainThenSequentialChunks() =
        assertPipelineStartupAndSequentialChunks(leveling = false)

    @Test fun volumeLevelingPipelineStartupEmptyDrainThenSequentialChunks() =
        assertPipelineStartupAndSequentialChunks(leveling = true)

    private companion object {
        const val TAG = "FullQaAudioPipeline"
    }
}
