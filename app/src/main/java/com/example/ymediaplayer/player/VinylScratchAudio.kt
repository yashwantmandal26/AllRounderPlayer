package com.example.ymediaplayer.player

import android.content.Context
import android.media.*
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/** A bounded PCM window, separate from the compressed playback/seek pipeline. */
internal class VinylScratchAudio(private val context: Context, private val uri: Uri) : AutoCloseable {
    /** Optional diagnostic tap, invoked before the PCM block is submitted to AudioTrack. */
    internal var onPcmBlock: ((ShortArray) -> Unit)? = null
    internal var outputVolume: Float = 1f
    private data class Window(val samples: ShortArray, val rate: Int, val channels: Int, val startMs: Double) {
        val frames get() = samples.size / channels
        fun frame(ms: Double) = (ms - startMs) * rate / 1000.0
        fun contains(ms: Long, margin: Int = 0) = frame(ms.toDouble()) in
            (if (startMs < 1.0) 0.0 else margin.toDouble())..(frames - margin - 2).toDouble()
    }
    @Volatile private var window: Window? = null
    @Volatile private var targetMs = 0.0
    @Volatile private var closed = false
    private val active = AtomicBoolean(false)
    private var worker: Thread? = null
    private var session: AtomicBoolean? = null

    suspend fun prepare(positionMs: Long) = withContext(Dispatchers.IO) {
        val cached = window
        if (closed || cached?.contains(positionMs, cached.rate * 4) == true) return@withContext
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val index = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return@withContext
            extractor.selectTrack(index)
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext
            val startUs = (positionMs - 12_000L).coerceAtLeast(0L) * 1000L
            val endUs = startUs + 24_000_000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val codec = MediaCodec.createDecoderByType(mime)
            decoder = codec
            format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            codec.configure(format, null, null, 0)
            codec.start()
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var firstMs: Double? = null
            var inputEnded = false
            var outputEnded = false
            var idle = 0
            val bytes = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            while (!outputEnded && !closed && idle < 300) {
                currentCoroutineContext().ensureActive()
                if (!inputEnded) {
                    val input = codec.dequeueInputBuffer(5_000)
                    if (input >= 0) {
                        val buffer = codec.getInputBuffer(input)!!
                        val size = extractor.readSampleData(buffer, 0)
                        val time = extractor.sampleTime
                        if (size < 0 || time > endUs + 1_000_000) {
                            codec.queueInputBuffer(input, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            codec.queueInputBuffer(input, 0, size, time, 0)
                            extractor.advance()
                        }
                    }
                }
                val output = codec.dequeueOutputBuffer(info, 5_000)
                when {
                    output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val decoded = codec.outputFormat
                        rate = decoded.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = decoded.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (channels !in 1..2 || (decoded.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            decoded.getInteger(MediaFormat.KEY_PCM_ENCODING) != AudioFormat.ENCODING_PCM_16BIT)) return@withContext
                    }
                    output >= 0 -> {
                        idle = 0
                        val frameBytes = channels * 2
                        val skipFrames = ((startUs - info.presentationTimeUs).coerceAtLeast(0) * rate / 1_000_000L)
                            .coerceAtMost((info.size / frameBytes).toLong()).toInt()
                        val keepFrames = ((endUs - info.presentationTimeUs).coerceAtLeast(0) * rate / 1_000_000L)
                            .coerceAtMost((info.size / frameBytes).toLong()).toInt() - skipFrames
                        if (keepFrames > 0) {
                            val buffer = codec.getOutputBuffer(output)!!
                            buffer.position(info.offset + skipFrames * frameBytes)
                            val part = ByteArray(keepFrames * frameBytes)
                            buffer.get(part)
                            if (firstMs == null) firstMs = info.presentationTimeUs / 1000.0 + skipFrames * 1000.0 / rate
                            bytes.write(part)
                        }
                        outputEnded = info.presentationTimeUs >= endUs || info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(output, false)
                    }
                    else -> idle++
                }
            }
            if (!closed && firstMs != null && bytes.size() >= channels * 4) {
                val raw = bytes.toByteArray()
                val samples = ShortArray(raw.size / 2)
                java.nio.ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)
                window = Window(samples, rate, channels, firstMs)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Unsupported/protected media retains silent timeline scrubbing and normal playback.
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            extractor.release()
        }
    }

    fun begin(positionMs: Long): Boolean {
        val pcm = window ?: return false
        if (closed || !pcm.contains(positionMs) || active.get()) return false
        targetMs = positionMs.toDouble()
        active.set(true)
        val running = AtomicBoolean(true)
        session = running
        worker = Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
            var track: AudioTrack? = null
            try {
                val mask = if (pcm.channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
                track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(pcm.rate).setChannelMask(mask)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(AudioTrack.getMinBufferSize(pcm.rate, mask, AudioFormat.ENCODING_PCM_16BIT)
                        .coerceAtLeast(1024 * pcm.channels))
                    .setTransferMode(AudioTrack.MODE_STREAM).build()
                track.setBufferSizeInFrames((pcm.rate / 100).coerceAtLeast(256))
                track.setVolume(outputVolume.coerceIn(0f, 1f))
                track.play()
                var audioWindow = pcm
                var cursor = audioWindow.frame(positionMs.toDouble())
                var speed = 0.0
                var gain = 0.0
                val block = ShortArray(256 * audioWindow.channels)
                while (running.get() && !closed) {
                    val fresh = window
                    if (fresh != null && fresh !== audioWindow && fresh.rate == audioWindow.rate && fresh.channels == audioWindow.channels) {
                        val absoluteMs = audioWindow.startMs + cursor * 1000.0 / audioWindow.rate
                        audioWindow = fresh
                        cursor = audioWindow.frame(absoluteMs).coerceIn(0.0, (audioWindow.frames - 2).toDouble())
                    }
                    val target = audioWindow.frame(targetMs).coerceIn(0.0, (audioWindow.frames - 2).toDouble())
                    val desired = ((target - cursor) / (audioWindow.rate * 0.012)).coerceIn(-64.0, 64.0)
                    for (frame in 0 until 256) {
                        speed += (desired - speed) * 0.015
                        gain += ((abs(speed) / 0.08).coerceIn(0.0, 1.0) - gain) * 0.012
                        val index = cursor.toInt().coerceIn(0, audioWindow.frames - 2)
                        val fraction = (cursor - index).coerceIn(0.0, 1.0)
                        for (channel in 0 until audioWindow.channels) {
                            val a = audioWindow.samples[index * audioWindow.channels + channel]
                            val b = audioWindow.samples[(index + 1) * audioWindow.channels + channel]
                            block[frame * audioWindow.channels + channel] = ((a + (b - a) * fraction) * gain).toInt()
                                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                        }
                        cursor = (cursor + speed).coerceIn(0.0, (audioWindow.frames - 2).toDouble())
                    }
                    onPcmBlock?.invoke(block.copyOf())
                    if (track.write(block, 0, block.size, AudioTrack.WRITE_BLOCKING) < 0) break
                }
            } catch (_: Exception) {
                // Audio route changes may invalidate the preview track.
            } finally {
                runCatching { track?.pause() }
                runCatching { track?.flush() }
                runCatching { track?.release() }
            }
        }, "VinylScratch").apply { start() }
        return true
    }

    fun move(positionMs: Long) { targetMs = positionMs.toDouble() }
    fun constrain(positionMs: Long): Long {
        if (!active.get()) return positionMs
        val pcm = window ?: return positionMs
        return positionMs.coerceIn(pcm.startMs.toLong().coerceAtLeast(0),
            (pcm.startMs + (pcm.frames - 2) * 1000.0 / pcm.rate).toLong().coerceAtLeast(0))
    }
    fun end() {
        session?.set(false)
        active.set(false)
        worker?.join(150)
        worker = null
    }
    override fun close() { closed = true; end(); window = null }
}
