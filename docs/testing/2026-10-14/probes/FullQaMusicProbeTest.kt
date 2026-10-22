@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ymediaplayer.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.common.Player
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ymediaplayer.AppRoot
import com.example.ymediaplayer.MainActivity
import com.example.ymediaplayer.data.AppPreferences
import com.example.ymediaplayer.data.MusicItem
import com.example.ymediaplayer.data.MusicRepository
import com.example.ymediaplayer.service.MusicService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * TEMPORARY full-app QA contract probes. These intentionally assert the expected
 * behavior, so known defects should fail at the final contract assertion.
 * Uses generated QA media only; never renames, removes, or modifies media files.
 */
class FullQaMusicProbeTest {
    @get:Rule val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val mounted = mutableStateOf(true)
    private lateinit var fixture: MusicItem
    private lateinit var preferences: SharedPreferences
    private var originalPreferenceValues: Map<String, Any?> = emptyMap()
    private val fixtureTitle = "AllRounder_QA_A"

    @Before fun prepareGeneratedFixture() {
        MusicRepository.invalidateCache()
        fixture = runBlocking {
            MusicRepository(context).getMusicFiles(forceRefresh = true)
                .firstOrNull {
                    it.title == fixtureTitle &&
                        (it.path.contains("AllRounder-QA-20261014") ||
                            it.relativePath.contains("AllRounder-QA-20261014"))
                }
        } ?: error("Generated fixture $fixtureTitle is not visible; check audio permission and MediaStore scan")

        preferences = context.getSharedPreferences("ymedia_prefs", Context.MODE_PRIVATE)
        val keys = listOf("music_played_uris", "played_songs_uris", "play_count_${fixture.uri}")
        originalPreferenceValues = keys.associateWith { preferences.all[it] }
        val previousHistory = preferences.getString("music_played_uris", "").orEmpty()
        preferences.edit().putString("music_played_uris",
            previousHistory.split(";;;").filter { it.isNotBlank() && it != fixture.uri.toString() }
                .joinToString(";;;"))
            .commit()

        rule.runOnIdle {
            MainActivity.openMusicTrigger.value = false
            MusicService.playerInstance?.let {
                it.pause()
                it.clearMediaItems()
                it.repeatMode = Player.REPEAT_MODE_OFF
                it.shuffleModeEnabled = false
            }
            MusicService.currentMusicItem.value = null
            MusicService.nowPlayingTitle.value = ""
            MusicService.nowPlayingArtist.value = ""
            MusicService.nowPlayingArtUri.value = null
            mounted.value = true
        }
        rule.setContent {
            AppRoot {
                if (mounted.value) {
                    MusicScreen(hasMediaPermission = true)
                } else {
                    Box(Modifier.fillMaxSize()) { Text("Music screen detached for QA") }
                }
            }
        }
        rule.waitUntil(timeoutMillis = 15_000) {
            rule.runOnIdle {
                MusicService.playerInstance != null && MusicService.onPlayNextAction != null
            }
        }
    }

    @After fun restoreQaPreferenceChanges() {
        rule.runOnIdle {
            MainActivity.openMusicTrigger.value = false
            if (::fixture.isInitialized && MusicService.currentMusicItem.value?.uri == fixture.uri) {
                MusicService.playerInstance?.pause()
            }
            mounted.value = false
        }
        if (::preferences.isInitialized) {
            val editor = preferences.edit()
            originalPreferenceValues.forEach { (key, value) ->
                when (value) {
                    null -> editor.remove(key)
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                    else -> error("Unexpected preference type for $key: ${value.javaClass}")
                }
            }
            editor.commit()
        }
    }

    @Test fun uiPlaybackShouldAddGeneratedTrackToRecentlyPlayed() {
        playFixtureThroughSearchUi()
        rule.onNodeWithContentDescription("Close").performClick()
        rule.onNode(hasText("Recent (", substring = true)).performTouchInput { click() }
        rule.onNodeWithText("Recently Played").assertExists()
        saveScreenshot("fullqa-music-recent.png")
        val recent = AppPreferences(context).getRecentlyPlayedMusicUris()
        Log.i("FullQaMusicProbe", "Recent after real UI playback: $recent; expected ${fixture.uri}")
        assertTrue("Playing $fixtureTitle through the actual Music UI must add it to Recently Played; actual=$recent",
            recent.contains(fixture.uri.toString()))
    }

    @Test fun repeatOneUiShouldUpdatePlaybackServiceRepeatMode() {
        playFixtureThroughSearchUi()
        openFullPlayer()
        rule.onNodeWithContentDescription("Repeat").performClick()
        rule.onNodeWithContentDescription("Repeat").performClick()
        saveScreenshot("fullqa-music-repeat-one.png")
        val actual = rule.runOnIdle { requireNotNull(MusicService.playerInstance).repeatMode }
        Log.i("FullQaMusicProbe", "Repeat One selected in UI; service repeatMode=$actual")
        assertEquals("Selecting Repeat One in the actual UI must update the playback service",
            Player.REPEAT_MODE_ONE, actual)
    }

    @Test fun sleepTimerShouldSurviveMusicScreenDetachAndRemount() {
        playFixtureThroughSearchUi()
        openFullPlayer()
        rule.onNodeWithContentDescription("Sleep Timer").performClick()
        rule.onNodeWithText("15 minutes").performClick()
        rule.onNode(hasText("Timer:", substring = true)).assertExists()
        saveScreenshot("fullqa-music-timer-before-detach.png")

        rule.runOnIdle { mounted.value = false }
        rule.onNodeWithText("Music screen detached for QA").assertExists()
        rule.runOnIdle { mounted.value = true }
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.runOnIdle { MusicService.onPlayNextAction != null }
        }
        openFullPlayer()
        saveScreenshot("fullqa-music-timer-after-remount.png")
        rule.onNode(hasText("Timer:", substring = true))
            .assertExists("A running 15-minute sleep timer must survive leaving and reopening Music")
    }

    private fun playFixtureThroughSearchUi() {
        rule.onNodeWithText("Search songs, artists, albums...").performClick()
        rule.onNode(hasSetTextAction()).performTextInput(fixtureTitle)
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Play", useUnmergedTree = false).fetchSemanticsNodes().isNotEmpty()
        }
        // This is the real search-results action, which invokes playSongForce().
        rule.onNodeWithText("Play").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.runOnIdle {
                val player = MusicService.playerInstance
                player != null && player.currentMediaItem?.mediaId == fixture.id.toString() && player.isPlaying
            }
        }
        // Keep the generated 30-second fixture away from an auto-next transition
        // while inspecting navigation and controls.
        rule.runOnIdle { MusicService.playerInstance?.pause() }
    }

    private fun openFullPlayer() {
        rule.runOnIdle { MainActivity.openMusicTrigger.value = true }
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithContentDescription("Repeat").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription("Repeat").assertIsDisplayed()
    }

    private fun saveScreenshot(name: String) {
        val file = File(context.getExternalFilesDir(null), name)
        file.outputStream().use { output ->
            rule.onRoot().captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        Log.i("FullQaMusicProbe", "Screenshot: ${file.absolutePath}")
    }
}
