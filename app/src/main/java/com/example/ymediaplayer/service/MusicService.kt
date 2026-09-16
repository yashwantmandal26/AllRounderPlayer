@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ymediaplayer.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.ymediaplayer.MainActivity

class MusicService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    companion object {
        const val EXTRA_OPEN_MUSIC_PLAYER = "EXTRA_OPEN_MUSIC_PLAYER"
        var currentAudioSessionId: Int = 0
            private set

        // Direct player reference when service is running
        var playerInstance: androidx.media3.common.Player? = null
            private set

        // Observable "Now Playing" state — readable from any Composable
        var isMusicPlaying: androidx.compose.runtime.MutableState<Boolean> =
            androidx.compose.runtime.mutableStateOf(false)
        var nowPlayingTitle: androidx.compose.runtime.MutableState<String> =
            androidx.compose.runtime.mutableStateOf("")
        var nowPlayingArtist: androidx.compose.runtime.MutableState<String> =
            androidx.compose.runtime.mutableStateOf("")
        var nowPlayingArtUri: androidx.compose.runtime.MutableState<android.net.Uri?> =
            androidx.compose.runtime.mutableStateOf(null)

        var currentPlaylist: List<com.example.ymediaplayer.data.MusicItem> = emptyList()
        var currentSongIndex: Int = -1

        var onPlayNextAction: (() -> Unit)? = null
        var onPlayPrevAction: (() -> Unit)? = null
        var onTogglePlayPauseAction: (() -> Unit)? = null
        var onPauseAction: (() -> Unit)? = null
        var onStopAction: (() -> Unit)? = null

        fun pauseMusic() {
            onPauseAction?.invoke()
            playerInstance?.let {
                if (it.isPlaying) {
                    it.pause()
                }
            }
            isMusicPlaying.value = false
        }

        fun stopMusic() {
            onStopAction?.invoke()
            playerInstance?.let {
                it.stop()
            }
            isMusicPlaying.value = false
        }

        fun playSong(item: com.example.ymediaplayer.data.MusicItem) {
            com.example.ymediaplayer.player.VideoPlaybackManager.player?.let { vPlayer ->
                if (vPlayer.isPlaying) {
                    vPlayer.pause()
                }
            }
            val player = playerInstance ?: return
            val metadata = androidx.media3.common.MediaMetadata.Builder()
                .setTitle(item.title)
                .setArtist(item.artist)
                .setArtworkUri(item.albumArtUri)
                .build()
            val mediaItem = androidx.media3.common.MediaItem.Builder()
                .setUri(item.uri)
                .setMediaId(item.id.toString())
                .setMediaMetadata(metadata)
                .build()
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
            nowPlayingTitle.value = item.title
            nowPlayingArtist.value = item.artist
            nowPlayingArtUri.value = item.albumArtUri
            isMusicPlaying.value = true
        }

        fun togglePlayPause() {
            if (onTogglePlayPauseAction != null) {
                onTogglePlayPauseAction?.invoke()
            } else {
                playerInstance?.let {
                    if (it.isPlaying) it.pause() else it.play()
                }
            }
        }

        fun playNext() {
            if (onPlayNextAction != null) {
                onPlayNextAction?.invoke()
            } else if (currentPlaylist.isNotEmpty()) {
                val nextIdx = (currentSongIndex + 1) % currentPlaylist.size
                currentSongIndex = nextIdx
                playSong(currentPlaylist[nextIdx])
            } else {
                playerInstance?.let {
                    if (it.hasNextMediaItem()) it.seekToNextMediaItem()
                }
            }
        }

        fun playPrevious() {
            if (onPlayPrevAction != null) {
                onPlayPrevAction?.invoke()
            } else if (currentPlaylist.isNotEmpty()) {
                val prevIdx = if (currentSongIndex <= 0) currentPlaylist.size - 1 else currentSongIndex - 1
                currentSongIndex = prevIdx
                playSong(currentPlaylist[prevIdx])
            } else {
                playerInstance?.let {
                    if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() else it.seekTo(0L)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val audioVisualizerProcessor = AudioVisualizerProcessor()
        val renderersFactory = object : androidx.media3.exoplayer.DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink {
                return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(audioVisualizerProcessor))
                    .build()
            }
        }

        val appPreferences = com.example.ymediaplayer.data.AppPreferences(this)
        val handleAudioFocus = !appPreferences.isPlayDuringCallsEnabled()

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                handleAudioFocus
            )
            .build()

        playerInstance = player
        currentAudioSessionId = player.audioSessionId
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                currentAudioSessionId = audioSessionId
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isMusicPlaying.value = isPlaying
                if (!isPlaying) {
                    AudioReactor.reset()
                }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_ENDED ||
                    playbackState == androidx.media3.common.Player.STATE_IDLE) {
                    AudioReactor.reset()
                }
            }
            override fun onPositionDiscontinuity(
                oldPosition: androidx.media3.common.Player.PositionInfo,
                newPosition: androidx.media3.common.Player.PositionInfo,
                reason: Int
            ) {
                AudioReactor.reset()
            }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                AudioReactor.reset()
                mediaItem?.mediaMetadata?.let { meta ->
                    if (!meta.title.isNullOrBlank()) nowPlayingTitle.value = meta.title.toString()
                    if (!meta.artist.isNullOrBlank()) nowPlayingArtist.value = meta.artist.toString()
                    if (meta.artworkUri != null) nowPlayingArtUri.value = meta.artworkUri
                }
            }
        })

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_MUSIC_PLAYER, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        playerInstance = null
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
