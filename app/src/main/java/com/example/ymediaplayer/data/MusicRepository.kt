package com.example.ymediaplayer.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import android.os.Build

enum class AudioCategory(val displayName: String) {
    SONG("Songs"),
    RECORDING("Recordings"),
    RINGTONE("Ringtones")
}

data class MusicItem(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val size: Long,
    val path: String = "",
    val relativePath: String = ""
) {
    val albumArtUri: Uri
        get() = ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId
        )

    val audioCategory: AudioCategory
        get() = classifyAudioCategory(title, path, relativePath, duration)
}

internal fun classifyAudioCategory(title: String, path: String, relativePath: String, duration: Long): AudioCategory {
    val lowerTitle = title.trim().lowercase()
    val directories = (java.io.File(path).parent.orEmpty() + "/" + relativePath)
        .lowercase()
        .split('/', '\\')
        .filter(String::isNotBlank)

    val recordingDirectories = setOf(
        "recordings", "call recordings", "recorded calls", "call", "calls", "sound_recorder",
        "voice recorder", "voice recordings", "voice notes", "voicenotes", "whatsapp voice notes"
    )
    val isKnownRecordingDirectory = directories.any { it in recordingDirectories }
    val isWhatsAppVoiceNote = lowerTitle.matches(Regex("^(aud|ptt)-\\d{8}-wa\\d+.*"))
    val isCallRecordingName = lowerTitle.contains("call recording") ||
        lowerTitle.matches(Regex("^call[_ @-]*\\+?[0-9][0-9 ()-]{4,}.*"))

    if (isKnownRecordingDirectory || isWhatsAppVoiceNote || isCallRecordingName) {
        return AudioCategory.RECORDING
    }

    val ringtoneDirectories = setOf("ringtones", "notifications", "alarms")
    val isRingtoneDirectory = directories.any { it in ringtoneDirectories }
    val isRingtoneName = lowerTitle.contains("ringtone") || lowerTitle.contains("notification") ||
        lowerTitle.contains("alarm tone") || lowerTitle.contains("alert tone")
    val isExplicitToneName = duration in 1_000L..45_000L &&
        lowerTitle.matches(Regex(".*\\b(ringtone|notification|alarm|alert)\\b.*"))

    return if (isRingtoneDirectory || isRingtoneName || isExplicitToneName) {
        AudioCategory.RINGTONE
    } else {
        AudioCategory.SONG
    }
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

        fun getCachedMusic(): List<MusicItem>? = memoryCachedMusic
    }

    suspend fun getMusicFiles(forceRefresh: Boolean = false): List<MusicItem> = getAllMusic(forceRefresh)

    suspend fun getAllMusic(forceRefresh: Boolean = false): List<MusicItem> = withContext(Dispatchers.IO) {
        val cached = memoryCachedMusic
        val now = System.currentTimeMillis()
        if (!forceRefresh && cached != null && (now - lastFetchTime < 300_000L)) {
            return@withContext cached
        }

        val musicList = mutableListOf<MusicItem>()

        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATA
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
        // High-performance indexed selection: also include partially corrupted / un-indexed audio files (duration=0) with valid file size
        val selection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%') AND (${MediaStore.Audio.Media.DURATION} >= 1000 OR (${MediaStore.Audio.Media.DURATION} = 0 AND ${MediaStore.Audio.Media.SIZE} >= 16384))"

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
            val pathColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            val relPathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH) else -1

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val rawTitle = if (titleColumn >= 0) cursor.getString(titleColumn) else null
                val rawDisplayName = if (displayNameColumn >= 0) cursor.getString(displayNameColumn) else null
                // Sanitize: if title is mostly garbage characters (?, *, #, etc.), use filename instead
                val cleanTitle = rawTitle?.let { t ->
                    val printableCount = t.count { it.isLetterOrDigit() || it == ' ' || it in "'-_.," }
                    if (t.isBlank() || (t.length > 2 && printableCount.toFloat() / t.length < 0.4f)) null else t
                }
                val title = when {
                    !cleanTitle.isNullOrBlank() -> cleanTitle
                    !rawDisplayName.isNullOrBlank() -> rawDisplayName.substringBeforeLast(".")
                    else -> "Unknown Track"
                }
                val artist = (if (artistColumn >= 0) cursor.getString(artistColumn) else null)?.takeIf { it != "<unknown>" && it.isNotBlank() } ?: "Unknown Artist"
                val album = cursor.getString(albumColumn) ?: "Unknown Album"
                val albumId = cursor.getLong(albumIdColumn)
                val duration = cursor.getLong(durationColumn)
                val size = cursor.getLong(sizeColumn)
                val path = if (pathColumn >= 0) cursor.getString(pathColumn).orEmpty() else ""
                val relPath = if (relPathColumn >= 0) cursor.getString(relPathColumn).orEmpty() else ""

                // Ignore corrupt or 0-byte audio entries
                if (duration <= 0L && size <= 0L) continue

                // Filter out ghost entries where the file no longer exists on disk
                if (path.isNotBlank()) {
                    val file = java.io.File(path)
                    if (!file.exists() || (size <= 0L && file.length() <= 0L)) {
                        continue
                    }
                }

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
                        size = size,
                        path = path,
                        relativePath = relPath
                    )
                )
            }
        }
        } catch (_: SecurityException) {
            return@withContext emptyList()
        }
        val deduplicated = deduplicateMusicItems(musicList)
        memoryCachedMusic = deduplicated
        lastFetchTime = now
        deduplicated
    }
}

/**
 * Deduplicates music items across MediaStore duplicate IDs, identical file paths/symlinks,
 * and identical audio tracks copied across multiple storage locations.
 * When duplicates exist, retains the highest quality/best tagged version.
 */
fun deduplicateMusicItems(items: List<MusicItem>): List<MusicItem> {
    if (items.size <= 1) return items

    val result = ArrayList<MusicItem>(items.size)
    val seenIds = HashSet<Long>()
    val seenPaths = HashSet<String>()

    for (item in items) {
        if (!seenIds.add(item.id)) continue

        val normPath = if (item.path.isNotBlank()) {
            try {
                java.io.File(item.path).canonicalPath.lowercase()
            } catch (_: Exception) {
                item.path.trim().lowercase()
            }
        } else item.uri.toString()

        if (!seenPaths.add(normPath)) continue

        val normTitle = item.title.trim().lowercase()
        val normArtist = item.artist.trim().lowercase()
        val durationSec = item.duration / 1000L

        val isGenericTitle = normTitle.isBlank() ||
            normTitle == "unknown track" ||
            normTitle == "unknown" ||
            normTitle.matches(Regex("^track\\s*\\d*$", RegexOption.IGNORE_CASE)) ||
            normTitle.matches(Regex("^audio\\s*\\d*$", RegexOption.IGNORE_CASE))

        val existingIndex = result.indexOfFirst { existing ->
            val exTitle = existing.title.trim().lowercase()
            val exArtist = existing.artist.trim().lowercase()
            val exDurationSec = existing.duration / 1000L

            if (!isGenericTitle && normArtist != "unknown artist" && exArtist != "unknown artist") {
                // Known title and known artist: match title and artist with duration within 2 seconds
                exTitle == normTitle && exArtist == normArtist && kotlin.math.abs(exDurationSec - durationSec) <= 2
            } else if (!isGenericTitle) {
                // Known title with unknown artist: match title and (same file size or duration within 1 sec)
                exTitle == normTitle && (
                    (item.size > 0 && item.size == existing.size) ||
                    (durationSec > 0 && kotlin.math.abs(exDurationSec - durationSec) <= 1)
                )
            } else {
                // Generic title: match exact filename and size and duration
                val itemFile = item.path.substringAfterLast('/', "").lowercase()
                val exFile = existing.path.substringAfterLast('/', "").lowercase()
                itemFile.isNotBlank() && itemFile == exFile && item.size > 0 && item.size == existing.size &&
                    kotlin.math.abs(exDurationSec - durationSec) <= 1
            }
        }

        if (existingIndex >= 0) {
            val existing = result[existingIndex]
            if (rateSongQuality(item) > rateSongQuality(existing)) {
                result[existingIndex] = item
            }
        } else {
            result.add(item)
        }
    }
    return result
}

private fun rateSongQuality(item: MusicItem): Int {
    var score = 0
    if (item.artist != "Unknown Artist" && item.artist.isNotBlank()) score += 10
    if (item.album != "Unknown Album" && item.album.isNotBlank()) score += 5
    if (item.albumId > 0) score += 5
    val lowerPath = item.path.lowercase()
    if (lowerPath.contains("/music/") || lowerPath.contains("/audio/")) score += 20
    if (lowerPath.contains("/download/")) score += 5
    if (lowerPath.contains("whatsapp") || lowerPath.contains("telegram")) score -= 10
    if (item.size > 1_000_000L) score += (item.size / 1_000_000L).coerceAtMost(20).toInt()
    return score
}
