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
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import com.example.ymediaplayer.util.MediaArtworkHelper
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

        const val ACTION_PLAY_PAUSE = "com.example.ymediaplayer.ACTION_PLAY_PAUSE"
        const val ACTION_PREV = "com.example.ymediaplayer.ACTION_PREV"
        const val ACTION_NEXT = "com.example.ymediaplayer.ACTION_NEXT"

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

        var isAutoPlayEnabled: androidx.compose.runtime.MutableState<Boolean> =
            androidx.compose.runtime.mutableStateOf(true)

        var totalTracksCount: androidx.compose.runtime.MutableIntState =
            androidx.compose.runtime.mutableIntStateOf(0)
        var totalTracksSize: androidx.compose.runtime.MutableLongState =
            androidx.compose.runtime.mutableLongStateOf(0L)

        var currentMusicItem: androidx.compose.runtime.MutableState<MusicItem?> =
            androidx.compose.runtime.mutableStateOf(null)
        var currentPlaylist: List<MusicItem> = emptyList()
        var currentSongIndex: Int = -1

        var onPlayNextAction: (() -> Unit)? = null
        var onPlayPrevAction: (() -> Unit)? = null
        var onTogglePlayPauseAction: (() -> Unit)? = null
        var onPauseAction: (() -> Unit)? = null
        var onStopAction: (() -> Unit)? = null

        fun resolveArtworkBytes(context: Context, songUri: Uri?, albumArtUri: Uri? = null): ByteArray? {
            return MediaArtworkHelper.getAudioArtworkBytes(context, songUri, albumArtUri)
        }

        fun resolveArtworkBytes(context: Context, uri: Uri?): ByteArray? {
            return MediaArtworkHelper.getAudioArtworkBytes(context, uri, uri)
        }

        private var fadeJob: Job? = null
        private val musicScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        fun fadeIn(player: Player, durationMs: Long = 250L, onComplete: (() -> Unit)? = null) {
            fadeJob?.cancel()
            player.volume = 0f
            if (!player.isPlaying) {
                player.play()
            }
            fadeJob = musicScope.launch {
                val steps = 10
                val stepDelay = durationMs / steps
                for (i in 1..steps) {
                    delay(stepDelay)
                    player.volume = (i.toFloat() / steps).coerceIn(0f, 1f)
                }
                player.volume = 1f
                onComplete?.invoke()
            }
        }

        fun fadeOutAndPause(player: Player, durationMs: Long = 250L, onComplete: (() -> Unit)? = null) {
            fadeJob?.cancel()
            val initialVolume = player.volume.coerceIn(0f, 1f)
            if (initialVolume <= 0.05f || !player.isPlaying) {
                player.pause()
                player.volume = 1f
                onComplete?.invoke()
                return
            }
            fadeJob = musicScope.launch {
                val steps = 10
                val stepDelay = durationMs / steps
                for (i in 1..steps) {
                    delay(stepDelay)
                    val fraction = 1f - (i.toFloat() / steps)
                    player.volume = (initialVolume * fraction).coerceIn(0f, 1f)
                }
                player.pause()
                player.volume = 1f
                onComplete?.invoke()
            }
        }

        fun pauseMusic() {
            playerInstance?.let {
                fadeOutAndPause(it) {
                    onPauseAction?.invoke()
                    isMusicPlaying.value = false
                }
            } ?: run {
                onPauseAction?.invoke()
                isMusicPlaying.value = false
            }
        }

        fun stopMusic() {
            fadeJob?.cancel()
            playerInstance?.let {
                it.stop()
                it.volume = 1f
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
            val context = serviceInstance?.applicationContext ?: com.example.ymediaplayer.YMediaApplication.instance
            try {
                com.example.ymediaplayer.service.VideoPlaybackService.stopService(context)
            } catch (_: Exception) {}

            try {
                val sIntent = Intent(context, MusicService::class.java)
                try {
                    context.startService(sIntent)
                } catch (_: Exception) {
                    ContextCompat.startForegroundService(context, sIntent)
                }
            } catch (_: Exception) {}

            currentMusicItem.value = item
            nowPlayingTitle.value = item.title
            nowPlayingArtist.value = item.artist
            nowPlayingArtUri.value = item.albumArtUri
            AppPreferences(context).recordMusicPlayed(item.uri.toString())

            val player = playerInstance ?: return

            val initialMetadata = MediaMetadata.Builder()
                .setTitle(item.title)
                .setArtist(item.artist)
                .setAlbumTitle(item.album)
                .setArtworkUri(item.albumArtUri)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(item.uri)
                .setMediaId(item.id.toString())
                .setMediaMetadata(initialMetadata)
                .build()

            player.setMediaItem(mediaItem)
            player.prepare()
            fadeIn(player)
            isMusicPlaying.value = true

            serviceInstance?.updateSessionCustomLayout()

            // Resolve artwork bytes asynchronously on IO thread to prevent UI thread freeze
            CoroutineScope(Dispatchers.IO).launch {
                val artBytes = resolveArtworkBytes(context, item.uri, item.albumArtUri)
                if (artBytes != null && currentMusicItem.value?.id == item.id) {
                    val enrichedMeta = initialMetadata.buildUpon()
                        .setArtworkData(artBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                        .build()
                    withContext(Dispatchers.Main) {
                        if (currentMusicItem.value?.id == item.id) {
                            val updatedItem = mediaItem.buildUpon().setMediaMetadata(enrichedMeta).build()
                            val curIdx = player.currentMediaItemIndex
                            if (curIdx >= 0 && curIdx < player.mediaItemCount) {
                                player.replaceMediaItem(curIdx, updatedItem)
                            }
                            serviceInstance?.updateSessionCustomLayout()
                        }
                    }
                }
            }
        }

        fun togglePlayPause() {
            val player = playerInstance
            if (player != null) {
                if (player.isPlaying) {
                    fadeOutAndPause(player) {
                        isMusicPlaying.value = false
                    }
                } else {
                    com.example.ymediaplayer.player.VideoPlaybackManager.player?.let { vPlayer ->
                        if (vPlayer.isPlaying) {
                            vPlayer.pause()
                        }
                    }
                    if (player.playbackState == Player.STATE_ENDED) {
                        player.seekTo(0L)
                    }
                    fadeIn(player) {
                        isMusicPlaying.value = true
                    }
                }
                return
            }
            onTogglePlayPauseAction?.invoke()
        }

        fun playNext() {
            if (onPlayNextAction != null) {
                onPlayNextAction?.invoke()
                return
            }
            val player = playerInstance
            if (player != null && currentPlaylist.isNotEmpty()) {
                val nextIdx = (currentSongIndex + 1) % currentPlaylist.size
                currentSongIndex = nextIdx
                playSong(currentPlaylist[nextIdx])
                return
            }
            player?.let {
                if (it.hasNextMediaItem()) it.seekToNextMediaItem()
            }
        }

        fun playPrevious() {
            if (onPlayPrevAction != null) {
                onPlayPrevAction?.invoke()
                return
            }
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
            player?.let {
                if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() else it.seekTo(0L)
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

        ensureForegroundNotification()

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
        isAutoPlayEnabled.value = appPreferences.isAutoPlayNextEnabled()
        val handleAudioFocus = !appPreferences.isPlayDuringCallsEnabled()

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 20_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 150,
                /* bufferForPlaybackAfterRebufferMs = */ 500
            )
            .setBackBuffer(/* backBufferDurationMs = */ 15_000, /* retainBackBufferFromKeyframe = */ true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setLoadControl(loadControl)
            .setWakeMode(C.WAKE_MODE_LOCAL)
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
                updateForegroundNotification()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED ||
                    playbackState == Player.STATE_IDLE) {
                    AudioReactor.reset()
                }
                if (playbackState == Player.STATE_ENDED) {
                    if (isAutoPlayEnabled.value && player.repeatMode != Player.REPEAT_MODE_ONE) {
                        playNext()
                    }
                }
                updateForegroundNotification()
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
                if (mediaItem != null) {
                    val mediaId = mediaItem.mediaId
                    val uriStr = mediaItem.localConfiguration?.uri?.toString()
                    val found = currentPlaylist.find {
                        (mediaId.isNotBlank() && it.id.toString() == mediaId) || it.uri.toString() == uriStr
                    }
                    if (found != null) {
                        currentMusicItem.value = found
                        currentSongIndex = currentPlaylist.indexOf(found)
                        nowPlayingTitle.value = found.title
                        nowPlayingArtist.value = found.artist
                        nowPlayingArtUri.value = found.albumArtUri
                    } else {
                        mediaItem.mediaMetadata.let { meta ->
                            if (!meta.title.isNullOrBlank()) nowPlayingTitle.value = meta.title.toString()
                            if (!meta.artist.isNullOrBlank()) nowPlayingArtist.value = meta.artist.toString()
                            if (meta.artworkUri != null) nowPlayingArtUri.value = meta.artworkUri
                        }
                    }
                }
                updateSessionCustomLayout()
                updateForegroundNotification()
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
                        val currentUri = currentMusicItem.value?.uri?.toString()
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

        val forwardingPlayer = object : ForwardingPlayer(player) {
            override fun play() {
                fadeIn(player)
            }

            override fun pause() {
                fadeOutAndPause(player)
            }

            override fun setPlayWhenReady(playWhenReady: Boolean) {
                if (playWhenReady) {
                    fadeIn(player)
                } else {
                    fadeOutAndPause(player)
                }
            }

            override fun isCommandAvailable(command: Int): Boolean {
                if (command == Player.COMMAND_SEEK_TO_NEXT || command == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) {
                    return currentPlaylist.isNotEmpty() || onPlayNextAction != null
                }
                if (command == Player.COMMAND_SEEK_TO_PREVIOUS || command == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) {
                    return currentPlaylist.isNotEmpty() || onPlayPrevAction != null
                }
                return super.isCommandAvailable(command)
            }

            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                    .build()
            }

            override fun seekToNext() {
                playNext()
            }

            override fun seekToNextMediaItem() {
                playNext()
            }

            override fun seekToPrevious() {
                playPrevious()
            }

            override fun seekToPreviousMediaItem() {
                playPrevious()
            }
        }

        try {
            mediaSession = MediaSession.Builder(this, forwardingPlayer)
                .setId("AllRounder_Music_Session")
                .setSessionActivity(pendingIntent)
                .setCallback(sessionCallback)
                .build()
        } catch (e: Exception) {
            android.util.Log.e("MusicService", "Error creating music MediaSession", e)
        }

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(MUSIC_NOTIFICATION_CHANNEL_ID)
                .setNotificationId(MUSIC_NOTIFICATION_ID)
                .build()
        )
    }

    private fun buildMediaNotification(): android.app.Notification {
        val title = nowPlayingTitle.value.ifEmpty { "Music Playback" }
        val artist = nowPlayingArtist.value.ifEmpty { "AllRounder Player" }
        val isPlaying = playerInstance?.isPlaying == true

        // Activity intent when notification body is tapped
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_MUSIC_PLAYER, true)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action PendingIntents
        val prevIntent = Intent(this, MusicService::class.java).apply { action = ACTION_PREV }
        val prevPendingIntent = PendingIntent.getService(
            this, 1, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = Intent(this, MusicService::class.java).apply { action = ACTION_PLAY_PAUSE }
        val playPausePendingIntent = PendingIntent.getService(
            this, 2, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(this, MusicService::class.java).apply { action = ACTION_NEXT }
        val nextPendingIntent = PendingIntent.getService(
            this, 3, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = androidx.core.app.NotificationCompat.Builder(this, MUSIC_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(contentPendingIntent)
            .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                playPausePendingIntent
            )
            .addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )

        try {
            val artUri = nowPlayingArtUri.value ?: currentMusicItem.value?.albumArtUri
            val songUri = currentMusicItem.value?.uri
            val bitmap = MediaArtworkHelper.getAudioArtworkBitmap(this, songUri, artUri)
            if (bitmap != null) {
                builder.setLargeIcon(bitmap)
            }
        } catch (_: Throwable) {}

        return builder.build()
    }

    fun updateForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val notification = buildMediaNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        MUSIC_NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(MUSIC_NOTIFICATION_ID, notification)
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicService", "Error in updateForegroundNotification", e)
            }
        }
    }

    private fun ensureForegroundNotification() {
        updateForegroundNotification()
    }

    private fun buildCustomLayout(): List<CommandButton> {
        val currentUri = currentMusicItem.value?.uri?.toString()
        val isFav = if (!currentUri.isNullOrBlank()) AppPreferences(this).isMusicFavorite(currentUri) else false

        val rewindButton = CommandButton.Builder()
            .setDisplayName("Rewind 10s")
            .setIconResId(R.drawable.ic_notif_rewind_10)
            .setSessionCommand(SessionCommand(CUSTOM_ACTION_REWIND, Bundle.EMPTY))
            .build()

        val favoriteButton = CommandButton.Builder()
            .setDisplayName(if (isFav) "Favorited" else "Favorite")
            .setIconResId(if (isFav) R.drawable.ic_notif_favorite else R.drawable.ic_notif_favorite_border)
            .setSessionCommand(SessionCommand(CUSTOM_ACTION_FAVORITE, Bundle.EMPTY))
            .build()

        return listOf(rewindButton, favoriteButton)
    }

    fun updateSessionCustomLayout() {
        val session = mediaSession ?: return
        session.setCustomLayout(buildCustomLayout())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_PREV -> playPrevious()
            ACTION_NEXT -> playNext()
        }
        updateForegroundNotification()
        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        serviceInstance = null
        playerInstance = null
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

