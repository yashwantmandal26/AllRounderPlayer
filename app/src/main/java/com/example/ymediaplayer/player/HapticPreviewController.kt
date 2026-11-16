@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ymediaplayer.player

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

internal data class HapticPreviewState(
    val busy: Boolean = false, val startedAtMs: Long = 0,
    val signal: HapticSignal? = null, val message: String? = null
)

/** Explicit, finite previews. The optional sound cue uses the same AudioSink clock as movies. */
internal class HapticPreviewController(
    context: Context,
    private val onState: (HapticPreviewState) -> Unit,
    private val output: CinemaHapticOutput = AndroidCinemaHapticOutput(context),
    private val touchOutput: CinemaHapticOutput = AndroidCinemaHapticOutput(context, touchFeedback = true)
) {
    private val context = context.applicationContext
    val available get() = output.available
    val supportsTextures get() = output.supportsTextures
    private val lock = Any()
    private val main = Handler(Looper.getMainLooper())
    private var worker: HandlerThread? = null
    private var handler: Handler? = null
    @Volatile private var generation = 0L
    private var closed = false
    private var ownsOutput = false
    private var lastPlayedMs = Long.MIN_VALUE / 2
    private val budget = HapticTimeline()
    private var sink: AudioSink? = null

    private fun state(token: Long, value: HapticPreviewState) {
        main.post { if (generation == token && !closed) onState(value) }
    }
    private fun releaseAudio() {
        val current = sink; sink = null
        try { current?.reset(); current?.release() } catch (_: RuntimeException) {}
    }

    fun preview(signal: HapticSignal, soundCue: Boolean = false, offsetMs: Long = 0, live: Boolean = false, touch: Boolean = false) {
        synchronized(lock) {
            if (closed) return
            val token = ++generation
            if (worker == null) {
                worker = HandlerThread("HapticPreview").apply { start() }
                handler = Handler(worker!!.looper)
            }
            val scheduler = handler!!
            scheduler.removeCallbacksAndMessages(null)
            scheduler.postAtFrontOfQueue { releaseAudio() }
            if (!live && ownsOutput) { output.cancel(); ownsOutput = false }
            state(token, HapticPreviewState(busy = true, signal = signal))
            val delay = if (live) (160 - (SystemClock.elapsedRealtime() - lastPlayedMs)).coerceAtLeast(0) else 0
            scheduler.postDelayed({
                if (token != generation || closed) return@postDelayed
                if (!available || signal.amplitudes.all { it == 0 }) {
                    state(token, HapticPreviewState(message = if (!available) "Vibration unavailable" else "This sample is disabled by the profile"))
                    return@postDelayed
                }
                if (soundCue) startAudioCue(token, signal, offsetMs, touch) else playNow(token, signal, touch)
            }, delay)
        }
    }

    private fun deliver(token: Long, signal: HapticSignal, touch: Boolean): HapticSignal? = synchronized(lock) {
        if (closed || token != generation) return@synchronized null
        val now = SystemClock.elapsedRealtime()
        budget.invalidate()
        budget.offer(0, signal, budget.generation)
        val allowed = budget.dueSignal(0, 1f, now) ?: return@synchronized null
        (if (touch) touchOutput else output).play(allowed)
        ownsOutput = true; lastPlayedMs = now
        state(token, HapticPreviewState(true, now, allowed))
        allowed
    }

    private fun playNow(token: Long, signal: HapticSignal, touch: Boolean) {
        val allowed = deliver(token, signal, touch)
        if (allowed == null) {
            state(token, HapticPreviewState(message = "Pause briefly before another strong preview")); return
        }
        handler?.postDelayed({
            if (token == generation) {
                synchronized(lock) { ownsOutput = false }
                state(token, HapticPreviewState(signal = allowed))
            }
        }, allowed.durationMs)
    }

    private fun startAudioCue(token: Long, signal: HapticSignal, offsetMs: Long, touch: Boolean) {
        try {
            val audio = DefaultAudioSink.Builder(context).build()
            sink = audio
            audio.setListener(object : AudioSink.Listener {
                override fun onPositionDiscontinuity() {}
                override fun onUnderrun(bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) {}
                override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) {}
            })
            val format = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW)
                .setSampleRate(48000).setChannelCount(2).setPcmEncoding(C.ENCODING_PCM_16BIT).build()
            audio.configure(AudioSink.AudioSinkConfig.Builder(format).build())
            audio.setVolume(0.35f)
            val pcm = ByteBuffer.allocateDirect(48000 * 2 * 4).order(ByteOrder.LITTLE_ENDIAN)
            repeat(96000) { frame ->
                val local = frame - 14400
                val sample = if (local in 0 until 1680) {
                    val fade = minOf(local / 144.0, (1680 - local) / 144.0, 1.0)
                    (sin(2 * PI * 880 * local / 48000) * fade * 6000).toInt().toShort()
                } else 0.toShort()
                pcm.putShort(sample); pcm.putShort(sample)
            }
            pcm.flip(); audio.play()
            val deadline = SystemClock.elapsedRealtime() + 5000
            var consumed = false
            var played = false
            val targetUs = (300 + offsetMs.coerceIn(-250, 250)) * 1000
            val step = object : Runnable {
                override fun run() {
                    if (token != generation || closed) return
                    try {
                        if (!consumed) {
                            consumed = audio.handleBuffer(pcm, 0, 1)
                            if (consumed) audio.playToEndOfStream()
                        }
                        val position = audio.getCurrentPositionUs(consumed)
                        if (!played && position != AudioSink.CURRENT_POSITION_NOT_SET && position >= targetUs) {
                            deliver(token, signal, touch); played = true
                        }
                        if (SystemClock.elapsedRealtime() >= deadline || consumed && audio.isEnded) {
                            synchronized(lock) { ownsOutput = false }
                            releaseAudio(); state(token, HapticPreviewState(signal = signal)); return
                        }
                        handler?.postDelayed(this, 5)
                    } catch (_: Exception) {
                        releaseAudio(); state(token, HapticPreviewState(message = "Sound cue unavailable"))
                    }
                }
            }
            handler?.post(step)
        } catch (_: Exception) {
            releaseAudio(); state(token, HapticPreviewState(message = "Sound cue unavailable"))
        }
    }

    fun stop() {
        synchronized(lock) {
            val token = ++generation
            handler?.removeCallbacksAndMessages(null)
            if (ownsOutput) { output.cancel(); ownsOutput = false }
            handler?.postAtFrontOfQueue { releaseAudio() }
            state(token, HapticPreviewState())
        }
    }
    fun close() {
        synchronized(lock) {
            if (closed) return
            stop(); closed = true
            main.removeCallbacksAndMessages(null)
            worker?.quitSafely(); worker = null; handler = null
        }
    }
}
