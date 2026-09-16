@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ymediaplayer.player

import android.content.Context
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
        }

        // Fast-start load control tuned for local device playback (instant startup in ~200ms)
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 1_500,
                /* maxBufferMs = */ 3_000,
                /* bufferForPlaybackMs = */ 200,
                /* bufferForPlaybackAfterRebufferMs = */ 500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val newPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                handleAudioFocus
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
                if (initialProgress > 3000L) {
                    setMediaItem(MediaItem.fromUri(url), initialProgress)
                } else {
                    setMediaItem(MediaItem.fromUri(url))
                }
                prepare()
                playWhenReady = true
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
        }
        newPlayer.addListener(listener)
        playerListener = listener

        player = newPlayer
        currentVideoUrl.value = url
        currentVideoTitle.value = title
        isPlaying.value = newPlayer.isPlaying

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
        } else {
            if (p.playbackState == Player.STATE_ENDED) {
                p.seekTo(0)
            }
            p.play()
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
    }
}
