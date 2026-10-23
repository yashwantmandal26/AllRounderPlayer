package com.example.ymediaplayer.mycinema.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.ymediaplayer.MainActivity
import com.example.ymediaplayer.R
import com.example.ymediaplayer.ui.VideoPlayerActivity
import kotlinx.coroutines.*
import java.io.File

class TorrentDownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "mycinema_download_channel"
        const val NOTIFICATION_ID = 9021

        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_CANCEL = "ACTION_CANCEL"

        const val EXTRA_HASH = "EXTRA_HASH"
        const val EXTRA_TITLE = "EXTRA_TITLE"

        fun start(context: Context, hash: String, title: String) {
            val intent = Intent(context, TorrentDownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_HASH, hash)
                putExtra(EXTRA_TITLE, title)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var tickerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "YMediaPlayer:TorrentDownloadWakeLock").apply {
                    setReferenceCounted(false)
                    acquire(8 * 3600 * 1000L) // 8 hours max
                }
            }
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        val hash = intent.getStringExtra(EXTRA_HASH) ?: ""
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Movie Download"

        when (action) {
            ACTION_START -> {
                acquireWakeLock()
                startForeground(
                    NOTIFICATION_ID,
                    buildInitialNotification(title)
                )
                startTicker()
            }
            ACTION_PAUSE -> {
                if (hash.isNotBlank()) TorrentEngine.pause(hash)
            }
            ACTION_RESUME -> {
                if (hash.isNotBlank()) TorrentEngine.resume(hash)
            }
            ACTION_CANCEL -> {
                if (hash.isNotBlank()) TorrentEngine.cancel(hash)
                val remaining = TorrentEngine.downloads.value.values.any {
                    it.status == TorrentDownloadState.Status.DOWNLOADING ||
                    it.status == TorrentDownloadState.Status.CONNECTING ||
                    it.status == TorrentDownloadState.Status.STREAMING
                }
                if (!remaining) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                TorrentEngine.updateProgressTicks()
                val activeList = TorrentEngine.downloads.value.values.toList()

                val downloading = activeList.firstOrNull {
                    it.status == TorrentDownloadState.Status.DOWNLOADING ||
                    it.status == TorrentDownloadState.Status.CONNECTING ||
                    it.status == TorrentDownloadState.Status.STREAMING
                }

                if (downloading != null) {
                    val notification = buildProgressNotification(downloading)
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, notification)
                } else {
                    val anyActive = activeList.any {
                        it.status != TorrentDownloadState.Status.FINISHED &&
                        it.status != TorrentDownloadState.Status.ERROR
                    }

                    if (!anyActive && activeList.isNotEmpty()) {
                        // Scan finished files into MediaStore
                        for (d in activeList) {
                            if (d.status == TorrentDownloadState.Status.FINISHED) {
                                val path = d.savePath ?: TorrentEngine.getFilePath(this@TorrentDownloadService, d.id)
                                if (path != null) {
                                    MediaScannerConnection.scanFile(this@TorrentDownloadService, arrayOf(path), null) { _, _ -> }
                                    showCompletedNotification(d.title, path)
                                }
                            }
                        }
                        releaseWakeLock()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        break
                    }
                }
            }
        }
    }

    private fun buildInitialNotification(title: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🍿 MYCinema: $title")
            .setContentText("Connecting to peers & starting download...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setProgress(100, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun buildProgressNotification(download: TorrentDownloadState): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Pause / Resume Action
        val isPaused = download.status == TorrentDownloadState.Status.PAUSED
        val toggleAction = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        val toggleIntent = Intent(this, TorrentDownloadService::class.java).apply {
            action = toggleAction
            putExtra(EXTRA_HASH, download.id)
        }
        val togglePending = PendingIntent.getService(
            this,
            101,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cancel Action
        val cancelIntent = Intent(this, TorrentDownloadService::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_HASH, download.id)
        }
        val cancelPending = PendingIntent.getService(
            this,
            102,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val speedText = "⬇ ${download.speedFormatted}"
        val sizeText = download.sizeFormatted
        val etaText = download.etaFormatted
        val shortContent = "$speedText • $sizeText (${download.progressPercent}%)"
        val subText = "$etaText • ${download.seeds} seeds"

        val bigText = "Speed: $speedText (${download.seeds} seeds)\n" +
                "Progress: $sizeText (${download.progressPercent}%)\n" +
                "Time Left: $etaText"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🍿 MYCinema: ${download.title}")
            .setContentText(shortContent)
            .setSubText(subText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setProgress(100, download.progressPercent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (isPaused) "Resume" else "Pause",
                togglePending
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                cancelPending
            )
            .build()
    }

    private fun showCompletedNotification(title: String, filePath: String) {
        val playIntent = Intent(this, VideoPlayerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(VideoPlayerActivity.EXTRA_VIDEO_URL, filePath)
        }
        val playPending = PendingIntent.getActivity(
            this,
            201,
            playIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🍿 Download Complete!")
            .setContentText("\"$title\" is ready for offline playback")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(playPending)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_media_play, "Play Movie", playPending)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MYCinema Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live download speed, ETA, and progress from MYCinema"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
