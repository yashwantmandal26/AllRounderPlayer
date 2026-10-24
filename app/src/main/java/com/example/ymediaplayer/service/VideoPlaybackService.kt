@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ymediaplayer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.ymediaplayer.R
import com.example.ymediaplayer.data.AppPreferences
import com.example.ymediaplayer.player.VideoPlaybackManager
import com.example.ymediaplayer.ui.VideoPlayerActivity
import com.example.ymediaplayer.util.MediaArtworkHelper
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.File

class VideoPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    companion object {
        const val VIDEO_NOTIFICATION_CHANNEL_ID = "video_playback_channel"
        const val VIDEO_NOTIFICATION_ID = 2001

        const val ACTION_PLAY_PAUSE = "com.example.ymediaplayer.VIDEO_ACTION_PLAY_PAUSE"
        const val ACTION_PREV = "com.example.ymediaplayer.VIDEO_ACTION_PREV"
        const val ACTION_NEXT = "com.example.ymediaplayer.VIDEO_ACTION_NEXT"
        const val ACTION_REWIND = "com.example.ymediaplayer.VIDEO_ACTION_REWIND_10"
        const val ACTION_FORWARD = "com.example.ymediaplayer.VIDEO_ACTION_FORWARD_10"
        const val ACTION_STOP = "com.example.ymediaplayer.VIDEO_ACTION_STOP"

        const val CUSTOM_ACTION_REWIND = "com.example.ymediaplayer.VIDEO_ACTION_REWIND"
        const val CUSTOM_ACTION_FORWARD = "com.example.ymediaplayer.VIDEO_ACTION_FORWARD"
        const val CUSTOM_ACTION_FAVORITE = "com.example.ymediaplayer.VIDEO_ACTION_FAVORITE"

        var serviceInstance: VideoPlaybackService? = null
            private set

        fun startService(context: Context) {
            try {
                val intent = Intent(context, VideoPlaybackService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {}
        }

        fun stopService(context: Context) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(VIDEO_NOTIFICATION_ID)
                serviceInstance?.run {
                    try {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } catch (_: Exception) {}
                    mediaSession?.run {
                        release()
                    }
                    mediaSession = null
                    stopSelf()
                }
                serviceInstance = null
            } catch (_: Exception) {}
        }

        fun notifyPlayerUpdated() {
            serviceInstance?.updateForegroundNotification()
        }
    }

    override fun onCreate() {
        super.onCreate()
        serviceInstance = this

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                VIDEO_NOTIFICATION_CHANNEL_ID,
                "Video & Movie Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows video and movie playback controls, title, and artwork"
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        if (mediaSession == null && VideoPlaybackManager.player != null) {
            initOrUpdateSession()
        }
        ensureForegroundNotification()
    }

    fun ensureForegroundNotification() {
        updateForegroundNotification()
    }

    fun initOrUpdateSession() {
        val player = VideoPlaybackManager.player ?: return

        if (mediaSession != null) {
            updateSessionCustomLayout()
            return
        }

        val curUrl = VideoPlaybackManager.currentVideoUrl.value ?: ""
        val curTitle = VideoPlaybackManager.currentVideoTitle.value ?: ""
        val curPoster = VideoPlaybackManager.currentPosterUrl.value ?: ""

        val intent = Intent(this, VideoPlayerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (curUrl.isNotBlank()) {
                putExtra(VideoPlayerActivity.EXTRA_VIDEO_URL, curUrl)
                putExtra(VideoPlayerActivity.EXTRA_VIDEO_TITLE, curTitle)
                putExtra(VideoPlayerActivity.EXTRA_POSTER_URL, curPoster)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val forwardingPlayer = object : ForwardingPlayer(player) {
            override fun isCommandAvailable(command: Int): Boolean {
                if (command == Player.COMMAND_SEEK_TO_NEXT || command == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) {
                    return VideoPlaybackManager.onPlayNextAction != null
                }
                if (command == Player.COMMAND_SEEK_TO_PREVIOUS || command == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) {
                    return VideoPlaybackManager.onPlayPrevAction != null
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

            override fun play() {
                VideoPlaybackManager.manualPlay()
            }

            override fun seekToNext() {
                VideoPlaybackManager.onPlayNextAction?.invoke()
            }

            override fun seekToNextMediaItem() {
                VideoPlaybackManager.onPlayNextAction?.invoke()
            }

            override fun seekToPrevious() {
                VideoPlaybackManager.onPlayPrevAction?.invoke()
            }

            override fun seekToPreviousMediaItem() {
                VideoPlaybackManager.onPlayPrevAction?.invoke()
            }
        }

        val sessionCallback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(SessionCommand(CUSTOM_ACTION_REWIND, Bundle.EMPTY))
                    .add(SessionCommand(CUSTOM_ACTION_FORWARD, Bundle.EMPTY))
                    .add(SessionCommand(CUSTOM_ACTION_FAVORITE, Bundle.EMPTY))
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
                    CUSTOM_ACTION_REWIND -> {
                        player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                        updateForegroundNotification()
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    CUSTOM_ACTION_FORWARD -> {
                        player.seekTo((player.currentPosition + 10_000L).coerceAtMost(player.duration.coerceAtLeast(0L)))
                        updateForegroundNotification()
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    CUSTOM_ACTION_FAVORITE -> {
                        val currentUri = VideoPlaybackManager.currentVideoUrl.value
                        if (!currentUri.isNullOrBlank()) {
                            AppPreferences(this@VideoPlaybackService).toggleFavorite(currentUri)
                            updateSessionCustomLayout()
                        }
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                }
                return super.onCustomCommand(session, controller, customCommand, args)
            }
        }

        try {
            mediaSession = MediaSession.Builder(this, forwardingPlayer)
                .setId("AllRounder_Video_Session")
                .setSessionActivity(pendingIntent)
                .setCallback(sessionCallback)
                .build()
        } catch (e: Exception) {
            android.util.Log.e("VideoPlaybackService", "Error creating video MediaSession", e)
        }
    }

    private fun buildCustomLayout(): List<CommandButton> {
        val rewindButton = CommandButton.Builder()
            .setDisplayName("Rewind 10s")
            .setIconResId(R.drawable.ic_notif_rewind_10)
            .setSessionCommand(SessionCommand(CUSTOM_ACTION_REWIND, Bundle.EMPTY))
            .build()

        val forwardButton = CommandButton.Builder()
            .setDisplayName("Forward 10s")
            .setIconResId(R.drawable.ic_notif_forward_10)
            .setSessionCommand(SessionCommand(CUSTOM_ACTION_FORWARD, Bundle.EMPTY))
            .build()

        return listOf(rewindButton, forwardButton)
    }

    fun updateSessionCustomLayout() {
        val session = mediaSession ?: return
        session.setCustomLayout(buildCustomLayout())
    }

    fun updateForegroundNotification() {
        val player = VideoPlaybackManager.player
        val curUrl = VideoPlaybackManager.currentVideoUrl.value
        if (player == null || curUrl.isNullOrBlank()) {
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(VIDEO_NOTIFICATION_ID)
            } catch (_: Exception) {}
            return
        }

        if (mediaSession == null) {
            initOrUpdateSession()
        }

        try {
            val notification = buildMediaNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    VIDEO_NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(VIDEO_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoPlaybackService", "Error in updateForegroundNotification", e)
        }
    }

    private fun buildMediaNotification(): android.app.Notification {
        val player = VideoPlaybackManager.player
        val isPlaying = player?.isPlaying == true
        val currentUrl = VideoPlaybackManager.currentVideoUrl.value ?: ""
        val title = VideoPlaybackManager.currentVideoTitle.value?.ifBlank { null }
            ?: if (currentUrl.contains("MYCinema", ignoreCase = true)) "MYCinema Movie"
            else currentUrl.substringAfterLast("/").substringBeforeLast(".").ifBlank { "Video Playback" }

        val isMovie = currentUrl.contains("MYCinema", ignoreCase = true)
        val subText = if (isMovie) "Movie • AllRounder Player" else "Video • AllRounder Player"

        // Tapping notification returns directly to VideoPlayerActivity
        val contentIntent = Intent(this, VideoPlayerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(VideoPlayerActivity.EXTRA_VIDEO_URL, currentUrl)
            putExtra(VideoPlayerActivity.EXTRA_VIDEO_TITLE, title)
            VideoPlaybackManager.currentPosterUrl.value?.let {
                putExtra(VideoPlayerActivity.EXTRA_POSTER_URL, it)
            }
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            20,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val rewindIntent = Intent(this, VideoPlaybackService::class.java).apply { action = ACTION_REWIND }
        val rewindPendingIntent = PendingIntent.getService(
            this, 21, rewindIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = Intent(this, VideoPlaybackService::class.java).apply { action = ACTION_PREV }
        val prevPendingIntent = PendingIntent.getService(
            this, 22, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = Intent(this, VideoPlaybackService::class.java).apply { action = ACTION_PLAY_PAUSE }
        val playPausePendingIntent = PendingIntent.getService(
            this, 23, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(this, VideoPlaybackService::class.java).apply { action = ACTION_NEXT }
        val nextPendingIntent = PendingIntent.getService(
            this, 24, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val forwardIntent = Intent(this, VideoPlaybackService::class.java).apply { action = ACTION_FORWARD }
        val forwardPendingIntent = PendingIntent.getService(
            this, 25, forwardIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, VideoPlaybackService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 26, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hasPlaylist = (VideoPlaybackManager.activePlaylistQueue?.size ?: 0) > 1

        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
        if (hasPlaylist) {
            mediaStyle.setShowActionsInCompactView(1, 2, 3) // Prev, Play/Pause, Next
        } else {
            mediaStyle.setShowActionsInCompactView(0, 2, 4) // Rewind, Play/Pause, Forward
        }

        // Attach MediaSessionCompat Token so System UI (Quick Settings QS Media Carousel) gets live scrubbing
        try {
            mediaSession?.platformToken?.let { token ->
                mediaStyle.setMediaSession(android.support.v4.media.session.MediaSessionCompat.Token.fromToken(token))
            }
        } catch (_: Exception) {}

        val builder = androidx.core.app.NotificationCompat.Builder(this, VIDEO_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(subText)
            .setContentIntent(contentPendingIntent)
            .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .setStyle(mediaStyle)

        // Actions: Rewind 10s, Prev, Play/Pause, Next, Forward 10s
        builder.addAction(R.drawable.ic_notif_rewind_10, "Rewind 10s", rewindPendingIntent)
        builder.addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent)
        builder.addAction(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (isPlaying) "Pause" else "Play",
            playPausePendingIntent
        )
        builder.addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
        builder.addAction(R.drawable.ic_notif_forward_10, "Forward 10s", forwardPendingIntent)

        // High quality large artwork (Movie poster or video keyframe)
        try {
            val poster = VideoPlaybackManager.currentPosterUrl.value
            val uri = if (currentUrl.isNotBlank()) {
                if (currentUrl.startsWith("/")) Uri.fromFile(File(currentUrl)) else Uri.parse(currentUrl)
            } else null
            val bmp = MediaArtworkHelper.getVideoThumbnailBitmap(this, uri, poster)
            if (bmp != null) {
                builder.setLargeIcon(bmp)
            }
        } catch (_: Throwable) {}

        return builder.build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                val p = VideoPlaybackManager.player
                if (p != null) {
                    if (p.isPlaying) p.pause() else VideoPlaybackManager.manualPlay()
                }
            }
            ACTION_REWIND -> {
                val p = VideoPlaybackManager.player
                if (p != null) {
                    val target = (p.currentPosition - 10_000L).coerceAtLeast(0L)
                    p.seekTo(target)
                }
            }
            ACTION_FORWARD -> {
                val p = VideoPlaybackManager.player
                if (p != null) {
                    val target = (p.currentPosition + 10_000L).coerceAtMost(p.duration.coerceAtLeast(0L))
                    p.seekTo(target)
                }
            }
            ACTION_PREV -> {
                VideoPlaybackManager.onPlayPrevAction?.invoke()
            }
            ACTION_NEXT -> {
                VideoPlaybackManager.onPlayNextAction?.invoke()
            }
            ACTION_STOP -> {
                stopService(this)
                VideoPlaybackManager.player?.pause()
                return START_NOT_STICKY
            }
        }
        updateForegroundNotification()
        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        if (mediaSession == null && VideoPlaybackManager.player != null) {
            initOrUpdateSession()
        }
        return mediaSession
    }

    override fun onDestroy() {
        serviceInstance = null
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
