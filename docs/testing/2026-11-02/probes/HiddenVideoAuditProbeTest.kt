@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.example.ymediaplayer.ui

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ymediaplayer.data.AppPreferences
import com.example.ymediaplayer.data.VideoItem
import com.example.ymediaplayer.player.VideoPlaybackManager
import org.junit.*
import org.junit.Assert.*
import java.io.File

class HiddenVideoAuditProbeTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val context = ins.targetContext
    private lateinit var scenario: ActivityScenario<VideoPlayerActivity>
    private lateinit var file: File
    private var oldQueue: List<VideoItem>? = null
    private fun main(action: () -> Unit) = ins.runOnMainSync(action)
    private fun waitFor(condition: () -> Boolean) {
        val end = android.os.SystemClock.elapsedRealtime() + 15000
        while (android.os.SystemClock.elapsedRealtime() < end) {
            var ready = false
            main { ready = condition() }
            if (ready) return
            Thread.sleep(70)
        }
        error("Timed out awaiting video playback")
    }
    @Before fun setup() {
        check(context.packageName.endsWith(".debug"))
        file = File(context.filesDir, "QA_Hidden_Video.mp4")
        ins.context.assets.open("QA_Hidden_30s.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
        val uri = Uri.fromFile(file)
        main {
            VideoPlaybackManager.releasePlayer()
            oldQueue = VideoPlaybackManager.activePlaylistQueue
            VideoPlaybackManager.activePlaylistQueue = listOf(VideoItem(999001, uri, file.name, 30000, file.length(), "qa", "qa"))
            AppPreferences(context).saveVideoProgress(uri.toString(), 0, 30000)
        }
        scenario = ActivityScenario.launch(Intent(context, VideoPlayerActivity::class.java)
            .putExtra(VideoPlayerActivity.EXTRA_VIDEO_URL, uri.toString()))
        waitFor { VideoPlaybackManager.player?.isPlaying == true && VideoPlaybackManager.player!!.duration > 0 }
    }
    @After fun cleanup() {
        if (::scenario.isInitialized) scenario.close()
        main {
            VideoPlaybackManager.releasePlayer()
            VideoPlaybackManager.activePlaylistQueue = oldQueue
        }
        if (::file.isInitialized) file.delete()
    }
    @Test fun pausedSeekMustRemainPaused() {
        main { VideoPlaybackManager.player!!.pause() }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Play/Pause").assertExists()
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).performTouchInput {
            click(androidx.compose.ui.geometry.Offset(width * 0.5f, center.y))
        }
        Thread.sleep(350)
        main {
            val p = VideoPlaybackManager.player!!
            Log.i("HiddenAudit", "paused video seek position=${p.currentPosition} playWhenReady=${p.playWhenReady}")
            assertTrue("Seek must change position", p.currentPosition > 10000)
            assertFalse("Scrubbing paused video must preserve pause", p.playWhenReady)
        }
    }
    @Test fun videoSleepTimerMustSurviveActivityRecreation() {
        main { VideoPlaybackManager.player!!.pause() }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("More").performSemanticsAction(SemanticsActions.OnClick) { it() }
        Thread.sleep(500)
        rule.waitForIdle()
        Log.i("HiddenAudit", "before timer select: " + rule.onRoot().printToString())
        rule.onNodeWithText("Sleep Timer").performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.onNodeWithText("15 Minutes").performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.onNodeWithContentDescription("More").performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.onNodeWithText("15m").assertExists()
        scenario.recreate()
        waitFor { VideoPlaybackManager.player != null }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("More").performSemanticsAction(SemanticsActions.OnClick) { it() }
        Log.i("HiddenAudit", "video timer after recreation: " + rule.onRoot().printToString())
        rule.onNodeWithText("15m").assertExists("Configured video sleep timer must survive Activity recreation")
    }
}
