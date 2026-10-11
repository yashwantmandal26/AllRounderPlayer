package com.example.ymediaplayer.ui

import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.example.ymediaplayer.player.VideoPlaybackManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Native VLC/FFmpeg-backed fallback for media the platform decoder or Media3 extractor cannot
 * handle. It is deliberately isolated so normal playback keeps Media3's smaller, efficient path.
 */
class CompatibilityPlayerActivity : Activity() {
    private lateinit var libVlc: LibVLC
    private lateinit var vlcPlayer: MediaPlayer
    private lateinit var videoLayout: VLCVideoLayout
    private lateinit var playPause: Button
    private lateinit var seekBar: SeekBar
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var currentMedia: Media? = null
    private var initialPositionMs = 0L
    private var softwareRetryUsed = false
    private var userSeeking = false

    private val progressUpdater = object : Runnable {
        override fun run() {
            if (!userSeeking && ::vlcPlayer.isInitialized) {
                val length = vlcPlayer.length
                val time = vlcPlayer.time
                if (length > 0L && time >= 0L) {
                    seekBar.progress = ((time * SEEK_MAX) / length).toInt().coerceIn(0, SEEK_MAX)
                }
            }
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val uri = intent.getStringExtra(EXTRA_URI)
        if (uri.isNullOrBlank()) {
            finish()
            return
        }
        initialPositionMs = intent.getLongExtra(EXTRA_POSITION_MS, 0L).coerceAtLeast(0L)
        buildUi(intent.getStringExtra(EXTRA_TITLE).orEmpty())

        libVlc = LibVLC(
            applicationContext,
            arrayListOf(
                "--network-caching=3000",
                "--file-caching=1500",
                "--live-caching=3000",
                "--drop-late-frames",
                "--skip-frames",
                "--avcodec-skiploopfilter=4",
                "--no-video-title-show"
            )
        )
        vlcPlayer = MediaPlayer(libVlc).apply {
            attachViews(videoLayout, null, true, false)
            setEventListener { event -> onVlcEvent(event, uri) }
        }
        startPlayback(uri, forceSoftware = false, startPositionMs = initialPositionMs)
        handler.post(progressUpdater)
    }

    private fun buildUi(title: String) {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        videoLayout = VLCVideoLayout(this)
        root.addView(
            videoLayout,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 16, 24, 24)
            setBackgroundColor(0xB3000000.toInt())
        }
        val titleView = TextView(this).apply {
            text = if (title.isBlank()) "Compatibility player" else title
            setTextColor(Color.WHITE)
            textSize = 16f
            maxLines = 1
        }
        status = TextView(this).apply {
            text = "Extended codec mode"
            setTextColor(0xFFB9C6D8.toInt())
            textSize = 12f
        }
        seekBar = SeekBar(this).apply {
            max = SEEK_MAX
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar?) { userSeeking = true }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val length = vlcPlayer.length
                    if (length > 0L) vlcPlayer.time = (length * (seekBar?.progress ?: 0)) / SEEK_MAX
                    userSeeking = false
                }
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = Unit
            })
        }
        playPause = Button(this).apply {
            text = "Pause"
            setOnClickListener {
                if (vlcPlayer.isPlaying) {
                    vlcPlayer.pause()
                } else {
                    vlcPlayer.play()
                }
            }
        }
        val close = Button(this).apply {
            text = "Back"
            setOnClickListener { finish() }
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(playPause)
            addView(close)
        }
        controls.addView(titleView, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        controls.addView(status, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        controls.addView(seekBar, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        controls.addView(buttons, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        root.addView(
            controls,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )
        setContentView(root)
    }

    private fun startPlayback(uri: String, forceSoftware: Boolean, startPositionMs: Long) {
        currentMedia?.release()
        val media = Media(libVlc, Uri.parse(uri)).apply {
            setHWDecoderEnabled(!forceSoftware, false)
            addOption(":network-caching=3000")
            addOption(":file-caching=1500")
            addOption(":drop-late-frames")
            addOption(":skip-frames")
        }
        currentMedia = media
        vlcPlayer.media = media
        vlcPlayer.play()
        if (startPositionMs > 0L) {
            handler.postDelayed({
                if (::vlcPlayer.isInitialized && vlcPlayer.isSeekable) vlcPlayer.time = startPositionMs
            }, 400L)
        }
    }

    private fun onVlcEvent(event: MediaPlayer.Event, uri: String) {
        runOnUiThread {
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    playPause.text = "Pause"
                    status.text = if (softwareRetryUsed) "Software decoder • damaged-file recovery" else "Extended codec mode"
                }
                MediaPlayer.Event.Paused -> playPause.text = "Play"
                MediaPlayer.Event.EncounteredError -> {
                    if (!softwareRetryUsed) {
                        softwareRetryUsed = true
                        val resumeAt = vlcPlayer.time.coerceAtLeast(initialPositionMs)
                        status.text = "Hardware decoder failed; trying software decoder"
                        startPlayback(uri, forceSoftware = true, startPositionMs = resumeAt)
                    } else {
                        status.text = "The readable portion of this file could not be recovered"
                        Toast.makeText(this, status.text, Toast.LENGTH_LONG).show()
                    }
                }
                MediaPlayer.Event.EndReached -> {
                    playPause.text = "Replay"
                    status.text = "Playback finished"
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (::vlcPlayer.isInitialized && vlcPlayer.isPlaying) vlcPlayer.pause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::vlcPlayer.isInitialized) {
            VideoPlaybackManager.currentPosition.value = vlcPlayer.time.coerceAtLeast(0L)
            vlcPlayer.setEventListener(null)
            vlcPlayer.detachViews()
            vlcPlayer.release()
        }
        currentMedia?.release()
        if (::libVlc.isInitialized) libVlc.release()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URI = "compatibility_uri"
        const val EXTRA_TITLE = "compatibility_title"
        const val EXTRA_POSITION_MS = "compatibility_position_ms"
        private const val SEEK_MAX = 10_000
    }
}
