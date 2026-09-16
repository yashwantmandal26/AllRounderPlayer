package com.example.ymediaplayer.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MusicItem(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val size: Long
) {
    val albumArtUri: Uri
        get() = ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId
        )
}

class MusicRepository(private val context: Context) {

    companion object {
        @Volatile
        private var memoryCachedMusic: List<MusicItem>? = null
        @Volatile
        private var lastFetchTime: Long = 0L

        fun invalidateCache() {
            memoryCachedMusic = null
            lastFetchTime = 0L
        }
    }

    suspend fun getMusicFiles(forceRefresh: Boolean = false): List<MusicItem> = getAllMusic(forceRefresh)

    suspend fun getAllMusic(forceRefresh: Boolean = false): List<MusicItem> = withContext(Dispatchers.IO) {
        val cached = memoryCachedMusic
        val now = System.currentTimeMillis()
        if (!forceRefresh && cached != null && (now - lastFetchTime < 15_000L)) {
            return@withContext cached
        }

        val musicList = mutableListOf<MusicItem>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE
        )

        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
        // High-performance indexed selection: uses B-tree index on IS_MUSIC and MIME_TYPE instead of full table scans
        val selection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%') AND ${MediaStore.Audio.Media.DURATION} >= 1000"

        try {
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val displayNameColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
            val artistColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val rawTitle = if (titleColumn >= 0) cursor.getString(titleColumn) else null
                val rawDisplayName = if (displayNameColumn >= 0) cursor.getString(displayNameColumn) else null
                val title = when {
                    !rawTitle.isNullOrBlank() -> rawTitle
                    !rawDisplayName.isNullOrBlank() -> rawDisplayName.substringBeforeLast(".")
                    else -> "Unknown Track"
                }
                val artist = (if (artistColumn >= 0) cursor.getString(artistColumn) else null)?.takeIf { it != "<unknown>" && it.isNotBlank() } ?: "Unknown Artist"
                val album = cursor.getString(albumColumn) ?: "Unknown Album"
                val albumId = cursor.getLong(albumIdColumn)
                val duration = cursor.getLong(durationColumn)
                val size = cursor.getLong(sizeColumn)

                val contentUri: Uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                musicList.add(
                    MusicItem(
                        id = id,
                        uri = contentUri,
                        title = title,
                        artist = artist,
                        album = album,
                        albumId = albumId,
                        duration = duration,
                        size = size
                    )
                )
            }
        }
        } catch (_: SecurityException) {
            return@withContext emptyList()
        }
        memoryCachedMusic = musicList
        lastFetchTime = now
        musicList
    }
}
