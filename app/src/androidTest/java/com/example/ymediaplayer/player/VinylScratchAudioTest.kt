package com.example.ymediaplayer.player

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class VinylScratchAudioTest {
    @Test fun rendersSamplesInBothDirectionsAndStopsWhenReleased() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "scratch-test.wav")
        val rate = 22050
        val count = rate * 5
        val bytes = ByteBuffer.allocate(44 + count * 2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36 + count * 2).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(count * 2)
        repeat(count) { bytes.putShort((-12000 + (it % rate) * 24000 / rate).toShort()) }
        file.writeBytes(bytes.array())
        val audio = VinylScratchAudio(context, Uri.fromFile(file))
        audio.outputVolume = 0f
        val blocks = CopyOnWriteArrayList<ShortArray>()
        audio.onPcmBlock = { blocks.add(it) }
        try {
            audio.prepare(2500)
            assertTrue("Decoded window can start a real PCM track", audio.begin(2500))
            audio.move(2700)
            Thread.sleep(85)
            val forward = blocks.toList()
            blocks.clear()
            audio.move(2300)
            Thread.sleep(100)
            val reverse = blocks.toList()
            assertTrue(audio.constrain(-100) >= 0)
            assertTrue(audio.constrain(100_000) < 5001)
            audio.end()
            fun slope(block: ShortArray) = block.last().toInt() - block.first().toInt()
            assertTrue("Forward movement emits ascending source samples", forward.any { slope(it) > 20 })
            assertTrue("Reverse movement emits descending source samples", reverse.any { slope(it) < -20 })
            val stopped = blocks.size
            Thread.sleep(50)
            assertEquals("Releasing the disc stops preview audio", stopped, blocks.size)
            assertEquals("An inactive cache must not pull a later seek into an old window", 100_000L, audio.constrain(100_000))
            assertTrue("Repeated scratches can restart", audio.begin(2500))
            audio.end()
        } finally {
            audio.close()
            file.delete()
        }
    }
}
