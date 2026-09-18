@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ymediaplayer.player

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.example.ymediaplayer.data.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Singleton managing shared video playback across the full-screen [VideoPlayerScreen]
 * and the floating YouTube-style [InAppMiniPlayer].
 */
object VideoPlaybackManager {
    var player: ExoPlayer? = null
        private set

    val isMiniPlayerActive: MutableState<Boolean> = mutableStateOf(false)
    val currentVideoUrl: MutableState<String?> = mutableStateOf(null)
    val currentVideoTitle: MutableState<String?> = mutableStateOf(null)
    val isPlaying: MutableState<Boolean> = mutableStateOf(false)
    val currentPosition: MutableState<Long> = mutableLongStateOf(0L)
    val duration: MutableState<Long> = mutableLongStateOf(0L)
    val videoWidth: MutableState<Int> = mutableIntStateOf(0)
    val videoHeight: MutableState<Int> = mutableIntStateOf(0)
    val currentAudioSessionId: MutableState<Int> = mutableIntStateOf(0)

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

    private var playerListener: Player.Listener? = null

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

        val current = player
        if (current != null && currentVideoUrl.value == url) {
            currentVideoTitle.value = title
            isPlaying.value = current.isPlaying
            return current
        }

        // Release existing player if switching to a new video
        releasePlayer()

        val appPreferences = AppPreferences(context)
        val handleAudioFocus = !appPreferences.isPlayDuringCallsEnabled()

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            setEnableDecoderFallback(true)
            setAllowedVideoJoiningTimeMs(5000L)
            setEnableAudioTrackPlaybackParams(true)
        }

        // Fast-start load control tuned for high-bitrate, 4K, and 60fps playback with 10s instant rewind back-buffer
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 250,
                /* bufferForPlaybackAfterRebufferMs = */ 1_000
            )
            .setBackBuffer(/* backBufferDurationMs = */ 10_000, /* retainBackBufferFromKeyframe = */ true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val newPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            // Seeking only to sync frames can send a short video back to 0 when it
            // has no later keyframe. Exact seeking honours the point the user taps.
            .setSeekParameters(androidx.media3.exoplayer.SeekParameters.EXACT)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ false
            )
            .setHandleAudioBecomingNoisy(appPreferences.isPauseOnHeadsetDisconnect())
            .setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
            .build().apply {
                var tspBuilder = trackSelectionParameters.buildUpon()
                if (preferredAudioLang != "default" && preferredAudioLang.isNotEmpty()) {
                    tspBuilder = tspBuilder.setPreferredAudioLanguage(preferredAudioLang)
                }
                if (!subtitlesEnabled) {
                    tspBuilder = tspBuilder.setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
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
                if (isMuted) {
                    volume = 0f
                }

                val initialMetadata = androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist("Video")
                    .build()

                val mItem = MediaItem.Builder()
                    .setUri(url)
                    .setMediaMetadata(initialMetadata)
                    .build()

                if (initialProgress > 3000L) {
                    setMediaItem(mItem, initialProgress)
                } else {
                    setMediaItem(mItem)
                }
                prepare()
                playWhenReady = true

                CoroutineScope(Dispatchers.IO).launch {
                    val thumbBytes = try {
                        com.example.ymediaplayer.util.MediaArtworkHelper.getVideoThumbnailBytes(context, android.net.Uri.parse(url))
                    } catch (_: Throwable) { null }
                    if (thumbBytes != null) {
                        withContext(Dispatchers.Main) {
                            if (currentVideoUrl.value == url && player == this@apply) {
                                val enrichedMeta = initialMetadata.buildUpon()
                                    .setArtworkData(thumbBytes, androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                                    .build()
                                val updatedItem = mItem.buildUpon().setMediaMetadata(enrichedMeta).build()
                                val curIdx = currentMediaItemIndex
                                if (curIdx >= 0 && curIdx < mediaItemCount) {
                                    replaceMediaItem(curIdx, updatedItem)
                                }
                            }
                        }
                    }
                }
            }

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying.value = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
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

        requestVideoAudioFocus(context)

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
        if (p.isPlaying) {
            p.pause()
            isPausedByCall = false
            isManuallyResumedDuringCall = false
        } else {
            com.example.ymediaplayer.service.MusicService.pauseMusic()
            manualPlay()
        }
        isPlaying.value = p.isPlaying
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
                p.stop()
                p.release()
            } catch (_: Exception) {}
        }
        player = null
        isPlaying.value = false
        currentAudioSessionId.value = 0
    }
}
