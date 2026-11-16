package com.example.ymediaplayer.mycinema

import com.example.ymediaplayer.mycinema.service.TorrentDownloadState
import com.example.ymediaplayer.mycinema.service.TorrentEngine
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TorrentStateStabilityTest {
    @Test fun concurrentNativeAlertsDoNotLoseProgressUpdates() {
        val field = TorrentEngine::class.java.getDeclaredField("_downloads").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val states = field.get(TorrentEngine) as MutableStateFlow<Map<String, TorrentDownloadState>>
        val original = states.value
        val update = TorrentEngine::class.java.declaredMethods.single { it.name == "updateState" }.apply { isAccessible = true }
        val workers = Executors.newFixedThreadPool(4)
        val start = CountDownLatch(1)
        try {
            states.value = mapOf("a" to TorrentDownloadState("a", "A", "magnet"),
                "b" to TorrentDownloadState("b", "B", "magnet"))
            val jobs = (0..3).map { worker -> workers.submit {
                start.await()
                repeat(250) {
                    val transform: (TorrentDownloadState) -> TorrentDownloadState = { it.copy(seeds = it.seeds + 1) }
                    update.invoke(TorrentEngine, if (worker % 2 == 0) "a" else "b", transform)
                }
            } }
            start.countDown()
            jobs.forEach { it.get(10, TimeUnit.SECONDS) }
            assertEquals(500, states.value.getValue("a").seeds)
            assertEquals(500, states.value.getValue("b").seeds)
            // A late native alert cannot reinsert a cancelled transfer.
            states.value = states.value - "a"
            val late: (TorrentDownloadState) -> TorrentDownloadState = { it.copy(progress = 1f) }
            update.invoke(TorrentEngine, "a", late)
            assertFalse(states.value.containsKey("a"))
        } finally {
            workers.shutdownNow()
            states.value = original
        }
    }

    @Test fun torrentPathsMustStayInsideTheDirectoryNotJustShareItsPrefix() {
        val root = File("downloads").canonicalFile
        assertTrue(TorrentEngine.isWithinDirectory(root, File(root, "movie/video.mp4").canonicalFile))
        assertFalse(TorrentEngine.isWithinDirectory(root, File(root.parentFile, "downloads-other/video.mp4").canonicalFile))
        assertFalse(TorrentEngine.isWithinDirectory(root, File(root, "../outside.mp4").canonicalFile))
        assertFalse(TorrentEngine.isWithinDirectory(root, root))
    }
}
