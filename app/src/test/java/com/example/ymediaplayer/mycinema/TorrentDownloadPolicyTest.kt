package com.example.ymediaplayer.mycinema

import com.example.ymediaplayer.mycinema.service.TorrentDownloadState
import com.example.ymediaplayer.mycinema.service.isActiveDownload
import com.example.ymediaplayer.mycinema.service.selectDownloadForNotification
import org.junit.Assert.*
import org.junit.Test

class TorrentDownloadPolicyTest {
    private fun state(status: TorrentDownloadState.Status, stream: Boolean = false) =
        TorrentDownloadState("hash", "movie", "magnet", status = status, isStream = stream,
            isSequential = status == TorrentDownloadState.Status.STREAMING)

    @Test fun sequentialDownloadsStayInTheForegroundWhileDownloading() {
        val sequential = state(TorrentDownloadState.Status.STREAMING)
        assertTrue(sequential.isActiveDownload)
        assertSame(sequential, selectDownloadForNotification(listOf(sequential)))
    }

    @Test fun activeDownloadTakesPriorityOverPausedAndFinishedEntries() {
        val active = state(TorrentDownloadState.Status.CONNECTING)
        assertSame(active, selectDownloadForNotification(listOf(
            state(TorrentDownloadState.Status.PAUSED), state(TorrentDownloadState.Status.FINISHED), active)))
    }

    @Test fun pausedDownloadOffersResumeWithoutKeepingActiveWork() {
        val paused = state(TorrentDownloadState.Status.PAUSED)
        assertFalse(paused.isActiveDownload)
        assertSame(paused, selectDownloadForNotification(listOf(paused)))
    }

    @Test fun streamsAndTerminalStatesDoNotKeepDownloadServiceAlive() {
        val states = listOf(state(TorrentDownloadState.Status.FINISHED), state(TorrentDownloadState.Status.ERROR),
            state(TorrentDownloadState.Status.STREAMING, stream = true))
        assertTrue(states.none { it.isActiveDownload })
        assertNull(selectDownloadForNotification(states))
        assertNull(selectDownloadForNotification(emptyList()))
    }
}
