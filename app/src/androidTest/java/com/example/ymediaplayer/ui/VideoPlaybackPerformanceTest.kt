@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ymediaplayer.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ymediaplayer.data.AppPreferences
import com.example.ymediaplayer.data.VideoItem
import com.example.ymediaplayer.player.VideoPlaybackManager
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** Repeatable on-device comparison, not a universal frame-time threshold. Keeps all effects enabled. */
class VideoPlaybackPerformanceTest {
    @Test fun recordPlaybackFramesWithAndWithoutHaptics() {
        val ins = InstrumentationRegistry.getInstrumentation()
        check(InstrumentationRegistry.getArguments().getString("allowReleaseQa") == "true")
        for (command in listOf("input keyevent KEYCODE_WAKEUP", "wm dismiss-keyguard")) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(ins.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        }
        val context = ins.targetContext
        val prefs = context.getSharedPreferences("ymedia_prefs", Context.MODE_PRIVATE)
        val saved = prefs.all.toMap()
        val oldQueue = VideoPlaybackManager.activePlaylistQueue
        val file = File(context.filesDir, "QA_Performance.mp4")
        val reference = InstrumentationRegistry.getArguments().getString("performanceVideoPath")
        (if (reference != null) File(reference).inputStream() else ins.context.assets.open("QA_Hidden_30s.mp4"))
            .use { input -> file.outputStream().use(input::copyTo) }
        val worker = HandlerThread("FrameMetricsProbe").apply { start() }
        var scenario: ActivityScenario<VideoPlayerActivity>? = null
        try {
            prefs.edit().putLong("controls_auto_hide_timeout_ms", 60000).putBoolean("keep_screen_awake", true).apply()
            ins.runOnMainSync {
                VideoPlaybackManager.releasePlayer()
                VideoPlaybackManager.activePlaylistQueue = listOf(VideoItem(999002, Uri.fromFile(file), file.name, 30000, file.length(), "qa", "qa"))
                AppPreferences(context).saveVideoProgress(Uri.fromFile(file).toString(), 0, 30000)
            }
            scenario = ActivityScenario.launch(Intent(context, VideoPlayerActivity::class.java)
                .putExtra(VideoPlayerActivity.EXTRA_VIDEO_URL, Uri.fromFile(file).toString()))
            Thread.sleep(2500)
            val underruns = java.util.concurrent.atomic.AtomicInteger()
            val analytics = object : androidx.media3.exoplayer.analytics.AnalyticsListener {
                override fun onAudioUnderrun(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                    bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) { underruns.incrementAndGet() }
            }
            ins.runOnMainSync { VideoPlaybackManager.player!!.addAnalyticsListener(analytics) }
            val frames = CopyOnWriteArrayList<Long>()
            val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
                frames.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION))
            }
            scenario.onActivity { it.window.addOnFrameMetricsAvailableListener(listener, Handler(worker.looper)) }
            for (enabled in listOf(false, true)) {
                ins.runOnMainSync {
                    VideoPlaybackManager.setHapticsEnabled(enabled)
                    if (reference != null) VideoPlaybackManager.player!!.seekTo(30000)
                }
                Thread.sleep(if (reference != null) 1000 else 500)
                frames.clear()
                var cpuStart = 0L
                var cpuEnd = 0L
                var droppedStart = 0
                var renderedStart = 0
                var dropped = 0
                var rendered = 0
                val underrunsStart = underruns.get()
                ins.runOnMainSync {
                    cpuStart = Debug.threadCpuTimeNanos()
                    VideoPlaybackManager.player!!.videoDecoderCounters?.let {
                        it.ensureUpdated(); droppedStart = it.droppedBufferCount; renderedStart = it.renderedOutputBufferCount
                    }
                }
                val start = SystemClock.elapsedRealtime()
                Thread.sleep(7000)
                ins.runOnMainSync {
                    cpuEnd = Debug.threadCpuTimeNanos()
                    assertTrue("Playback must continue", VideoPlaybackManager.player!!.isPlaying)
                    VideoPlaybackManager.player!!.videoDecoderCounters?.let {
                        it.ensureUpdated(); dropped = it.droppedBufferCount - droppedStart
                        rendered = it.renderedOutputBufferCount - renderedStart
                    }
                }
                val sorted = frames.sorted()
                assertTrue("Expected rendered controls", sorted.size > 30)
                Log.i("PlaybackPerf", "haptics=$enabled frames=${sorted.size} mainCpuMs=${(cpuEnd - cpuStart) / 1000000} wallMs=${SystemClock.elapsedRealtime() - start} p50Ms=${sorted[sorted.size / 2] / 1000000.0} p95Ms=${sorted[(sorted.size * 0.95).toInt()] / 1000000.0} over16ms=${sorted.count { it > 16666667 }} videoRendered=$rendered videoDropped=$dropped audioUnderruns=${underruns.get() - underrunsStart}")
            }
            ins.runOnMainSync { VideoPlaybackManager.player!!.removeAnalyticsListener(analytics) }
            scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
        } finally {
            scenario?.close()
            ins.runOnMainSync {
                VideoPlaybackManager.releasePlayer()
                VideoPlaybackManager.activePlaylistQueue = oldQueue
            }
            worker.quitSafely()
            file.delete()
            val editor = prefs.edit().clear()
            saved.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            editor.commit()
            AppPreferences.clearCache()
        }
    }
}
