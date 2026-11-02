@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.example.ymediaplayer.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.media3.common.Player
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.material3.Text
import com.example.ymediaplayer.AppRoot
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ymediaplayer.data.AppPreferences
import com.example.ymediaplayer.data.MusicItem
import com.example.ymediaplayer.service.MusicService
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Real service regression tests with app-private generated audio fixtures. */
class MusicQueueAndTimerRegressionTest {
    @get:Rule val rule = createComposeRule()
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val context = ins.targetContext
    private lateinit var savedPreferences: Map<String, *>
    private lateinit var songs: List<MusicItem>
    private val files = mutableListOf<File>()
    private var originalFade = true
    private var originalAuto = true
    private fun main(action: () -> Unit) = ins.runOnMainSync(action)
    private fun waitFor(condition: () -> Boolean) {
        val end = android.os.SystemClock.elapsedRealtime() + 10000
        while (android.os.SystemClock.elapsedRealtime() < end) {
            var ready = false
            main { ready = condition() }
            if (ready) return
            Thread.sleep(50)
        }
        error("Timed out awaiting playback condition")
    }
    @Before fun setup() {
        check(context.packageName.endsWith(".debug") ||
            InstrumentationRegistry.getArguments().getString("allowReleaseQa") == "true")
        savedPreferences = context.getSharedPreferences("ymedia_prefs", Context.MODE_PRIVATE).all.toMap()
        val prefs = AppPreferences(context)
        originalFade = prefs.isSleepTimerFadeOutEnabled()
        originalAuto = prefs.isAutoPlayNextEnabled()
        prefs.setSleepTimerFadeOutEnabled(true)
        rule.setContent { AppRoot { Text("Music playback regression tests") } }
        songs = (1L..3L).map { id ->
            val file = File(context.filesDir, "hidden_qa_$id.mp3")
            files += file
            ins.context.assets.open("QA_A_30s.mp3").use { input -> file.outputStream().use { input.copyTo(it) } }
            MusicItem(990000L + id, Uri.fromFile(file), "QA_$id", "QA", "QA", 0, 30000, file.length())
        }
        main { context.startService(Intent(context, MusicService::class.java)) }
        waitFor { MusicService.playerInstance != null }
        main {
            MusicService.setSleepTimer(0)
            MusicService.setRepeatMode(Player.REPEAT_MODE_OFF)
            MusicService.setShuffleEnabled(false)
            MusicService.setAutoPlayEnabled(true)
            MusicService.currentPlaylist = songs
            MusicService.playSong(songs[0])
        }
        waitFor { MusicService.playerInstance?.isPlaying == true }
    }
    @After fun cleanup() {
        main {
            MusicService.setSleepTimer(0)
            MusicService.playerInstance?.volume = 1f
            MusicService.playerInstance?.pause()
            MusicService.playerInstance?.clearMediaItems()
            MusicService.currentPlaylist = emptyList()
            MusicService.setAutoPlayEnabled(originalAuto)
        }
        AppPreferences(context).setSleepTimerFadeOutEnabled(originalFade)
        main { MusicService.stopAndDismiss() }
        waitFor { MusicService.playerInstance == null }
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
        files.forEach { it.delete() }
    }
    @Test fun queueReorderingMustChangeTheActualNextTrack() {
        main {
            MusicService.playerInstance!!.pause()
            MusicService.reorderQueue(2, 1)
            val player = MusicService.playerInstance!!
            Log.i("HiddenAudit", "queue UI=${MusicService.currentPlaylist.map { it.title }} engine=${(0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaMetadata.title }}")
            MusicService.playNext()
            Log.i("HiddenAudit", "next after reorder=${MusicService.currentMusicItem.value?.title}")
            assertEquals("Next must respect reordered queue", songs[2].uri, MusicService.currentMusicItem.value?.uri)
        }
    }
    @Test fun cancellingFadingSleepTimerMustRestoreVolume() {
        main { MusicService.startSleepTimer(15000L) }
        waitFor { MusicService.playerInstance!!.volume < 0.9f }
        main {
            val before = MusicService.playerInstance!!.volume
            MusicService.setSleepTimer(0)
            Log.i("HiddenAudit", "cancel fade before=$before after=${MusicService.playerInstance!!.volume}")
            assertEquals("Cancel must restore pre-fade volume", 1f, MusicService.playerInstance!!.volume, 0.001f)
        }
    }
    @Test fun extendingFadingSleepTimerMustRestoreVolume() {
        main { MusicService.startSleepTimer(15000L) }
        waitFor { MusicService.playerInstance!!.volume < 0.9f }
        main { MusicService.setSleepTimer(15) }
        Thread.sleep(400)
        main {
            Log.i("HiddenAudit", "extended timer volume=${MusicService.playerInstance!!.volume}")
            assertEquals("Extending timer must stop old fade", 1f, MusicService.playerInstance!!.volume, 0.001f)
        }
    }
    @Test fun repeatOffMustNotWrapAtEndOfQueue() {
        main { MusicService.playSong(songs.last()) }
        waitFor { MusicService.playerInstance!!.isPlaying && MusicService.playerInstance!!.duration > 0 }
        main { MusicService.playerInstance!!.let { it.seekTo(it.duration - 250) } }
        Thread.sleep(1600)
        main {
            Log.i("HiddenAudit", "end with repeat OFF current=${MusicService.currentMusicItem.value?.title} playing=${MusicService.playerInstance!!.isPlaying}")
            assertEquals("Repeat OFF must end queue instead of wrapping", songs.last().uri, MusicService.currentMusicItem.value?.uri)
            assertFalse(MusicService.playerInstance!!.isPlaying)
        }
    }
    @Test fun enqueueCurrentSongMustNotCreateDuplicateQueueKeys() {
        main {
            MusicService.enqueueNext(songs[0])
            val ids = MusicService.currentPlaylist.map { "queue_${it.id}" }
            Log.i("HiddenAudit", "play current next queue keys=$ids")
            assertEquals("Queue UI uses song id as LazyColumn key; it must remain unique", ids.size, ids.distinct().size)
        }
    }
    @Test fun timerCancellationRestoresCustomVolumeAndPreservesUserChanges() {
        main { MusicService.playerInstance!!.volume = 0.6f; MusicService.startSleepTimer(15000L) }
        waitFor { MusicService.playerInstance!!.volume < 0.5f }
        main {
            MusicService.setSleepTimer(0)
            assertEquals(0.6f, MusicService.playerInstance!!.volume, 0.001f)
            MusicService.startSleepTimer(15000L)
        }
        waitFor { MusicService.playerInstance!!.volume < 0.5f }
        main {
            MusicService.playerInstance!!.volume = 0.35f
            MusicService.setSleepTimer(0)
            assertEquals(0.35f, MusicService.playerInstance!!.volume, 0.001f)
        }
    }
    @Test fun reorderingTheCurrentTrackPreservesItsPositionAndPauseState() {
        main { MusicService.playerInstance!!.pause(); MusicService.playerInstance!!.seekTo(8000L) }
        main {
            MusicService.reorderQueue(0, 2)
            val player = MusicService.playerInstance!!
            assertEquals(songs[0].id.toString(), player.currentMediaItem!!.mediaId)
            assertEquals(2, player.currentMediaItemIndex)
            assertEquals(2, MusicService.currentSongIndex)
            assertEquals(8000L, player.currentPosition)
            assertFalse(player.playWhenReady)
        }
    }
    @Test fun autoAdvanceStillPlaysTheNextTrackAndRepeatAllStillWraps() {
        main { MusicService.playerInstance!!.let { it.seekTo(it.duration - 250L) } }
        waitFor { MusicService.currentMusicItem.value?.uri == songs[1].uri }
        main { MusicService.setRepeatMode(Player.REPEAT_MODE_ALL); MusicService.playSong(songs.last()) }
        waitFor { MusicService.playerInstance!!.isPlaying && MusicService.playerInstance!!.duration > 0 }
        main { MusicService.playerInstance!!.let { it.seekTo(it.duration - 250L) } }
        waitFor { MusicService.currentMusicItem.value?.uri == songs[0].uri }
    }

}
