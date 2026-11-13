package com.example.ymediaplayer.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
        const val EXTRA_BACKDROP_URL = "EXTRA_BACKDROP_URL"
        const val EXTRA_LOGO_URL = "EXTRA_LOGO_URL"
        const val EXTRA_IS_STREAMING = "EXTRA_IS_STREAMING"
        const val EXTRA_CINEMA_SHOW_ID = "EXTRA_CINEMA_SHOW_ID"
        const val EXTRA_CINEMA_SEASON = "EXTRA_CINEMA_SEASON"
        const val EXTRA_CINEMA_EPISODE = "EXTRA_CINEMA_EPISODE"
        const val EXTRA_CINEMA_IS_SERIES = "EXTRA_CINEMA_IS_SERIES"
        var onUserLeaveHintListener: (() -> Unit)? = null
        var activeInstance: java.lang.ref.WeakReference<VideoPlayerActivity>? = null

        fun finishPipIfActive() {
            try {
                activeInstance?.get()?.let { act ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && act.isInPictureInPictureMode) {
                        act.finishAndRemoveTask()
                    } else if (!act.isFinishing) {
                        act.finish()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private var currentUrl by mutableStateOf("")
    private var requestSequence by mutableIntStateOf(0)
    private var streamTorrentHash by mutableStateOf<String?>(null)
    private var isStreaming by mutableStateOf(false)
    private var streamTitle by mutableStateOf("")
    private var streamPosterUrl by mutableStateOf("")
    private var streamBackdropUrl by mutableStateOf("")
    private var streamLogoUrl by mutableStateOf("")
    private var cinemaShowId by mutableIntStateOf(0)
    private var cinemaSeason by mutableIntStateOf(0)
    private var cinemaEpisode by mutableIntStateOf(0)
    private var cinemaIsSeries by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Dismiss any existing PiP or previous player instance so two videos never play at once
        activeInstance?.get()?.let { oldAct ->
            if (oldAct != this) {
                try {
                    oldAct.finishAndRemoveTask()
                } catch (_: Exception) {
                    oldAct.finish()
                }
            }
        }
        activeInstance = java.lang.ref.WeakReference(this)

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
        streamBackdropUrl = savedInstanceState?.getString(EXTRA_BACKDROP_URL) ?: intent.getStringExtra(EXTRA_BACKDROP_URL) ?: ""
        streamLogoUrl = savedInstanceState?.getString(EXTRA_LOGO_URL) ?: intent.getStringExtra(EXTRA_LOGO_URL) ?: ""
        cinemaShowId = savedInstanceState?.getInt(EXTRA_CINEMA_SHOW_ID) ?: intent.getIntExtra(EXTRA_CINEMA_SHOW_ID, 0)
        cinemaSeason = savedInstanceState?.getInt(EXTRA_CINEMA_SEASON) ?: intent.getIntExtra(EXTRA_CINEMA_SEASON, 0)
        cinemaEpisode = savedInstanceState?.getInt(EXTRA_CINEMA_EPISODE) ?: intent.getIntExtra(EXTRA_CINEMA_EPISODE, 0)
        cinemaIsSeries = savedInstanceState?.getBoolean(EXTRA_CINEMA_IS_SERIES) ?: intent.getBooleanExtra(EXTRA_CINEMA_IS_SERIES, false)

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
                                streamBackdropUrl = streamBackdropUrl,
                                streamLogoUrl = streamLogoUrl,
                                cinemaShowId = cinemaShowId,
                                cinemaSeason = cinemaSeason,
                                cinemaEpisode = cinemaEpisode,
                                cinemaIsSeries = cinemaIsSeries,
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Restart pointer handlers on either edge of a system interruption.
        // A screenshot overlay can cancel a gesture without delivering normal UPs.
        com.example.ymediaplayer.player.VideoPlaybackManager.windowFocusEvent.value += 1L
        if (hasFocus) {
            try {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } catch (_: Exception) {}
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // If this activity is currently in Picture-in-Picture and another video was clicked, exit PiP and restart in full screen!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
            try {
                finishAndRemoveTask()
            } catch (_: Exception) {
                finish()
            }
            val restartIntent = Intent(intent).apply {
                setClass(this@VideoPlayerActivity, VideoPlayerActivity::class.java)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(restartIntent)
            return
        }
        val newUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
        val newHash = intent.getStringExtra(EXTRA_TORRENT_HASH)
        val newStream = intent.getBooleanExtra(EXTRA_IS_STREAMING, false)
        val oldStreamHash = streamTorrentHash
        if (isStreaming && !oldStreamHash.isNullOrBlank() && (!newStream || !oldStreamHash.equals(newHash, ignoreCase = true))) {
            com.example.ymediaplayer.mycinema.service.TorrentEngine.cleanupStream(this, oldStreamHash)
        }
        cinemaShowId = intent.getIntExtra(EXTRA_CINEMA_SHOW_ID, 0)
        cinemaSeason = intent.getIntExtra(EXTRA_CINEMA_SEASON, 0)
        cinemaEpisode = intent.getIntExtra(EXTRA_CINEMA_EPISODE, 0)
        cinemaIsSeries = intent.getBooleanExtra(EXTRA_CINEMA_IS_SERIES, false)
        if (newStream && !newHash.isNullOrBlank()) {
            streamTorrentHash = newHash
            isStreaming = true
            streamTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: ""
            streamPosterUrl = intent.getStringExtra(EXTRA_POSTER_URL) ?: ""
            streamBackdropUrl = intent.getStringExtra(EXTRA_BACKDROP_URL) ?: ""
            streamLogoUrl = intent.getStringExtra(EXTRA_LOGO_URL) ?: ""
            currentUrl = newUrl ?: ""
            requestSequence++
        } else {
            isStreaming = false
            streamTorrentHash = null
            streamTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: ""
            streamPosterUrl = intent.getStringExtra(EXTRA_POSTER_URL) ?: ""
            streamBackdropUrl = ""
            streamLogoUrl = ""
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
                    com.example.ymediaplayer.player.VideoPlaybackManager.manualPause()
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
        outState.putString(EXTRA_TORRENT_HASH, streamTorrentHash)
        outState.putBoolean(EXTRA_IS_STREAMING, isStreaming)
        outState.putString(EXTRA_VIDEO_TITLE, streamTitle)
        outState.putString(EXTRA_POSTER_URL, streamPosterUrl)
        outState.putString(EXTRA_BACKDROP_URL, streamBackdropUrl)
        outState.putString(EXTRA_LOGO_URL, streamLogoUrl)
        outState.putInt(EXTRA_CINEMA_SHOW_ID, cinemaShowId)
        outState.putInt(EXTRA_CINEMA_SEASON, cinemaSeason)
        outState.putInt(EXTRA_CINEMA_EPISODE, cinemaEpisode)
        outState.putBoolean(EXTRA_CINEMA_IS_SERIES, cinemaIsSeries)
        manager.player?.let { player ->
            val prefs = com.example.ymediaplayer.data.AppPreferences(this)
            val dur = player.duration.coerceAtLeast(0L)
            prefs.saveVideoProgress(manager.currentVideoUrl.value ?: currentUrl,
                player.currentPosition, dur)
            if (cinemaShowId > 0 && cinemaIsSeries && cinemaSeason > 0 && cinemaEpisode > 0 && player.currentPosition > 1000L) {
                prefs.saveEpisodeProgress(cinemaShowId, cinemaSeason, cinemaEpisode, player.currentPosition, dur)
            }
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
            com.example.ymediaplayer.player.VideoPlaybackManager.manualPause()
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
        if (activeInstance?.get() == this) {
            activeInstance = null
        }
        onUserLeaveHintListener = null
        if (!isChangingConfigurations) {
            if (isStreaming && !streamTorrentHash.isNullOrBlank()) {
                com.example.ymediaplayer.mycinema.service.TorrentEngine.cleanupStream(this, streamTorrentHash!!)
            }
            if (!com.example.ymediaplayer.player.VideoPlaybackManager.isMiniPlayerActive.value) {
                com.example.ymediaplayer.service.VideoPlaybackService.stopService(this)
                com.example.ymediaplayer.player.VideoPlaybackManager.releasePlayer()
            }
        }
    }
}
