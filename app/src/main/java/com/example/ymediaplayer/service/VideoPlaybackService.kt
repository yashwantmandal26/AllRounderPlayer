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
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.ymediaplayer.MainActivity
import com.example.ymediaplayer.R
import com.example.ymediaplayer.data.AppPreferences
import com.example.ymediaplayer.player.VideoPlaybackManager
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class VideoPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    companion object {
        const val VIDEO_NOTIFICATION_CHANNEL_ID = "video_playback_channel"
        const val VIDEO_NOTIFICATION_ID = 2001

        const val CUSTOM_ACTION_REWIND = "com.example.ymediaplayer.VIDEO_ACTION_REWIND"
        const val CUSTOM_ACTION_FORWARD = "com.example.ymediaplayer.VIDEO_ACTION_FORWARD"
        const val CUSTOM_ACTION_FAVORITE = "com.example.ymediaplayer.VIDEO_ACTION_FAVORITE"

        var serviceInstance: VideoPlaybackService? = null
            private set

        fun startService(context: Context) {
            // No-op: user requested no notifications on the phone for video mode
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

        fun updateCustomLayout() {
            // No-op
        }

        fun notifyPlayerUpdated() {
            // No-op: no notification for video mode
        }
    }

    override fun onCreate() {
        super.onCreate()
        serviceInstance = this
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(VIDEO_NOTIFICATION_ID)
        } catch (_: Exception) {}
    }

    private fun ensureForegroundNotification() {
        // No-op: video playback mode has no notifications
    }

    fun initOrUpdateSession() {
        val player = VideoPlaybackManager.player ?: return

        if (mediaSession != null) {
            updateSessionCustomLayout()
            return
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            val curUrl = VideoPlaybackManager.currentVideoUrl.value
            if (!curUrl.isNullOrEmpty()) {
                putExtra(MainActivity.EXTRA_OPEN_VIDEO_URL, curUrl)
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
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    CUSTOM_ACTION_FORWARD -> {
                        player.seekTo((player.currentPosition + 10_000L).coerceAtMost(player.duration.coerceAtLeast(0L)))
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        ensureForegroundNotification()
        if (mediaSession == null && VideoPlaybackManager.player != null) {
            initOrUpdateSession()
        }
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
