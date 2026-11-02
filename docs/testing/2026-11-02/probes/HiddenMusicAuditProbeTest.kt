@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.example.ymediaplayer.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.media3.common.Player
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ymediaplayer.MainActivity
import com.example.ymediaplayer.data.AppPreferences
import com.example.ymediaplayer.data.MusicItem
import com.example.ymediaplayer.service.MusicService
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Audit probes: intentionally assert correct behavior to expose defects. */
class HiddenMusicAuditProbeTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val context = ins.targetContext
    private lateinit var scenario: ActivityScenario<MainActivity>
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
        check(context.packageName.endsWith(".debug"))
        val prefs = AppPreferences(context)
        originalFade = prefs.isSleepTimerFadeOutEnabled()
        originalAuto = prefs.isAutoPlayNextEnabled()
        prefs.setSleepTimerFadeOutEnabled(true)
        scenario = ActivityScenario.launch(MainActivity::class.java)
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
        if (::scenario.isInitialized) scenario.close()
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
}
