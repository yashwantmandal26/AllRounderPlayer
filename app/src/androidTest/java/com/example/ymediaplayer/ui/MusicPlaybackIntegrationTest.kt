@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ymediaplayer.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.background
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
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real UI/service regressions using media owned and cleaned up by the debug test app. */
class MusicPlaybackIntegrationTest {
    @get:Rule val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val mounted = mutableStateOf(true)
    private lateinit var fixture: MusicItem
    private lateinit var preferences: SharedPreferences
    private var originalPreferenceValues: Map<String, Any?> = emptyMap()
    private val fixtureTitle = "QA_" + System.nanoTime().toString().takeLast(12)
    private val createdUris = mutableListOf<android.net.Uri>()
    private lateinit var secondFixture: MusicItem
    private lateinit var themeController: com.example.ymediaplayer.theme.ThemeController

    private fun createFixture(asset: String): android.net.Uri {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, "AllRounder_Test_${System.nanoTime()}_$asset")
            put(android.provider.MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                put(android.provider.MediaStore.Audio.Media.RELATIVE_PATH, "Music/AllRounder-Tests/")
                put(android.provider.MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }
        val uri = requireNotNull(context.contentResolver.insert(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values))
        createdUris += uri
        val title = if (asset.startsWith("QA_A")) fixtureTitle else fixtureTitle.dropLast(1) + "B"
        // Replace the equal-length ASCII ID3 title too, so asynchronous scanning
        // cannot restore a title that collides with a previous run's fixture.
        val originalTitle = if (asset.startsWith("QA_A")) "AllRounder_QA_A" else "AllRounder_QA_B"
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open(asset).use { it.readBytes() }
        val tagged = String(bytes, Charsets.ISO_8859_1).replace(originalTitle, title).toByteArray(Charsets.ISO_8859_1)
        check(tagged.size == bytes.size)
        requireNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(tagged) }
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            context.contentResolver.update(uri, android.content.ContentValues().apply {
                put(android.provider.MediaStore.Audio.Media.IS_PENDING, 0)
            }, null, null)
        }
        // Known fixture metadata avoids scanner timing and collisions with old QA
        // tracks retained by the production library's duplicate suppression.
        context.contentResolver.update(uri, android.content.ContentValues().apply {
            put(android.provider.MediaStore.Audio.Media.TITLE,
                title)
            put(android.provider.MediaStore.Audio.Media.DURATION, 30_000L)
        }, null, null)
        return uri
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun grantPermissionsBeforeComposeHostLaunches() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                automation.grantRuntimePermission(context.packageName, android.Manifest.permission.READ_MEDIA_AUDIO)
                automation.grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                automation.grantRuntimePermission(context.packageName, android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    @Before fun prepareGeneratedFixture() {
        check(context.packageName.endsWith(".debug") ||
            InstrumentationRegistry.getArguments().getString("allowReleaseQa") == "true") {
            "Regular-app QA requires an explicit allowReleaseQa instrumentation argument"
        }
        val firstUri = createFixture("QA_A_30s.mp3")
        val secondUri = createFixture("QA_B_30s.mp3")
        MusicRepository.invalidateCache()
        val songs = runBlocking { MusicRepository(context).getMusicFiles(forceRefresh = true) }
        for (uri in listOf(firstUri, secondUri)) {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val details = listOf("_id", "title", "duration", "_size", "_data").joinToString { key ->
                        val index = cursor.getColumnIndex(key)
                        "$key=${if (index >= 0) cursor.getString(index) else "?"}"
                    }
                    Log.i("MusicRegression", "Generated fixture $uri: $details")
                }
            }
        }
        // Some providers return external_primary URIs on insert while the
        // repository uses the equivalent external-volume URI for the same ID.
        fixture = songs.first { it.id == android.content.ContentUris.parseId(firstUri) }
        secondFixture = songs.first { it.id == android.content.ContentUris.parseId(secondUri) }

        preferences = context.getSharedPreferences("ymedia_prefs", Context.MODE_PRIVATE)
        originalPreferenceValues = preferences.all.toMap()
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
            MusicService.isMiniPlayerDismissed.value = false
            mounted.value = true
        }
        rule.setContent {
            AppRoot {
                themeController = com.example.ymediaplayer.theme.LocalThemeController.current
                Box(Modifier.fillMaxSize().background(com.example.ymediaplayer.theme.LocalAppColors.current.baseBackground)) {
                    if (mounted.value) {
                        MusicScreen(hasMediaPermission = true)
                    } else {
                        Text("Music screen detached for QA")
                    }
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
            MusicService.setSleepTimer(0)
            MusicService.setAutoPlayEnabled(AppPreferences(context).isAutoPlayNextEnabled())
            MusicService.playerInstance?.pause()
            mounted.value = false
            MusicService.currentPlaylist = emptyList()
            MusicService.currentMusicItem.value = null
            MusicService.playerInstance?.clearMediaItems()
        }
        if (::preferences.isInitialized) {
            val editor = preferences.edit().clear()
            originalPreferenceValues.forEach { (key, value) ->
                when (value) {
                    null -> editor.remove(key)
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
        createdUris.forEach { context.contentResolver.delete(it, null, null) }
        MusicRepository.invalidateCache()
    }

    @Test fun uiPlaybackShouldAddGeneratedTrackToRecentlyPlayed() {
        playFixtureThroughSearchUi()
        rule.onNodeWithContentDescription("Clear").performClick()
        rule.onNode(hasText("Recent (", substring = true)).performTouchInput { click() }
        rule.onNodeWithText("Recently Played").assertExists()
        saveScreenshot("fullqa-music-recent.png")
        val recent = AppPreferences(context).getRecentlyPlayedMusicUris()
        Log.i("FullQaMusicProbe", "Recent after real UI playback: $recent; expected ${fixture.uri}")
        assertTrue("Playing $fixtureTitle through the actual Music UI must add it to Recently Played; actual=$recent",
            recent.contains(fixture.uri.toString()))
    }

    @Test fun whiteAccentSearchPlayButtonHasVisibleDarkContent() {
        val oldMode = rule.runOnIdle { themeController.mode }
        val oldColor = rule.runOnIdle { themeController.colorTheme }
        try {
            rule.runOnIdle {
                themeController.updateMode(com.example.ymediaplayer.theme.ThemeMode.OLED_BLACK)
                themeController.updateColorTheme(PlayerTheme.WHITE)
            }
            rule.onNodeWithText("Search songs, artists, albums...").performClick()
            rule.onNode(hasSetTextAction()).performTextInput(fixtureTitle)
            rule.waitUntil(10_000) { rule.onAllNodesWithText("Play").fetchSemanticsNodes().isNotEmpty() }
            val bitmap = rule.onNodeWithText("Play").captureToImage().asAndroidBitmap()
            var darkPixels = 0
            for (y in bitmap.height / 5 until bitmap.height * 4 / 5) {
                for (x in bitmap.width / 10 until bitmap.width * 9 / 10) {
                    val pixel = bitmap.getPixel(x, y)
                    if (android.graphics.Color.red(pixel) < 80 && android.graphics.Color.green(pixel) < 80 &&
                        android.graphics.Color.blue(pixel) < 100) darkPixels++
                }
            }
            assertTrue("White Play button must contain visible dark text/icon; darkPixels=$darkPixels", darkPixels > 20)
            saveScreenshot("music-white-accent-fixed.png")
        } finally {
            rule.runOnIdle {
                themeController.updateMode(oldMode)
                themeController.updateColorTheme(oldColor)
            }
        }
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

    @Test fun playNextOnCurrentSongThenOpeningQueueDoesNotCrash() {
        playFixtureThroughSearchUi()
        val queueBefore = rule.runOnIdle { MusicService.currentPlaylist.map { it.uri } }
        rule.onNodeWithContentDescription("Options").performClick()
        rule.onNodeWithText("Play Next").performClick()
        openFullPlayer()
        rule.onNodeWithContentDescription("Queue").performClick()
        rule.onNode(hasText("Up Next (", substring = true)).assertIsDisplayed()
        rule.runOnIdle {
            assertEquals(queueBefore, MusicService.currentPlaylist.map { it.uri })
        }
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

    @Test fun activeQueueSurvivesLeavingMusicAndNextUsesThatQueue() {
        playFixtureThroughSearchUi()
        val queue = listOf(secondFixture, fixture)
        rule.runOnIdle {
            MusicService.currentPlaylist = queue
            MusicService.currentSongIndex = 1
            MusicService.playSong(fixture)
            MusicService.playerInstance?.pause()
            mounted.value = false
        }
        rule.onNodeWithText("Music screen detached for QA").assertExists()
        rule.runOnIdle { mounted.value = true }
        rule.waitUntil(10_000) { rule.runOnIdle { MusicService.onPlayNextAction != null } }
        rule.runOnIdle {
            assertEquals(queue.map { it.uri }, MusicService.currentPlaylist.map { it.uri })
            MusicService.playNext()
        }
        rule.waitUntil(10_000) { rule.runOnIdle { MusicService.currentMusicItem.value?.uri == secondFixture.uri } }
        rule.runOnIdle { assertEquals(queue.map { it.uri }, MusicService.currentPlaylist.map { it.uri }) }
    }

    @Test fun serviceSleepTimerPausesWithoutMusicScreen() {
        playFixtureThroughSearchUi()
        rule.runOnIdle {
            mounted.value = false
            MusicService.playerInstance?.play()
            MusicService.startSleepTimer(400L)
        }
        rule.waitUntil(10_000) { rule.runOnIdle {
            MusicService.sleepTimerSecondsRemaining.intValue == 0 && MusicService.playerInstance?.isPlaying == false
        } }
    }

    @Test fun autoPlayOffPausesAtBoundaryWhileKeepingTheSelectedQueue() {
        playFixtureThroughSearchUi()
        val queue = listOf(fixture, secondFixture)
        rule.runOnIdle {
            MusicService.currentPlaylist = queue
            MusicService.setRepeatMode(Player.REPEAT_MODE_OFF)
            MusicService.setShuffleEnabled(false)
            MusicService.setAutoPlayEnabled(false)
            MusicService.playSong(fixture)
            mounted.value = false
        }
        rule.waitUntil(10_000) { rule.runOnIdle {
            val player = MusicService.playerInstance
            player != null && player.isPlaying && player.duration > 0
        } }
        rule.runOnIdle { MusicService.playerInstance?.let { it.seekTo(it.duration - 250L) } }
        rule.waitUntil(10_000) { rule.runOnIdle { MusicService.playerInstance?.isPlaying == false } }
        rule.runOnIdle { assertEquals(queue.map { it.uri }, MusicService.currentPlaylist.map { it.uri }) }
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
