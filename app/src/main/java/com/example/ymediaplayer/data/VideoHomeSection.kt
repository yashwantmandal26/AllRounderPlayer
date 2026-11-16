package com.example.ymediaplayer.data

/** Visibility in a home shelf is independent of the underlying file and playback history. */
enum class VideoHomeSection(val folderId: String, val title: String) {
    RECENTLY_ADDED("recently_added", "Recently Added"),
    CONTINUE_WATCHING("continue_watching", "Continue Watching");

    companion object {
        fun fromFolderId(id: String): VideoHomeSection? = entries.firstOrNull { it.folderId == id }
    }
}
