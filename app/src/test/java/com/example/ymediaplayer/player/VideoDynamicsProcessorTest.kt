@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ymediaplayer.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VideoDynamicsProcessorTest {
    private fun processor(): VideoDynamicsProcessor = VideoDynamicsProcessor().apply {
        configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT))
        flush()
    }

    @Test fun originalModePreservesEverySample() {
        val processor = processor()
        val values = shortArrayOf(-32768, 32767, -2000, 1000, 0, 0)
        val input = ByteBuffer.allocateDirect(values.size * 2).order(ByteOrder.nativeOrder())
        values.forEach { input.putShort(it) }
        input.flip()
        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())
        values.forEach { assertEquals(it, output.short) }
        assertEquals(0, input.remaining())
    }

    @Test fun linkedGainPreservesStereoBalance() {
        val processor = processor()
        processor.settings = VideoDynamicsProcessor.Settings(night = 1f)
        val input = ByteBuffer.allocateDirect(48000 * 4).order(ByteOrder.nativeOrder())
        repeat(48000) { input.putShort(12000); input.putShort(24000) }
        input.flip()
        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())
        output.position(output.limit() - 4)
        val left = output.short.toInt()
        val right = output.short.toInt()
        assertTrue(right < 16000)
        assertTrue(kotlin.math.abs(left * 2 - right) <= 1)
    }
}
