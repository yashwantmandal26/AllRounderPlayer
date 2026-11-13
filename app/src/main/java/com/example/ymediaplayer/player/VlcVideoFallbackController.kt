@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ymediaplayer.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.media3.ui.AspectRatioFrameLayout
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer as VlcMediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File

/**
 * In-screen LibVLC + FFmpeg fallback controller embedded inside VideoPlayerScreen so
 * partially corrupted files or exotic codecs keep every gesture, pinch-to-zoom, speed control,
 * PiP, night mode, and notification control intact instead of ejecting to a bare activity.
 */
class VlcVideoFallbackController {
    val isActive = mutableStateOf(false)
    val isPlaying = mutableStateOf(false)
    val positionMs = mutableLongStateOf(0L)
    val durationMs = mutableLongStateOf(0L)
    val videoWidth = mutableIntStateOf(0)
    val videoHeight = mutableIntStateOf(0)
    val hasEnded = mutableStateOf(false)
    val statusNote = mutableStateOf<String?>(null)

    private val mainHandler = Handler(Looper.getMainLooper())
    private var libVlc: LibVLC? = null
    private var vlcPlayer: VlcMediaPlayer? = null
    private var currentMedia: Media? = null
    private var activePfd: ParcelFileDescriptor? = null
    private var attachedLayout: VLCVideoLayout? = null

    private var activeUriString: String = ""
    private var currentSpeed: Float = 1.0f
    private var currentMuted: Boolean = false
    private var currentResizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT
    private var softwareRetryUsed: Boolean = false

    fun attachView(layout: VLCVideoLayout) {
        if (attachedLayout === layout) return
        detachView()
        attachedLayout = layout
        vlcPlayer?.let { mp ->
            runCatching {
                mp.attachViews(layout, null, true, false)
                applyResizeMode(currentResizeMode)
            }
        }
    }

    fun detachView() {
        val prev = attachedLayout
        attachedLayout = null
        if (prev != null) {
            runCatching { vlcPlayer?.detachViews() }
        }
    }

    fun start(
        context: Context,
        uriString: String,
        startPositionMs: Long,
        speed: Float,
        isMuted: Boolean,
        resizeMode: Int
    ) {
        stopAndReset(keepActiveFlag = true)
        activeUriString = uriString
        currentSpeed = speed.coerceIn(0.25f, 4.0f)
        currentMuted = isMuted
        currentResizeMode = resizeMode
        softwareRetryUsed = false
        hasEnded.value = false
        statusNote.value = "Compatibility engine: recovering stream"
        isActive.value = true

        launchVlcPlayback(
            context = context.applicationContext,
            uriString = uriString,
            startPositionMs = startPositionMs.coerceAtLeast(0L),
            forceSoftware = false
        )
    }

    private fun launchVlcPlayback(
        context: Context,
        uriString: String,
        startPositionMs: Long,
        forceSoftware: Boolean
    ) {
        releaseInternalObjects()
        try {
            val lib = LibVLC(
                context,
                arrayListOf(
                    "--network-caching=3000",
                    "--file-caching=1500",
                    "--live-caching=3000",
                    "--drop-late-frames",
                    "--skip-frames",
                    "--avcodec-corrupted",
                    "--avcodec-error-resilience=1",
                    "--avcodec-skiploopfilter=4",
                    "--no-video-title-show"
                )
            )
            libVlc = lib
            val mp = VlcMediaPlayer(lib)
            vlcPlayer = mp

            attachedLayout?.let { layout ->
                runCatching { mp.attachViews(layout, null, true, false) }
            }

            val parsedUri = if (uriString.startsWith("/")) {
                Uri.fromFile(File(uriString))
            } else {
                Uri.parse(uriString)
            }
            val pfd = if (parsedUri.scheme == "content") {
                runCatching { context.contentResolver.openFileDescriptor(parsedUri, "r") }.getOrNull()
            } else null
            activePfd = pfd

            val media = (if (pfd != null) Media(lib, pfd.fileDescriptor) else Media(lib, parsedUri)).apply {
                setHWDecoderEnabled(!forceSoftware, false)
                addOption(":network-caching=3000")
                addOption(":file-caching=1500")
                addOption(":drop-late-frames")
                addOption(":skip-frames")
                addOption(":avcodec-corrupted")
            }
            currentMedia = media
            mp.media = media
            mp.rate = currentSpeed
            mp.volume = if (currentMuted) 0 else 100
            applyResizeMode(currentResizeMode)

            var appliedStartSeek = startPositionMs <= 0L
            positionMs.longValue = startPositionMs

            mp.setEventListener { event ->
                mainHandler.post {
                    if (vlcPlayer !== mp) return@post
                    when (event.type) {
                        VlcMediaPlayer.Event.Playing -> {
                            isPlaying.value = true
                            hasEnded.value = false
                            VideoPlaybackManager.isPlaying.value = true
                            statusNote.value = if (softwareRetryUsed) {
                                "Software recovery active"
                            } else null
                            val len = mp.length
                            if (len > 0L) {
                                durationMs.longValue = len
                                VideoPlaybackManager.duration.value = len
                            }
                            if (!appliedStartSeek && startPositionMs > 0L && mp.isSeekable) {
                                appliedStartSeek = true
                                mp.time = startPositionMs
                            }
                            refreshVideoDimensions(mp)
                        }
                        VlcMediaPlayer.Event.Paused -> {
                            isPlaying.value = false
                            VideoPlaybackManager.isPlaying.value = false
                        }
                        VlcMediaPlayer.Event.TimeChanged -> {
                            val t = mp.time
                            if (t >= 0L) {
                                positionMs.longValue = t
                                VideoPlaybackManager.currentPosition.value = t
                            }
                        }
                        VlcMediaPlayer.Event.LengthChanged -> {
                            val len = mp.length
                            if (len > 0L) {
                                durationMs.longValue = len
                                VideoPlaybackManager.duration.value = len
                            }
                        }
                        VlcMediaPlayer.Event.Vout -> {
                            refreshVideoDimensions(mp)
                        }
                        VlcMediaPlayer.Event.EndReached -> {
                            isPlaying.value = false
                            hasEnded.value = true
                            VideoPlaybackManager.isPlaying.value = false
                        }
                        VlcMediaPlayer.Event.EncounteredError -> {
                            if (!softwareRetryUsed) {
                                softwareRetryUsed = true
                                val resumeAt = positionMs.longValue.coerceAtLeast(startPositionMs)
                                statusNote.value = "Hardware decoder failed; retrying software recovery"
                                launchVlcPlayback(
                                    context = context,
                                    uriString = uriString,
                                    startPositionMs = resumeAt,
                                    forceSoftware = true
                                )
                            } else {
                                isPlaying.value = false
                                VideoPlaybackManager.isPlaying.value = false
                                statusNote.value = "Readable frames finished; file may be truncated"
                            }
                        }
                    }
                }
            }

            mp.play()
        } catch (t: Throwable) {
            Log.e("VlcFallback", "Failed to start in-screen VLC fallback", t)
            isPlaying.value = false
            VideoPlaybackManager.isPlaying.value = false
            statusNote.value = "Could not open damaged stream"
        }
    }

    private fun refreshVideoDimensions(mp: VlcMediaPlayer) {
        val track = runCatching { mp.currentVideoTrack }.getOrNull() ?: return
        val w = track.width
        val h = track.height
        if (w > 0 && h > 0) {
            videoWidth.intValue = w
            videoHeight.intValue = h
            VideoPlaybackManager.videoWidth.value = w
            VideoPlaybackManager.videoHeight.value = h
        }
    }

    fun play() {
        val mp = vlcPlayer ?: return
        hasEnded.value = false
        mp.volume = if (currentMuted) 0 else 100
        mp.rate = currentSpeed
        mp.play()
        isPlaying.value = true
        VideoPlaybackManager.isPlaying.value = true
    }

    fun pause() {
        val mp = vlcPlayer ?: return
        mp.pause()
        isPlaying.value = false
        VideoPlaybackManager.isPlaying.value = false
    }

    fun seekTo(targetMs: Long) {
        val mp = vlcPlayer ?: return
        val dur = durationMs.longValue
        val clamped = if (dur > 0L) targetMs.coerceIn(0L, dur) else targetMs.coerceAtLeast(0L)
        if (mp.isSeekable) {
            mp.time = clamped
        }
        positionMs.longValue = clamped
        VideoPlaybackManager.currentPosition.value = clamped
    }

    fun setSpeed(speed: Float) {
        currentSpeed = speed.coerceIn(0.25f, 4.0f)
        vlcPlayer?.rate = currentSpeed
    }

    fun setMuted(muted: Boolean) {
        currentMuted = muted
        vlcPlayer?.volume = if (muted) 0 else 100
    }

    fun applyResizeMode(resizeMode: Int) {
        currentResizeMode = resizeMode
        val mp = vlcPlayer ?: return
        mp.videoScale = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> VlcMediaPlayer.ScaleType.SURFACE_FILL
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> VlcMediaPlayer.ScaleType.SURFACE_FIT_SCREEN
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH -> VlcMediaPlayer.ScaleType.SURFACE_ORIGINAL
            else -> VlcMediaPlayer.ScaleType.SURFACE_BEST_FIT
        }
    }

    private fun releaseInternalObjects() {
        val mp = vlcPlayer
        vlcPlayer = null
        if (mp != null) {
            runCatching { mp.setEventListener(null) }
            runCatching { mp.detachViews() }
            runCatching { mp.stop() }
            runCatching { mp.release() }
        }
        runCatching { currentMedia?.release() }
        currentMedia = null
        runCatching { activePfd?.close() }
        activePfd = null
        runCatching { libVlc?.release() }
        libVlc = null
    }

    fun stopAndReset(keepActiveFlag: Boolean = false) {
        mainHandler.removeCallbacksAndMessages(null)
        releaseInternalObjects()
        isPlaying.value = false
        positionMs.longValue = 0L
        durationMs.longValue = 0L
        videoWidth.intValue = 0
        videoHeight.intValue = 0
        hasEnded.value = false
        if (!keepActiveFlag) {
            isActive.value = false
            statusNote.value = null
            activeUriString = ""
        }
    }
}
