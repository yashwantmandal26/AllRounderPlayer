package com.example.ymediaplayer.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ymediaplayer.AppRoot
import com.example.ymediaplayer.data.AppPreferences
import com.example.ymediaplayer.service.MusicService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import android.net.Uri
import com.example.ymediaplayer.data.MusicItem
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MusicHapticsControlTest {
    @get:Rule val rule = createComposeRule()

    @Test fun fullPlayerShowsHapticsAndAllFourSelectableSoundModes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("ymedia_prefs", android.content.Context.MODE_PRIVATE)
        val original = prefs.all.filterKeys { it == "music_sound_mode" || it.startsWith("eq_") }
        var selected by mutableStateOf(MusicSoundMode.ORIGINAL)
        val song = MusicItem(-1, Uri.EMPTY, "Beat haptics", "Studio preview", "Preview", -9999, 180000, 0)
        try {
            rule.setContent { AppRoot {
                FullScreenMusicPlayer(
                    song = song, isPlaying = false, currentPosition = 30000, duration = 180000,
                    isShuffle = false, repeatMode = 0, playbackSpeed = 1f, artworkStyle = "CARD",
                    sleepTimerSecondsRemaining = 0, isFavorite = false,
                    onPlayPause = {}, onNext = {}, onPrev = {}, onSeek = {}, onToggleShuffle = {},
                    onToggleRepeat = {}, onToggleArtworkStyle = {}, onFavoriteToggle = {}, onOpenQueue = {},
                    onOpenSleepTimer = {}, onOpenSpeed = {}, onOpenSongInfo = {}, onClose = {},
                    soundMode = selected, onSelectSoundMode = { selected = it; MusicService.applySoundMode(context, it) }
                )
            } }
            rule.onNodeWithTag("music_beat_haptics").assertIsDisplayed()
            val hapticBounds = rule.onNodeWithTag("music_beat_haptics").fetchSemanticsNode().boundsInRoot
            val heartBounds = rule.onNodeWithContentDescription("Favorite").fetchSemanticsNode().boundsInRoot
            assertTrue("Haptic icon should sit immediately right of Favorite", hapticBounds.left >= heartBounds.right - 1f)
            assertTrue(kotlin.math.abs(hapticBounds.center.y - heartBounds.center.y) < 2f)
            assertTrue("Haptics uses a compact square icon target", kotlin.math.abs(hapticBounds.width - hapticBounds.height) < 2f)
            assertTrue("Haptic target stays close to Favorite's size", hapticBounds.width <= heartBounds.width * 1.25f + 2f)
            rule.onNodeWithContentDescription("Queue").assertIsDisplayed()
            for (mode in MusicSoundMode.entries) {
                rule.onNodeWithContentDescription("Sound mode: ${selected.displayName}").performClick()
                rule.onNodeWithText(mode.description).assertIsDisplayed()
                rule.onNodeWithText(mode.description).performClick()
                rule.runOnIdle {
                    assertEquals(mode, MusicService.currentSoundMode.value)
                    assertEquals(mode != MusicSoundMode.ORIGINAL, MusicService.isEqEnabled.value)
                }
            }
            val folder = File(context.getExternalFilesDir(null), "song-haptics-qa").apply { mkdirs() }
            File(folder, "music-player.png").outputStream().use {
                rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            val edit = prefs.edit()
            (prefs.all.keys.filter { it == "music_sound_mode" || it.startsWith("eq_") }).forEach { edit.remove(it) }
            original.forEach { (key, value) -> when (value) {
                is String -> edit.putString(key, value)
                is Boolean -> edit.putBoolean(key, value)
                is Float -> edit.putFloat(key, value)
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
            } }
            edit.commit()
            MusicService.initPreferencesState(context)
        }
    }

    @Test fun togglePersistsAcrossRecreationAndIsIndependentOfMovieHaptics() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = AppPreferences(context)
        val original = prefs.isMusicHapticsEnabled()
        val movie = prefs.isVideoHapticsEnabled()
        val shown = mutableStateOf(true)
        try {
            MusicService.setMusicHapticsEnabled(context, false)
            rule.setContent { AppRoot { if (shown.value) MusicHapticsControl() } }
            rule.onNodeWithTag("music_beat_haptics").assertIsOff().performClick().assertIsOn()
            assertTrue(prefs.isMusicHapticsEnabled())
            assertEquals(movie, prefs.isVideoHapticsEnabled())
            rule.runOnIdle { shown.value = false }
            rule.waitForIdle()
            rule.runOnIdle { shown.value = true }
            rule.onNodeWithTag("music_beat_haptics").assertIsOn().performTouchInput { longClick() }
            rule.onNodeWithText("Haptic tuning").assertIsDisplayed()
            rule.onNodeWithText("Negative: vibration earlier. Positive: vibration later. Tune while your song plays.").assertIsDisplayed()
            rule.onNodeWithText("Done").performClick()
            rule.onNodeWithTag("music_beat_haptics").performClick().assertIsOff()
            assertFalse(prefs.isMusicHapticsEnabled())
        } finally {
            rule.runOnIdle { shown.value = false }
            MusicService.setMusicHapticsEnabled(context, original)
        }
    }
}
