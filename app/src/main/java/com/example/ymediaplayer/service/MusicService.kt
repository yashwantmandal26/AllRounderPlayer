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

        // Observable "Now Playing" state — readable from any Composable
        var isMusicPlaying: androidx.compose.runtime.MutableState<Boolean> =
            androidx.compose.runtime.mutableStateOf(false)
        var nowPlayingTitle: androidx.compose.runtime.MutableState<String> =
            androidx.compose.runtime.mutableStateOf("")
        var nowPlayingArtist: androidx.compose.runtime.MutableState<String> =
            androidx.compose.runtime.mutableStateOf("")
        var nowPlayingArtUri: androidx.compose.runtime.MutableState<android.net.Uri?> =
            androidx.compose.runtime.mutableStateOf(null)
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .build()

        currentAudioSessionId = player.audioSessionId
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                currentAudioSessionId = audioSessionId
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
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
