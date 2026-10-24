package com.example.ymediaplayer.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.ymediaplayer.AppRoot
import com.example.ymediaplayer.MainActivity

class VideoPlayerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_VIDEO_URL = "EXTRA_VIDEO_URL"
        const val EXTRA_VIDEO_TITLE = "EXTRA_VIDEO_TITLE"
        const val EXTRA_TORRENT_HASH = "EXTRA_TORRENT_HASH"
        const val EXTRA_POSTER_URL = "EXTRA_POSTER_URL"
        const val EXTRA_IS_STREAMING = "EXTRA_IS_STREAMING"
        var onUserLeaveHintListener: (() -> Unit)? = null
    }

    private var currentUrl by mutableStateOf("")
    private var requestSequence by mutableIntStateOf(0)
    private var streamTorrentHash by mutableStateOf<String?>(null)
    private var isStreaming by mutableStateOf(false)
    private var streamTitle by mutableStateOf("")
    private var streamPosterUrl by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialLp = window.attributes
        initialLp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = initialLp

        currentUrl = savedInstanceState?.getString(EXTRA_VIDEO_URL) ?: intent.getStringExtra(EXTRA_VIDEO_URL) ?: ""
        streamTorrentHash = savedInstanceState?.getString(EXTRA_TORRENT_HASH) ?: intent.getStringExtra(EXTRA_TORRENT_HASH)
        isStreaming = savedInstanceState?.getBoolean(EXTRA_IS_STREAMING) ?: intent.getBooleanExtra(EXTRA_IS_STREAMING, false)
        streamTitle = savedInstanceState?.getString(EXTRA_VIDEO_TITLE) ?: intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: ""
        streamPosterUrl = savedInstanceState?.getString(EXTRA_POSTER_URL) ?: intent.getStringExtra(EXTRA_POSTER_URL) ?: ""

        val prefs = com.example.ymediaplayer.data.AppPreferences(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                setPictureInPictureParams(
                    android.app.PictureInPictureParams.Builder()
                        .setAutoEnterEnabled(prefs.isAutoPipEnabled())
                        .build()
                )
            } catch (_: Exception) {}
        }

        setContent {
            AppRoot {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    if (currentUrl.isNotEmpty() || (isStreaming && !streamTorrentHash.isNullOrBlank())) {
                        key(currentUrl, requestSequence, streamTorrentHash) {
                            VideoPlayerScreen(
                                videoUrl = currentUrl,
                                streamTorrentHash = if (isStreaming) streamTorrentHash else null,
                                streamTitle = streamTitle,
                                streamPosterUrl = streamPosterUrl,
                                onBack = { finish() },
                                onOpenSettings = {
                                    val intent = Intent(this@VideoPlayerActivity, MainActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                        putExtra("OPEN_SETTINGS", true)
                                    }
                                    startActivity(intent)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val prefs = com.example.ymediaplayer.data.AppPreferences(this)
            try {
                setPictureInPictureParams(
                    android.app.PictureInPictureParams.Builder()
                        .setAutoEnterEnabled(prefs.isAutoPipEnabled())
                        .build()
                )
            } catch (_: Exception) {}
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
        val newHash = intent.getStringExtra(EXTRA_TORRENT_HASH)
        val newStream = intent.getBooleanExtra(EXTRA_IS_STREAMING, false)
        if (newStream && !newHash.isNullOrBlank()) {
            streamTorrentHash = newHash
            isStreaming = true
            streamTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: ""
            streamPosterUrl = intent.getStringExtra(EXTRA_POSTER_URL) ?: ""
            currentUrl = newUrl ?: ""
            requestSequence++
        } else {
            isStreaming = false
            streamTorrentHash = null
            streamTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: ""
            streamPosterUrl = intent.getStringExtra(EXTRA_POSTER_URL) ?: ""
            val manager = com.example.ymediaplayer.player.VideoPlaybackManager
            if (!newUrl.isNullOrEmpty()) {
                manager.player?.let { player ->
                    manager.currentVideoUrl.value?.let { url ->
                        com.example.ymediaplayer.data.AppPreferences(this).saveVideoProgress(
                            url, player.currentPosition, player.duration.coerceAtLeast(0L))
                    }
                }
                requestSequence++
                currentUrl = newUrl
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        onUserLeaveHintListener?.invoke()
        if (com.example.ymediaplayer.player.VideoPlaybackManager.player?.isPlaying == true) {
            com.example.ymediaplayer.service.VideoPlaybackService.startService(this)
            com.example.ymediaplayer.service.VideoPlaybackService.notifyPlayerUpdated()
        }
    }

    override fun onPause() {
        super.onPause()
        val player = com.example.ymediaplayer.player.VideoPlaybackManager.player
        if (isFinishing && !com.example.ymediaplayer.player.VideoPlaybackManager.isMiniPlayerActive.value) {
            com.example.ymediaplayer.service.VideoPlaybackService.stopService(this)
            player?.let { p ->
                try {
                    p.volume = 0f
                    p.pause()
                    p.stop()
                } catch (_: Exception) {}
            }
        } else if (player?.isPlaying == true) {
            com.example.ymediaplayer.service.VideoPlaybackService.startService(this)
            com.example.ymediaplayer.service.VideoPlaybackService.notifyPlayerUpdated()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val manager = com.example.ymediaplayer.player.VideoPlaybackManager
        outState.putString(EXTRA_VIDEO_URL, manager.currentVideoUrl.value ?: currentUrl)
        manager.player?.let { player ->
            val prefs = com.example.ymediaplayer.data.AppPreferences(this)
            prefs.saveVideoProgress(manager.currentVideoUrl.value ?: currentUrl,
                player.currentPosition, player.duration.coerceAtLeast(0L))
        }
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        super.onStop()
        val prefs = com.example.ymediaplayer.data.AppPreferences(this)
        val isPip = isInPictureInPictureMode
        val isBg = prefs.isBackgroundPlayEnabled() || com.example.ymediaplayer.player.VideoPlaybackManager.isMiniPlayerActive.value
        val player = com.example.ymediaplayer.player.VideoPlaybackManager.player

        if (!isPip && !isBg) {
            player?.pause()
            com.example.ymediaplayer.service.VideoPlaybackService.stopService(this)
        } else if (player != null && player.isPlaying) {
            com.example.ymediaplayer.service.VideoPlaybackService.startService(this)
            com.example.ymediaplayer.service.VideoPlaybackService.notifyPlayerUpdated()
        }

        if (isFinishing && !com.example.ymediaplayer.player.VideoPlaybackManager.isMiniPlayerActive.value) {
            com.example.ymediaplayer.service.VideoPlaybackService.stopService(this)
            com.example.ymediaplayer.player.VideoPlaybackManager.releasePlayer()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        onUserLeaveHintListener = null
        if (isStreaming && !streamTorrentHash.isNullOrBlank()) {
            com.example.ymediaplayer.mycinema.service.TorrentEngine.cleanupStream(this, streamTorrentHash!!)
        }
        if (!isChangingConfigurations && !com.example.ymediaplayer.player.VideoPlaybackManager.isMiniPlayerActive.value) {
            com.example.ymediaplayer.service.VideoPlaybackService.stopService(this)
            com.example.ymediaplayer.player.VideoPlaybackManager.releasePlayer()
        }
    }
}
