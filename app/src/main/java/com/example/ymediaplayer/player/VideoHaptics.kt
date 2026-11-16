package com.example.ymediaplayer.player

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.media.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.example.ymediaplayer.data.AppPreferences

internal interface CinemaHapticOutput {
    val available: Boolean
    val supportsTextures: Boolean get() = true
    fun prepare() {}
    fun play(impact: CinemaImpact)
    fun play(signal: HapticSignal) { play(signal.impact) }
    fun cancel()
}

internal class AndroidCinemaHapticOutput(context: Context, private val touchFeedback: Boolean = false) : CinemaHapticOutput {
    private val vibrator = context.getSystemService(Vibrator::class.java)
    override val available = try { vibrator?.hasVibrator() == true } catch (_: RuntimeException) { false }
    override val supportsTextures = available && Build.VERSION.SDK_INT >= 26 &&
        try { vibrator?.hasAmplitudeControl() == true } catch (_: RuntimeException) { false }
    private val audioAttributes = AudioAttributes.Builder().setUsage(if (touchFeedback) AudioAttributes.USAGE_ASSISTANCE_SONIFICATION else AudioAttributes.USAGE_MEDIA).build()
    private val vibrationAttributes = if (Build.VERSION.SDK_INT >= 33) {
        VibrationAttributes.Builder().setUsage(if (touchFeedback) VibrationAttributes.USAGE_TOUCH else VibrationAttributes.USAGE_MEDIA).build()
    } else null
    // Lazy initialization happens on the haptics worker. No capability queries or effect allocations per hit.
    private val effects by lazy { CinemaImpact.entries.associateWith(::createEffect) }
    override fun prepare() { if (available && !supportsTextures && Build.VERSION.SDK_INT >= 26) effects }

    private fun createEffect(impact: CinemaImpact): VibrationEffect? {
        if (!available || Build.VERSION.SDK_INT < 26) return null
        return try {
            if (Build.VERSION.SDK_INT >= 31) {
                val primitive = when (impact) {
                    CinemaImpact.TICK -> VibrationEffect.Composition.PRIMITIVE_LOW_TICK
                    CinemaImpact.TAP -> VibrationEffect.Composition.PRIMITIVE_CLICK
                    else -> VibrationEffect.Composition.PRIMITIVE_THUD
                }
                if (vibrator!!.arePrimitivesSupported(primitive).all { it } &&
                    vibrator.getPrimitiveDurations(primitive)[0] in 1..impact.durationMs) {
                    return VibrationEffect.startComposition().addPrimitive(primitive, impact.scale).compose()
                }
            }
            if (vibrator!!.hasAmplitudeControl()) {
                val peak = (255 * impact.scale).toInt().coerceIn(1, 255)
                // Fast attack, body, and a short release rather than an abrupt rectangular buzz.
                VibrationEffect.createWaveform(
                    longArrayOf(2, impact.durationMs - 8, 4, 2),
                    intArrayOf(peak / 2, peak, peak / 2, 0), -1
                )
            } else VibrationEffect.createOneShot(impact.durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
        } catch (_: RuntimeException) {
            VibrationEffect.createOneShot(impact.durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
        }
    }

    override fun play(signal: HapticSignal) {
        if (!available) return
        if (Build.VERSION.SDK_INT < 26 || !signal.scalable || !supportsTextures) {
            if (!signal.texture) play(signal.impact)
            return
        }
        try {
            val effect = VibrationEffect.createWaveform(signal.timingsMs, signal.amplitudes, -1)
            if (Build.VERSION.SDK_INT >= 33) vibrator!!.vibrate(effect, vibrationAttributes!!)
            else vibrator!!.vibrate(effect, audioAttributes)
        } catch (_: RuntimeException) { /* Keep audio uninterrupted on hardware failure. */ }
    }

    override fun play(impact: CinemaImpact) {
        if (!available) return
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                val effect = effects[impact] ?: return
                if (Build.VERSION.SDK_INT >= 33) {
                    vibrator!!.vibrate(effect, vibrationAttributes!!)
                } else {
                    vibrator!!.vibrate(effect, audioAttributes)
                }
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(impact.durationMs, audioAttributes)
            }
        } catch (_: RuntimeException) { /* Hardware/policy failure must never affect playback. */ }
    }
    override fun cancel() { try { vibrator?.cancel() } catch (_: RuntimeException) {} }
}

/** Player-owned worker keeps haptic deadlines and vibrator IPC independent of UI frames. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class VideoHaptics(
    context: Context,
    private val output: CinemaHapticOutput = AndroidCinemaHapticOutput(context),
    private val musicMode: Boolean = false
) {
    val available get() = output.available
    val supportsTextures get() = output.supportsTextures
    val timeline = HapticTimeline(impactCooldownMs = if (musicMode) 100L else 180L)
    private val handler = Handler(Looper.getMainLooper())
    private val stateLock = Any()
    private var worker: HandlerThread? = null
    @Volatile private var scheduler: Handler? = null
    @Volatile var active = false
        private set
    private var enabled = false
    private var playing = false
    private var formatSupported = false
    private var released = false
    @Volatile var tuning = HapticTuning()
        private set
    private var previewSuppressed = false
    @Volatile private var clock: Clock? = null
    private data class Clock(val positionUs: Long, val elapsedMs: Long, val speed: Float, val token: Long)
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val preferences = context.getSharedPreferences("ymedia_prefs", Context.MODE_PRIVATE)?.let(::AppPreferences)
    @Volatile private var loadedRoute: String? = null
    private var lastRouteCheckMs = 0L
    private fun reloadTuning(route: String = currentHapticRoute(audioManager)) {
        loadedRoute = route
        if (musicMode) applyTuning(preferences?.getMusicHapticTuning(route) ?: HapticTuning(syncMs = -20L, textures = false))
        else preferences?.let { applyTuning(it.getVideoHapticTuning(route)) }
    }
    private val routeCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) { reloadTuning(); invalidate() }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) { reloadTuning(); invalidate() }
    }
    private val tick = object : Runnable {
        override fun run() {
            val queryTime = SystemClock.elapsedRealtime()
            val route = if (active && queryTime - lastRouteCheckMs >= 1000) {
                lastRouteCheckMs = queryTime
                currentHapticRoute(audioManager)
            } else null
            synchronized(stateLock) {
                if (!active || released) return
                val now = SystemClock.elapsedRealtime()
                if (route != null && route != loadedRoute) {
                    reloadTuning(route); invalidate(); return
                }
                var nextDelayMs = 40L
                clock?.let {
                    if (it.token == timeline.generation && now - it.elapsedMs in 0..100) {
                        val position = extrapolate(it.positionUs, now - it.elapsedMs, it.speed) -
                            (tuning.syncMs * 1000 * it.speed.toDouble()).toLong()
                        timeline.dueSignal(position, it.speed, now, tuning = tuning)?.let { signal ->
                            if (it.token == timeline.generation) output.play(signal)
                        }
                        nextDelayMs = timeline.nextDelayMs(position, it.speed)
                    }
                }
                if (timeline.hasPendingEvents()) scheduler?.postDelayed(this, nextDelayMs)
            }
        }
    }
    init { reloadTuning(); audioManager?.registerAudioDeviceCallback(routeCallback, handler) }

    fun setEnabled(value: Boolean) { if (enabled != value) { enabled = value; update() } }
    fun reloadSavedTuning() { reloadTuning() }
    fun setTuning(strength: Float, offsetMs: Long) {
        applyTuning(tuning.copy(strength = strength, syncMs = offsetMs))
    }
    fun setPreviewSuppressed(value: Boolean) {
        if (previewSuppressed != value) { previewSuppressed = value; update() }
    }
    fun applyTuningForRoute(route: String, value: HapticTuning) {
        if (route == loadedRoute) applyTuning(value)
    }
    fun applyTuning(value: HapticTuning) {
        synchronized(stateLock) {
            val next = value.bounded()
            val changedOffset = tuning.syncMs != next.syncMs
            tuning = next
            update()
            if (changedOffset) invalidate()
        }
    }
    fun setPlaying(value: Boolean) { if (playing != value) { playing = value; update() } }
    private fun update() {
        synchronized(stateLock) {
            val shouldRun = !released && enabled && playing && !previewSuppressed && available && formatSupported &&
                (tuning.impacts && tuning.impactStrength > 0 || tuning.textures && tuning.textureStrength > 0 && supportsTextures)
            if (active == shouldRun) return
            active = shouldRun
            invalidate()
        }
    }
    fun invalidate() {
        synchronized(stateLock) {
            timeline.invalidate(); clock = null
            scheduler?.removeCallbacks(tick)
            output.cancel()
            if (active && !released) {
                if (scheduler == null) {
                    worker = HandlerThread("CinemaHaptics").apply { start() }
                    scheduler = Handler(worker!!.looper)
                    scheduler?.post { output.prepare() }
                }
                scheduler?.post(tick)
            }
        }
    }
    fun onImpactQueued() {
        // Never wait for vibrator IPC on the decoder/audio thread.
        scheduler?.post {
            scheduler?.removeCallbacks(tick)
            tick.run()
        }
    }
    fun publishPosition(positionUs: Long, speed: Float, token: Long) {
        if (active && token == timeline.generation && positionUs != AudioSink.CURRENT_POSITION_NOT_SET) {
            val now = SystemClock.elapsedRealtime()
            val previous = clock
            if (previous == null || previous.token != token || now - previous.elapsedMs >= 5) {
                clock = Clock(positionUs, now, speed, token)
            }
        }
    }
    fun setFormatSupported(supported: Boolean) {
        handler.post {
            if (!released && formatSupported != supported) {
                formatSupported = supported
                update()
            }
        }
    }
    fun release() {
        synchronized(stateLock) {
            if (released) return
            released = true
            active = false; enabled = false
            invalidate()
            worker?.quitSafely()
            scheduler = null; worker = null
        }
        audioManager?.unregisterAudioDeviceCallback(routeCallback)
    }

    companion object {
        internal fun extrapolate(positionUs: Long, elapsedMs: Long, speed: Float): Long =
            positionUs + (elapsedMs * 1000 * speed.toDouble()).toLong()
    }
}

/** Observe input PCM before the existing dynamics processor; the delegate retains all audio behavior.
 * Sink PTS and getCurrentPositionUs share the renderer's time domain, including stream offsets.
 * Passthrough/offload compressed input is forwarded without analysis or route changes.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class HapticAudioSink(
    delegate: AudioSink,
    private val haptics: VideoHaptics,
    musicMode: Boolean = false
) : ForwardingAudioSink(delegate) {
    private val detector: PcmHapticDetector = if (musicMode) MusicHapticDetector() else HapticDetector()
    private var sampleRate = 0
    private var channels = 0
    private var encoding = C.ENCODING_INVALID
    private var pendingBuffer = false
    private var token = -1L

    override fun configure(config: AudioSink.AudioSinkConfig) {
        super.configure(config)
        haptics.timeline.invalidate()
        val inputFormat = config.format
        sampleRate = inputFormat.sampleRate
        channels = inputFormat.channelCount
        encoding = if (inputFormat.sampleMimeType == MimeTypes.AUDIO_RAW &&
            sampleRate > 0 && channels in 1..8) inputFormat.pcmEncoding else C.ENCODING_INVALID
        haptics.setFormatSupported(encoding == C.ENCODING_PCM_16BIT || encoding == C.ENCODING_PCM_FLOAT)
        if (encoding == C.ENCODING_PCM_16BIT || encoding == C.ENCODING_PCM_FLOAT) {
            detector.configure(sampleRate, channels)
        }
        pendingBuffer = false
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        // Media3 retries the same buffer until consumed. Analyze once, without retaining its reference.
        if (!pendingBuffer && buffer.hasRemaining() && haptics.active &&
            (encoding == C.ENCODING_PCM_16BIT || encoding == C.ENCODING_PCM_FLOAT)) {
            val generation = haptics.timeline.generation
            if (token != generation) { detector.reset(); token = generation }
            detector.setSensitivity(haptics.tuning.sensitivity)
            val bytes = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
            val start = buffer.position()
            val order = buffer.order()
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            // Absolute reads leave the caller's position untouched, including delegate retries.
            var pos = start
            var frame = 0L
            while (pos + channels * bytes <= buffer.limit()) {
                // Only the start of each analysis window needs a timestamp (200 divisions/sec).
                val time = if (detector.needsTimestamp) presentationTimeUs + frame * 1_000_000 / sampleRate else 0L
                for (channel in 0 until channels) {
                    val value = if (bytes == 4) buffer.getFloat(pos).toDouble() else buffer.getShort(pos) / 32768.0
                    detector.sample(value, channel, time)?.let {
                        val signal = detector.signal ?: return@let
                        if ((!signal.texture || haptics.supportsTextures) &&
                            haptics.timeline.offer(detector.impactTimeUs, signal, generation)) haptics.onImpactQueued()
                    }
                    pos += bytes
                }
                frame++
            }
            buffer.order(order)
        }
        val consumed = super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        pendingBuffer = !consumed
        return consumed
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val generation = haptics.timeline.generation
        val position = super.getCurrentPositionUs(sourceEnded)
        haptics.publishPosition(position, playbackParameters.speed, generation)
        return position
    }
    override fun flush() {
        haptics.timeline.invalidate()
        pendingBuffer = false; detector.reset()
        super.flush()
    }
    override fun handleDiscontinuity() {
        haptics.timeline.invalidate()
        detector.reset()
        super.handleDiscontinuity()
    }
    override fun reset() {
        haptics.setFormatSupported(false)
        haptics.timeline.invalidate()
        pendingBuffer = false; detector.reset()
        super.reset()
    }
}
