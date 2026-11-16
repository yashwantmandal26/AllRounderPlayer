package com.example.ymediaplayer.player

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Optional local-content probe. No copyrighted media is stored in the repository. */
class HapticCalibrationTest {
    @Test fun analyzeLocalPcmReference() {
        val path = System.getenv("HAPTIC_CALIBRATION_WAV")
        assumeTrue("Set HAPTIC_CALIBRATION_WAV to a stereo PCM16/48kHz WAV", path != null)
        val bytes = File(path!!).readBytes()
        val pcm = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE")
        var offset = 12
        var start = 0
        var size = 0
        while (offset + 8 <= bytes.size) {
            val length = pcm.getInt(offset + 4)
            when (String(bytes, offset, 4)) {
                "fmt " -> require(pcm.getShort(offset + 8).toInt() == 1 &&
                    pcm.getShort(offset + 10).toInt() == 2 && pcm.getInt(offset + 12) == 48000 &&
                    pcm.getShort(offset + 22).toInt() == 16)
                "data" -> { start = offset + 8; size = length; break }
            }
            offset += 8 + length + (length and 1)
        }
        require(size > 0 && start + size <= bytes.size)
        val detector = HapticDetector().apply { configure(48000, 2) }
        val timeline = HapticTimeline()
        val events = mutableListOf("onsetSeconds,confirmationSeconds,impact,shape,peakAmplitude,durationMs")
        val playback = mutableListOf("audioSeconds,shape,peakAmplitude,durationMs")
        fun dispatch(positionUs: Long) {
            if (positionUs < 0) return
            timeline.dueSignal(positionUs, 1f, positionUs / 1000)?.let {
                playback.add("${positionUs / 1000000.0},${it.shape},${it.amplitudes.max()},${it.durationMs}")
            }
        }
        val started = System.nanoTime()
        for (frame in 0 until size / 4) {
            val timeUs = frame * 1000000L / 48000
            for (channel in 0..1) {
                detector.sample(pcm.getShort(start + frame * 4 + channel * 2) / 32768.0, channel, timeUs)?.let {
                    val signal = detector.signal!!
                    timeline.offer(detector.impactTimeUs, signal, timeline.generation)
                    events.add("${detector.impactTimeUs / 1000000.0},${timeUs / 1000000.0},$it,${signal.shape},${signal.amplitudes.max()},${signal.durationMs}")
                }
            }
            // Emulate a bounded 150 ms decoder lead and a 1 ms output worker.
            if (frame % 48 == 0) dispatch(timeUs - 150000)
        }
        val endUs = size / 4 * 1000000L / 48000
        for (timeUs in (endUs - 150000)..endUs step 1000) dispatch(timeUs)
        val output = File(path).resolveSibling("events.csv")
        output.writeText(events.joinToString("\n"))
        File(path).resolveSibling("played.csv").writeText(playback.joinToString("\n"))
        println("Calibration: ${events.size - 1} detected; ${playback.size - 1} dispatched; analysisMs=${(System.nanoTime() - started) / 1000000}; output=$output")
    }
}
