@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ymediaplayer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.ymediaplayer.MainActivity
import com.example.ymediaplayer.R
import com.example.ymediaplayer.data.AppPreferences
import com.example.ymediaplayer.data.MusicItem
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.ByteArrayOutputStream

class MusicService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    companion object {
        const val EXTRA_OPEN_MUSIC_PLAYER = "EXTRA_OPEN_MUSIC_PLAYER"
        const val MUSIC_NOTIFICATION_CHANNEL_ID = "music_playback_channel"
        const val MUSIC_NOTIFICATION_ID = 1001

        const val CUSTOM_ACTION_FAVORITE = "com.example.ymediaplayer.ACTION_FAVORITE"
        const val CUSTOM_ACTION_REWIND = "com.example.ymediaplayer.ACTION_REWIND_10"
        const val CUSTOM_ACTION_FORWARD = "com.example.ymediaplayer.ACTION_FORWARD_10"

        var currentAudioSessionId: Int = 0
            private set

        // Direct player reference when service is running
        var playerInstance: Player? = null
            private set

        var serviceInstance: MusicService? = null
            private set

        // Observable "Now Playing" state — readable from any Composable
        var isMusicPlaying: androidx.compose.runtime.MutableState<Boolean> =
            androidx.compose.runtime.mutableStateOf(false)
        var nowPlayingTitle: androidx.compose.runtime.MutableState<String> =
            androidx.compose.runtime.mutableStateOf("")
        var nowPlayingArtist: androidx.compose.runtime.MutableState<String> =
            androidx.compose.runtime.mutableStateOf("")
        var nowPlayingArtUri: androidx.compose.runtime.MutableState<Uri?> =
            androidx.compose.runtime.mutableStateOf(null)

        var currentMusicItem: MusicItem? = null
        var currentPlaylist: List<MusicItem> = emptyList()
        var currentSongIndex: Int = -1

        var onPlayNextAction: (() -> Unit)? = null
        var onPlayPrevAction: (() -> Unit)? = null
        var onTogglePlayPauseAction: (() -> Unit)? = null
        var onPauseAction: (() -> Unit)? = null
        var onStopAction: (() -> Unit)? = null

        fun resolveArtworkBytes(context: Context, uri: Uri?): ByteArray? {
            if (uri == null) return null
            return try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream) ?: return null
                    val maxDim = 512
                    val scaled = if (bmp.width > maxDim || bmp.height > maxDim) {
                        val ratio = maxDim.toFloat() / maxOf(bmp.width, bmp.height)
                        Bitmap.createScaledBitmap(bmp, (bmp.width * ratio).toInt().coerceAtLeast(1), (bmp.height * ratio).toInt().coerceAtLeast(1), true)
                    } else bmp
                    val baos = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                    baos.toByteArray()
                }
            } catch (_: Exception) {
                null
            }
        }

        fun pauseMusic() {
            playerInstance?.let {
                if (it.isPlaying) {
                    it.pause()
                }
            }
            onPauseAction?.invoke()
            isMusicPlaying.value = false
        }

        fun stopMusic() {
            playerInstance?.let {
                it.stop()
            }
            onStopAction?.invoke()
            isMusicPlaying.value = false
        }

        fun playSong(item: MusicItem) {
            com.example.ymediaplayer.player.VideoPlaybackManager.player?.let { vPlayer ->
                if (vPlayer.isPlaying) {
                    vPlayer.pause()
                }
            }
            currentMusicItem = item
            nowPlayingTitle.value = item.title
            nowPlayingArtist.value = item.artist
            nowPlayingArtUri.value = item.albumArtUri

            val player = playerInstance ?: return
            val context = serviceInstance?.applicationContext

            val artBytes = context?.let { resolveArtworkBytes(it, item.albumArtUri) }

            val metadata = MediaMetadata.Builder()
                .setTitle(item.title)
                .setArtist(item.artist)
                .setAlbumTitle(item.album)
                .setArtworkUri(item.albumArtUri)
                .apply {
                    if (artBytes != null) {
                        setArtworkData(artBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    }
                }
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(item.uri)
                .setMediaId(item.id.toString())
                .setMediaMetadata(metadata)
                .build()

            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
            isMusicPlaying.value = true

            serviceInstance?.updateSessionCustomLayout()
        }

        fun togglePlayPause() {
            val player = playerInstance
            if (player != null) {
                if (player.isPlaying) {
                    player.pause()
                } else {
                    com.example.ymediaplayer.player.VideoPlaybackManager.player?.let { vPlayer ->
                        if (vPlayer.isPlaying) {
                            vPlayer.pause()
                        }
                    }
                    if (player.playbackState == Player.STATE_ENDED) {
                        player.seekTo(0L)
                    }
                    player.play()
                }
                isMusicPlaying.value = player.isPlaying
                return
            }
            onTogglePlayPauseAction?.invoke()
        }

        fun playNext() {
            val player = playerInstance
            if (player != null && currentPlaylist.isNotEmpty()) {
                val nextIdx = (currentSongIndex + 1) % currentPlaylist.size
                currentSongIndex = nextIdx
                playSong(currentPlaylist[nextIdx])
                return
            }
            if (onPlayNextAction != null) {
                onPlayNextAction?.invoke()
            } else {
                player?.let {
                    if (it.hasNextMediaItem()) it.seekToNextMediaItem()
                }
            }
        }

        fun playPrevious() {
            val player = playerInstance
            if (player != null && currentPlaylist.isNotEmpty()) {
                if (player.currentPosition > 3000L) {
                    player.seekTo(0L)
                    return
                }
                val prevIdx = if (currentSongIndex <= 0) currentPlaylist.size - 1 else currentSongIndex - 1
                currentSongIndex = prevIdx
                playSong(currentPlaylist[prevIdx])
                return
            }
            if (onPlayPrevAction != null) {
                onPlayPrevAction?.invoke()
            } else {
                player?.let {
                    if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() else it.seekTo(0L)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        serviceInstance = this

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MUSIC_NOTIFICATION_CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active music playback controls and artwork"
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        val audioVisualizerProcessor = AudioVisualizerProcessor()
        val renderersFactory = object : androidx.media3.exoplayer.DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink {
                return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(audioVisualizerProcessor))
                    .build()
            }
        }

        val appPreferences = AppPreferences(this)
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
        player.addListener(object : Player.Listener {
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
                if (playbackState == Player.STATE_ENDED ||
                    playbackState == Player.STATE_IDLE) {
                    AudioReactor.reset()
                }
            }
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                AudioReactor.reset()
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                AudioReactor.reset()
                mediaItem?.mediaMetadata?.let { meta ->
                    if (!meta.title.isNullOrBlank()) nowPlayingTitle.value = meta.title.toString()
                    if (!meta.artist.isNullOrBlank()) nowPlayingArtist.value = meta.artist.toString()
                    if (meta.artworkUri != null) nowPlayingArtUri.value = meta.artworkUri
                }
                updateSessionCustomLayout()
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

        val sessionCallback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(SessionCommand(CUSTOM_ACTION_FAVORITE, Bundle.EMPTY))
                    .add(SessionCommand(CUSTOM_ACTION_REWIND, Bundle.EMPTY))
                    .add(SessionCommand(CUSTOM_ACTION_FORWARD, Bundle.EMPTY))
                    .build()

                val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                    .add(Player.COMMAND_PLAY_PAUSE)
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                    .build()

                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(sessionCommands)
                    .setAvailablePlayerCommands(playerCommands)
                    .setCustomLayout(buildCustomLayout())
                    .build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    CUSTOM_ACTION_FAVORITE -> {
                        val currentUri = currentMusicItem?.uri?.toString()
                        if (!currentUri.isNullOrBlank()) {
                            AppPreferences(this@MusicService).toggleMusicFavorite(currentUri)
                            updateSessionCustomLayout()
                        }
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    CUSTOM_ACTION_REWIND -> {
                        player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    CUSTOM_ACTION_FORWARD -> {
                        player.seekTo((player.currentPosition + 10_000L).coerceAtMost(player.duration.coerceAtLeast(0L)))
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                }
                return super.onCustomCommand(session, controller, customCommand, args)
            }

            override fun onPlaybackResumption(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                val currentItem = mediaSession.player.currentMediaItem
                if (currentItem != null) {
                    val startPos = mediaSession.player.currentPosition
                    return Futures.immediateFuture(
                        MediaSession.MediaItemsWithStartPosition(listOf(currentItem), 0, startPos)
                    )
                }
                val firstItem = currentPlaylist.firstOrNull()
                if (firstItem != null) {
                    val artBytes = resolveArtworkBytes(this@MusicService, firstItem.albumArtUri)
                    val metadata = MediaMetadata.Builder()
                        .setTitle(firstItem.title)
                        .setArtist(firstItem.artist)
                        .setAlbumTitle(firstItem.album)
                        .setArtworkUri(firstItem.albumArtUri)
                        .apply {
                            if (artBytes != null) {
                                setArtworkData(artBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                            }
                        }
                        .build()
                    val mItem = MediaItem.Builder()
                        .setUri(firstItem.uri)
                        .setMediaId(firstItem.id.toString())
                        .setMediaMetadata(metadata)
                        .build()
                    return Futures.immediateFuture(
                        MediaSession.MediaItemsWithStartPosition(listOf(mItem), 0, 0L)
                    )
                }
                return super.onPlaybackResumption(mediaSession, controller)
            }
        }

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setCallback(sessionCallback)
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(MUSIC_NOTIFICATION_CHANNEL_ID)
                .setNotificationId(MUSIC_NOTIFICATION_ID)
                .build()
        )
    }

    private fun buildCustomLayout(): List<CommandButton> {
        val currentUri = currentMusicItem?.uri?.toString()
        val isFav = if (!currentUri.isNullOrBlank()) AppPreferences(this).isMusicFavorite(currentUri) else false

        val favoriteButton = CommandButton.Builder()
            .setDisplayName(if (isFav) "Favorited" else "Favorite")
            .setIconResId(if (isFav) R.drawable.ic_notif_favorite else R.drawable.ic_notif_favorite_border)
            .setSessionCommand(SessionCommand(CUSTOM_ACTION_FAVORITE, Bundle.EMPTY))
            .build()

        return listOf(favoriteButton)
    }

    fun updateSessionCustomLayout() {
        val session = mediaSession ?: return
        session.setCustomLayout(buildCustomLayout())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        serviceInstance = null
        playerInstance = null
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

