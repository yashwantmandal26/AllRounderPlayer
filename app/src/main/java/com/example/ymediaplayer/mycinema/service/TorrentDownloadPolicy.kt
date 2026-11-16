package com.example.ymediaplayer.mycinema.service

internal val TorrentDownloadState.isActiveDownload: Boolean
    get() = !isStream && when (status) {
        TorrentDownloadState.Status.CONNECTING,
        TorrentDownloadState.Status.DOWNLOADING,
        TorrentDownloadState.Status.STREAMING -> true
        else -> false
    }

internal fun selectDownloadForNotification(downloads: Collection<TorrentDownloadState>): TorrentDownloadState? =
    downloads.firstOrNull { it.isActiveDownload }
        ?: downloads.firstOrNull { !it.isStream && it.status == TorrentDownloadState.Status.PAUSED }
