@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ymediaplayer.player

import android.content.Context
import android.app.ActivityManager
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import com.example.ymediaplayer.data.AppPreferences
import com.example.ymediaplayer.data.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.ArrayDeque

/**
 * Singleton managing shared video playback across the full-screen [VideoPlayerScreen]
 * and the floating YouTube-style [InAppMiniPlayer].
 */
object VideoPlaybackManager {
    data class CompatibilityFallbackRequest(
        val uri: String,
        val title: String,
        val positionMs: Long,
        val reason: String,
        val requestId: Long = SystemClock.elapsedRealtime()
    )

    data class PlaybackDiagnostic(
        val timestampMs: Long,
        val event: String,
        val details: String
    )

    var player: ExoPlayer? = null
        private set

    val isMiniPlayerActive: MutableState<Boolean> = mutableStateOf(false)
    val currentVideoUrl: MutableState<String?> = mutableStateOf(null)
    val currentVideoTitle: MutableState<String?> = mutableStateOf(null)
    val currentPosterUrl: MutableState<String?> = mutableStateOf(null)
    val isPlaying: MutableState<Boolean> = mutableStateOf(false)
    val currentPosition: MutableState<Long> = mutableLongStateOf(0L)
    val duration: MutableState<Long> = mutableLongStateOf(0L)
    val videoWidth: MutableState<Int> = mutableIntStateOf(0)
    val videoHeight: MutableState<Int> = mutableIntStateOf(0)
    val currentAudioSessionId: MutableState<Int> = mutableIntStateOf(0)
    val safeModeActive: MutableState<Boolean> = mutableStateOf(false)
    val recoveryMessage: MutableState<String?> = mutableStateOf(null)
    val compatibilityFallbackRequest: MutableState<CompatibilityFallbackRequest?> = mutableStateOf(null)

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recoveryJob: Job? = null
    private var recoveryAttempt = 0
    private var lastReadyAtMs = 0L
    private var configuredSubtitlesEnabled = true
    private val diagnostics = ArrayDeque<PlaybackDiagnostic>(MAX_DIAGNOSTICS)

    var onPlayNextAction: (() -> Unit)? = null
    var onPlayPrevAction: (() -> Unit)? = null

    var isPausedByCall = false
        private set
    var isManuallyResumedDuringCall = false
        private set
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Incoming call or transient system interruption: pause if playing
                if (!isManuallyResumedDuringCall) {
                    player?.let { p ->
                        if (p.isPlaying) {
                            isPausedByCall = true
                            p.pause()
                        }
                    }
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                if (!isManuallyResumedDuringCall) {
                    player?.let { p ->
                        if (p.isPlaying) {
                            isPausedByCall = true
                            p.pause()
                        }
                    }
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Call finished or focus regained
                if (isPausedByCall) {
                    isPausedByCall = false
                    isManuallyResumedDuringCall = false
                    player?.play()
                }
            }
        }
    }

    private fun requestVideoAudioFocus(context: Context) {
        val am = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audioManager = am
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val aAttr = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(aAttr)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            audioFocusRequest = req
            am.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun abandonVideoAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { req -> am.abandonAudioFocusRequest(req) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(audioFocusChangeListener)
        }
        audioManager = null
        isPausedByCall = false
        isManuallyResumedDuringCall = false
    }

    fun manualPlay() {
        isManuallyResumedDuringCall = true
        isPausedByCall = false
        val p = player ?: return
        if (p.playbackState == Player.STATE_ENDED) {
            p.seekTo(0L)
        }
        p.play()
        isPlaying.value = true
    }

    fun manualPause() {
        val p = player ?: return
        p.pause()
        p.playWhenReady = false
        isPausedByCall = false
        isManuallyResumedDuringCall = false
        isPlaying.value = false
    }

    private var playerListener: Player.Listener? = null
    private var dynamicsProcessor: VideoDynamicsProcessor? = null
    var activePlaylistQueue: List<VideoItem>? = null
    var activeStreamingHash: String? = null

    fun setAudioDynamics(nightStrength: Float, leveling: Boolean) {
        dynamicsProcessor?.settings = VideoDynamicsProcessor.Settings(nightStrength.coerceIn(0f, 1f), leveling)
    }


    fun consumeCompatibilityFallback(requestId: Long) {
        if (compatibilityFallbackRequest.value?.requestId == requestId) {
            compatibilityFallbackRequest.value = null
        }
    }

    @Synchronized
    fun getDiagnosticsSnapshot(): List<PlaybackDiagnostic> = diagnostics.toList()

    private data class BufferProfile(
        val minBufferMs: Int,
        val maxBufferMs: Int,
        val playbackBufferMs: Int,
        val rebufferMs: Int,
        val backBufferMs: Int,
        val targetBytes: Int
    )

    private fun bufferProfile(context: Context): BufferProfile {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryClass = manager?.memoryClass ?: 128
        return when {
            manager?.isLowRamDevice == true || memoryClass <= 128 -> BufferProfile(
                minBufferMs = 4_000,
                maxBufferMs = 15_000,
                playbackBufferMs = 250,
                rebufferMs = 1_000,
                backBufferMs = 2_000,
                targetBytes = 24 * 1024 * 1024
            )
            memoryClass <= 256 -> BufferProfile(
                minBufferMs = 6_000,
                maxBufferMs = 25_000,
                playbackBufferMs = 250,
                rebufferMs = 1_000,
                backBufferMs = 4_000,
                targetBytes = 48 * 1024 * 1024
            )
            else -> BufferProfile(
                minBufferMs = 10_000,
                maxBufferMs = 35_000,
                playbackBufferMs = 250,
                rebufferMs = 1_000,
                backBufferMs = 8_000,
                targetBytes = 96 * 1024 * 1024
            )
        }
    }

    private fun buildMediaSourceFactory(context: Context): DefaultMediaSourceFactory {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("AllRounderMediaPlayer/1.5.5")
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)
        val defaultDataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        val dataSourceFactory = com.example.ymediaplayer.mycinema.service.TorrentAwareDataSourceFactory(context, defaultDataSourceFactory)
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setConstantBitrateSeekingAlwaysEnabled(true)
            .setTsExtractorTimestampSearchBytes(5 * 1024 * 1024)
            .setMatroskaExtractorFlags(androidx.media3.extractor.mkv.MatroskaExtractor.FLAG_DISABLE_SEEK_FOR_CUES)
        return DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(5))
    }

    @Synchronized
    private fun recordDiagnostic(context: Context, event: String, details: String) {
        val entry = PlaybackDiagnostic(System.currentTimeMillis(), event, details.take(1_000))
        if (diagnostics.size >= MAX_DIAGNOSTICS) diagnostics.removeFirst()
        diagnostics.addLast(entry)
        Log.i(TAG, "$event: ${entry.details}")
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val file = File(context.filesDir, DIAGNOSTICS_FILE)
                if (file.exists() && file.length() > MAX_DIAGNOSTICS_FILE_BYTES) {
                    file.writeText("")
                }
                file.appendText("${entry.timestampMs}\t${entry.event}\t${entry.details.replace('\n', ' ')}\n")
            }
        }
    }

    private fun resetRecoveryForNewItem(context: Context, mediaItem: MediaItem?) {
        recoveryJob?.cancel()
        recoveryAttempt = 0
        lastReadyAtMs = 0L
        if (safeModeActive.value) {
            player?.let { targetPlayer ->
                targetPlayer.setSeekParameters(SeekParameters.EXACT)
                targetPlayer.trackSelectionParameters = targetPlayer.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !configuredSubtitlesEnabled)
                    .build()
            }
        }
        safeModeActive.value = false
        recoveryMessage.value = null
        compatibilityFallbackRequest.value = null
        mediaItem?.localConfiguration?.uri?.toString()?.let { currentVideoUrl.value = it }
        mediaItem?.mediaMetadata?.title?.toString()?.takeIf { it.isNotBlank() }?.let {
            currentVideoTitle.value = it
        }
        recordDiagnostic(context, "media_item", currentVideoUrl.value.orEmpty())
    }

    private fun handlePlaybackError(
        context: Context,
        targetPlayer: ExoPlayer,
        error: PlaybackException
    ) {
        if (player !== targetPlayer) return
        val now = SystemClock.elapsedRealtime()
        if (lastReadyAtMs > 0L && now - lastReadyAtMs >= STABLE_PLAYBACK_RESET_MS) {
            recoveryAttempt = 0
        }
        val exoError = error as? ExoPlaybackException
        val audioRendererFailed = exoError?.rendererFormat?.sampleMimeType?.startsWith("audio/") == true ||
            exoError?.rendererName?.contains("audio", ignoreCase = true) == true
        val action = PlaybackRecoveryPolicy.actionFor(error.errorCode, recoveryAttempt, audioRendererFailed)
        recordDiagnostic(
            context,
            "playback_error",
            "code=${error.errorCodeName}, attempt=$recoveryAttempt, action=$action, renderer=${exoError?.rendererName}, message=${error.message}"
        )

        when (action) {
            RecoveryAction.RETRY -> scheduleRetry(context, targetPlayer, safe = false, disableAudio = false)
            RecoveryAction.SAFE_MODE_RETRY -> scheduleRetry(context, targetPlayer, safe = true, disableAudio = false)
            RecoveryAction.DISABLE_AUDIO_RETRY -> scheduleRetry(context, targetPlayer, safe = true, disableAudio = true)
            RecoveryAction.COMPATIBILITY_FALLBACK -> requestCompatibilityFallback(context, targetPlayer, error)
            RecoveryAction.FAIL -> {
                recoveryMessage.value = friendlyError(error)
                isPlaying.value = false
            }
        }
    }

    private fun scheduleRetry(
        context: Context,
        targetPlayer: ExoPlayer,
        safe: Boolean,
        disableAudio: Boolean
    ) {
        val attempt = recoveryAttempt++
        if (attempt >= 4) {
            recoveryMessage.value = "Cannot play video: this file appears to be incomplete or corrupted."
            isPlaying.value = false
            return
        }
        val resumePosition = if (attempt >= 2) 0L else targetPlayer.currentPosition.coerceAtLeast(0L)
        if (safe) {
            safeModeActive.value = true
            targetPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC)
            targetPlayer.trackSelectionParameters = targetPlayer.trackSelectionParameters.buildUpon()
                .clearOverrides()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, disableAudio)
                .build()
        }
        recoveryMessage.value = when {
            disableAudio -> "Damaged audio track skipped; continuing video"
            attempt >= 2 -> "Compatibility mode: playing from beginning"
            safe -> "Compatibility mode enabled; recovering playback"
            else -> "Playback interrupted; retrying"
        }
        recoveryJob?.cancel()
        recoveryJob = managerScope.launch {
            delay(PlaybackRecoveryPolicy.retryDelayMs(attempt))
            if (player !== targetPlayer) return@launch
            runCatching {
                targetPlayer.seekTo(resumePosition)
                targetPlayer.prepare()
                targetPlayer.playWhenReady = true
            }.onFailure {
                recordDiagnostic(context, "retry_failed", it.message ?: it.javaClass.simpleName)
            }
        }
    }

    private fun requestCompatibilityFallback(
        context: Context,
        targetPlayer: ExoPlayer,
        error: PlaybackException
    ) {
        val uri = targetPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
            ?: currentVideoUrl.value
        if (uri.isNullOrBlank()) {
            recoveryMessage.value = friendlyError(error)
            return
        }

        // Torrent streaming guard:
        // Only guard actively streaming torrents, NOT completed/downloaded offline files
        val isActivelyStreaming = (uri.contains("/torrents/", ignoreCase = true) || uri.contains("MYCinema", ignoreCase = true)) &&
            com.example.ymediaplayer.mycinema.service.TorrentEngine.getActiveTorrent(uri)?.let { hash ->
                val state = com.example.ymediaplayer.mycinema.service.TorrentEngine.downloads.value[hash]
                state != null && state.status != com.example.ymediaplayer.mycinema.service.TorrentDownloadState.Status.FINISHED && state.status != com.example.ymediaplayer.mycinema.service.TorrentDownloadState.Status.ERROR
            } == true
        if (isActivelyStreaming) {
            recoveryMessage.value = "Buffering stream... Please wait"
            scheduleRetry(context, targetPlayer, safe = true, disableAudio = false)
            return
        }

        val exoError = error as? ExoPlaybackException
        val audioFailed = exoError?.rendererFormat?.sampleMimeType?.startsWith("audio/") == true ||
            exoError?.rendererName?.contains("audio", ignoreCase = true) == true
        if (audioFailed) {
            scheduleRetry(context, targetPlayer, safe = true, disableAudio = true)
            return
        }

        // For local / downloaded files, do NOT pause or switch to compatibility player.
        // Instead, retry in safe mode to keep the user in the original player with all controls.
        val isOffline = !uri.startsWith("http://") && !uri.startsWith("https://")
        if (isOffline) {
            scheduleRetry(context, targetPlayer, safe = true, disableAudio = false)
            return
        }

        targetPlayer.pause()
        val reason = "${error.errorCodeName}: ${error.message.orEmpty()}".trim()
        recoveryMessage.value = "Opening extended compatibility player"
        compatibilityFallbackRequest.value = CompatibilityFallbackRequest(
            uri = uri,
            title = currentVideoTitle.value ?: "Video",
            positionMs = targetPlayer.currentPosition.coerceAtLeast(0L),
            reason = reason
        )
        recordDiagnostic(context, "compatibility_fallback", reason)
    }

    private fun friendlyError(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "Video file is missing or was moved"
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        PlaybackException.ERROR_CODE_PERMISSION_DENIED -> "Permission to read this video was lost"
        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> "This network stream requires a secure HTTPS address"
        PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED -> "The video's playback licence has expired"
        else -> "This video could not be recovered (${error.errorCodeName})"
    }

    fun getOrCreatePlayer(
        context: Context,
        url: String,
        title: String,
        initialProgress: Long = 0L,
        playbackSpeed: Float = 1.0f,
        repeatMode: Int = Player.REPEAT_MODE_OFF,
        isMuted: Boolean = false,
        preferredAudioLang: String = "",
        preferredSubLang: String = "",
        subtitlesEnabled: Boolean = true
    ): ExoPlayer {
        // Stop/pause background music playback immediately when video starts
        com.example.ymediaplayer.service.MusicService.pauseMusic()

        try {
            val avail = androidx.media3.decoder.ffmpeg.FfmpegLibrary.isAvailable()
            val ver = androidx.media3.decoder.ffmpeg.FfmpegLibrary.getVersion()
            val supportsEac3 = androidx.media3.decoder.ffmpeg.FfmpegLibrary.supportsFormat("audio/eac3")
            android.util.Log.i("VideoPlayback", "FFmpeg diagnostic: available=$avail, ver=$ver, supportsEac3=$supportsEac3")
        } catch (t: Throwable) {
            android.util.Log.e("VideoPlayback", "FFmpeg diagnostic failed", t)
        }

        val current = player
        if (current != null && currentVideoUrl.value == url) {
            currentVideoTitle.value = title
            isPlaying.value = current.isPlaying
            return current
        }

        // Release existing player if switching to a new video
        releasePlayer()

        val applicationContext = context.applicationContext
        configuredSubtitlesEnabled = subtitlesEnabled
        val appPreferences = AppPreferences(context)
        val handleAudioFocus = !appPreferences.isPlayDuringCallsEnabled()

        val dynamics = VideoDynamicsProcessor()
        dynamicsProcessor = dynamics
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean): androidx.media3.exoplayer.audio.AudioSink {
                return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(false)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(dynamics))
                    .build()
            }

            override fun buildAudioRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: androidx.media3.exoplayer.mediacodec.MediaCodecSelector,
                enableDecoderFallback: Boolean,
                audioSink: androidx.media3.exoplayer.audio.AudioSink,
                eventHandler: Handler,
                eventListener: androidx.media3.exoplayer.audio.AudioRendererEventListener,
                out: java.util.ArrayList<androidx.media3.exoplayer.Renderer>
            ) {
                // Priority 1: Inject FfmpegAudioRenderer for universal software codec decoding (EAC-3/DDP 5.1, AC-3, DTS, TrueHD)
                try {
                    val ffmpegRenderer = androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer(
                        eventHandler, eventListener, audioSink
                    )
                    out.add(ffmpegRenderer)
                    android.util.Log.i("VideoPlayback", "Manually injected FfmpegAudioRenderer as primary audio renderer")
                } catch (t: Throwable) {
                    android.util.Log.w("VideoPlayback", "Could not inject FfmpegAudioRenderer: ${t.message}")
                }

                // Standard hardware platform renderers (pass EXTENSION_RENDERER_MODE_OFF to avoid duplicate reflection addition)
                super.buildAudioRenderers(
                    context,
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF,
                    mediaCodecSelector,
                    enableDecoderFallback,
                    audioSink,
                    eventHandler,
                    eventListener,
                    out
                )
            }
        }.apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            setEnableDecoderFallback(true)
            setEnableAudioTrackPlaybackParams(true)
        }

        val profile = bufferProfile(applicationContext)
        // Keep enough data for high-bitrate playback while respecting low-RAM devices.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                profile.minBufferMs,
                profile.maxBufferMs,
                profile.playbackBufferMs,
                profile.rebufferMs
            )
            .setBackBuffer(profile.backBufferMs, true)
            .setTargetBufferBytes(profile.targetBytes)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()

        val newPlayer = ExoPlayer.Builder(
            applicationContext,
            renderersFactory,
            buildMediaSourceFactory(applicationContext)
        )
            .setLoadControl(loadControl)
            // Seeking with closest sync enables instant playback without keyframe decoding stalls
            .setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ handleAudioFocus
            )
            .setHandleAudioBecomingNoisy(appPreferences.isPauseOnHeadsetDisconnect())
            .setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
            .setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS)
            .setStuckBufferingDetectionTimeoutMs(25_000)
            .setStuckPlayingDetectionTimeoutMs(30_000)
            .setStuckPlayingNotEndingTimeoutMs(30_000)
            .setUsePlatformDiagnostics(true)
            .build().apply {
                var tspBuilder = trackSelectionParameters.buildUpon()
                if (!preferredAudioLang.equals("default", ignoreCase = true) && preferredAudioLang.isNotBlank()) {
                    tspBuilder = tspBuilder.setPreferredAudioLanguage(preferredAudioLang)
                }
                if (!subtitlesEnabled) {
                    tspBuilder = tspBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                } else if (preferredSubLang.isNotEmpty()) {
                    tspBuilder = tspBuilder.setPreferredTextLanguage(preferredSubLang)
                }
                trackSelectionParameters = tspBuilder.build()

                if (playbackSpeed != 1.0f) {
                    this.playbackParameters = PlaybackParameters(playbackSpeed)
                }
                if (repeatMode != Player.REPEAT_MODE_OFF) {
                    this.repeatMode = repeatMode
                }
                volume = if (isMuted) 0f else 1f

                if (url.isNotBlank()) {
                    val initialMetadata = androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist("Video")
                        .build()

                    val mediaUri = if (url.startsWith("/")) {
                        android.net.Uri.fromFile(java.io.File(url))
                    } else {
                        android.net.Uri.parse(url)
                    }

                    val mItem = MediaItem.Builder()
                        .setUri(mediaUri)
                        .setMediaMetadata(initialMetadata)
                        .build()

                    if (initialProgress > 0L) {
                        setMediaItem(mItem, initialProgress)
                    } else {
                        setMediaItem(mItem)
                    }
                    prepare()
                    playWhenReady = true
                }
            }

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying.value = playing
                com.example.ymediaplayer.service.VideoPlaybackService.serviceInstance?.updateForegroundNotification()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    lastReadyAtMs = SystemClock.elapsedRealtime()
                    recoveryMessage.value = if (safeModeActive.value) "Playing in compatibility mode" else null
                    duration.value = newPlayer.duration.coerceAtLeast(0L)
                    val vs = newPlayer.videoSize
                    val rot = vs.unappliedRotationDegrees
                    val effW = if (rot == 90 || rot == 270) vs.height else vs.width
                    val effH = if (rot == 90 || rot == 270) vs.width else vs.height
                    if (effW > 0 && effH > 0) {
                        videoWidth.value = effW
                        videoHeight.value = effH
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    isPlaying.value = false
                }
                com.example.ymediaplayer.service.VideoPlaybackService.serviceInstance?.updateForegroundNotification()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady) {
                    isPlaying.value = false
                }
                com.example.ymediaplayer.service.VideoPlaybackService.serviceInstance?.updateForegroundNotification()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                resetRecoveryForNewItem(applicationContext, mediaItem)
                com.example.ymediaplayer.service.VideoPlaybackService.serviceInstance?.updateForegroundNotification()
            }

            override fun onPlayerError(error: PlaybackException) {
                handlePlaybackError(applicationContext, newPlayer, error)
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                val rot = videoSize.unappliedRotationDegrees
                val rawW = videoSize.width
                val rawH = videoSize.height
                val effectiveW = if (rot == 90 || rot == 270) rawH else rawW
                val effectiveH = if (rot == 90 || rot == 270) rawW else rawH
                if (effectiveW > 0 && effectiveH > 0) {
                    videoWidth.value = effectiveW
                    videoHeight.value = effectiveH
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                currentAudioSessionId.value = audioSessionId
            }
        }
        newPlayer.addListener(listener)
        playerListener = listener

        player = newPlayer
        currentVideoUrl.value = url
        currentVideoTitle.value = title
        isPlaying.value = newPlayer.isPlaying
        currentAudioSessionId.value = newPlayer.audioSessionId

        if (handleAudioFocus) requestVideoAudioFocus(context)
        recordDiagnostic(
            applicationContext,
            "player_created",
            "buffer=${profile.minBufferMs}-${profile.maxBufferMs}ms, target=${profile.targetBytes / (1024 * 1024)}MB"
        )

        return newPlayer
    }

    fun setInitialDimensions(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            videoWidth.value = width
            videoHeight.value = height
        }
    }

    fun minimizeToMiniPlayer(url: String, title: String, width: Int = 0, height: Int = 0) {
        currentVideoUrl.value = url
        currentVideoTitle.value = title
        if (width > 0 && height > 0) {
            videoWidth.value = width
            videoHeight.value = height
        }
        isMiniPlayerActive.value = true
    }

    fun expandFromMiniPlayer() {
        isMiniPlayerActive.value = false
    }

    fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying || p.playWhenReady) {
            p.pause()
            p.playWhenReady = false
            isPausedByCall = false
            isManuallyResumedDuringCall = false
            isPlaying.value = false
        } else {
            com.example.ymediaplayer.service.MusicService.pauseMusic()
            manualPlay()
            isPlaying.value = true
        }
    }

    fun closeMiniPlayer() {
        isMiniPlayerActive.value = false
        releasePlayer()
        currentVideoUrl.value = null
        currentVideoTitle.value = null
        videoWidth.value = 0
        videoHeight.value = 0
    }

    fun releasePlayer() {
        activeStreamingHash = null
        recoveryJob?.cancel()
        recoveryJob = null
        recoveryAttempt = 0
        safeModeActive.value = false
        recoveryMessage.value = null
        compatibilityFallbackRequest.value = null
        videoWidth.value = 0
        videoHeight.value = 0
        abandonVideoAudioFocus()
        try {
            com.example.ymediaplayer.service.VideoPlaybackService.stopService(com.example.ymediaplayer.YMediaApplication.instance)
        } catch (_: Exception) {}
        player?.let { p ->
            playerListener?.let { l -> p.removeListener(l) }
            playerListener = null
            try {
                p.volume = 0f
                p.pause()
                p.stop()
                p.release()
            } catch (_: Exception) {}
        }
        player = null
        isPlaying.value = false
        currentAudioSessionId.value = 0
    }

    private const val TAG = "VideoPlayback"
    private const val DIAGNOSTICS_FILE = "playback_diagnostics.log"
    private const val MAX_DIAGNOSTICS = 80
    private const val MAX_DIAGNOSTICS_FILE_BYTES = 512 * 1024L
    private const val STABLE_PLAYBACK_RESET_MS = 30_000L
}
