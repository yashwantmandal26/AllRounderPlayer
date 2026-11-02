@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.example.ymediaplayer.ui

import android.content.Intent
import android.content.Context
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

class VideoInteractionRegressionTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val context = ins.targetContext
    private lateinit var scenario: ActivityScenario<VideoPlayerActivity>
    private lateinit var file: File
    private var oldQueue: List<VideoItem>? = null
    private lateinit var savedPreferences: Map<String, *>
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
        check(context.packageName.endsWith(".debug") ||
            InstrumentationRegistry.getArguments().getString("allowReleaseQa") == "true")
        savedPreferences = context.getSharedPreferences("ymedia_prefs", Context.MODE_PRIVATE).all.toMap()
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
        if (::savedPreferences.isInitialized) {
            val editor = context.getSharedPreferences("ymedia_prefs", Context.MODE_PRIVATE).edit().clear()
            savedPreferences.forEach { (key, value) ->
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
        rule.onNodeWithText("Sleep Timer").performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.onNodeWithText("15 Minutes").performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.onNodeWithContentDescription("More").performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.onNodeWithText("15m").assertExists()
        main { assertTrue(VideoPlaybackManager.sleepTimerSecondsRemaining.intValue > 850) }
        scenario.recreate()
        waitFor { VideoPlaybackManager.player != null }
        main {
            VideoPlaybackManager.player!!.pause()
            Log.i("VideoRegression", "Timer after recreate=${VideoPlaybackManager.sleepTimerSecondsRemaining.intValue}")
            assertTrue("Recreation must retain the running deadline", VideoPlaybackManager.sleepTimerSecondsRemaining.intValue > 0)
        }
        rule.waitForIdle()
        // Let the recreated window attach before sending a physical button tap.
        rule.mainClock.advanceTimeBy(250)
        if (rule.onAllNodesWithText("15m").fetchSemanticsNodes().isEmpty()) {
            rule.onNodeWithContentDescription("More").performClick()
        }
        ins.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(context.getExternalFilesDir(null), "video-timer-after-fixed.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("15m").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("15m").assertExists("Configured video sleep timer must survive Activity recreation")
    }
    @Test fun threeFingerGestureThenReleaseDoesNotFreezeControls() {
        exerciseSystemGesture(cancelGesture = false)
    }
    @Test fun threeFingerGestureCancellationDoesNotFreezeControls() {
        exerciseSystemGesture(cancelGesture = true)
    }
    private fun exerciseSystemGesture(cancelGesture: Boolean) {
        main { VideoPlaybackManager.player!!.pause() }
        rule.waitForIdle()
        rule.onRoot().performTouchInput {
            down(0, androidx.compose.ui.geometry.Offset(width * 0.30f, height * 0.42f))
            down(1, androidx.compose.ui.geometry.Offset(width * 0.50f, height * 0.42f))
            down(2, androidx.compose.ui.geometry.Offset(width * 0.70f, height * 0.42f))
            moveBy(0, androidx.compose.ui.geometry.Offset(0f, 100f))
            moveBy(1, androidx.compose.ui.geometry.Offset(0f, 100f))
            moveBy(2, androidx.compose.ui.geometry.Offset(0f, 100f))
            if (cancelGesture) cancel() else { up(2); up(1); up(0) }
        }
        // A new tap must toggle controls after either UPs or ACTION_CANCEL.
        Thread.sleep(400)
        rule.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.38f)) }
        rule.onNodeWithContentDescription("Play/Pause").assertDoesNotExist()
        Thread.sleep(400)
        rule.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.38f)) }
        rule.onNodeWithContentDescription("Play/Pause").assertIsDisplayed().performClick()
        waitFor { VideoPlaybackManager.player?.isPlaying == true }
        // The 16-ms progress poll intentionally keeps short playing videos busy.
        // Verify real playback, then pause before asking Espresso to await idle.
        main { VideoPlaybackManager.player!!.pause() }
        rule.onNodeWithContentDescription("More").performClick()
        rule.onNodeWithText("Sleep Timer").assertExists()
    }
    @Test fun videoTimerExpiresAfterRecreationWithoutRestartingTheDeadline() {
        main { VideoPlaybackManager.startSleepTimer(3000L) }
        scenario.recreate()
        waitFor { VideoPlaybackManager.player != null }
        main { VideoPlaybackManager.player!!.play() }
        waitFor { VideoPlaybackManager.player!!.isPlaying }
        waitFor { VideoPlaybackManager.sleepTimerSecondsRemaining.intValue == 0 }
        main { assertFalse(VideoPlaybackManager.player!!.playWhenReady) }
    }

}
