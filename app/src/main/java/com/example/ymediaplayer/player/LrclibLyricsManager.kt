package com.example.ymediaplayer.player

import android.content.Context
import android.util.Log
import com.example.ymediaplayer.mycinema.data.DnsOverHttpsClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder

data class LyricLine(
    val timeMs: Long,
    val text: String
)

data class SongLyrics(
    val title: String,
    val artist: String,
    val lines: List<LyricLine>,
    val plainLyrics: String? = null,
    val isSynced: Boolean = lines.isNotEmpty()
)

object LrclibLyricsManager {
    private const val TAG = "LrclibLyricsManager"
    private val httpClient by lazy { DnsOverHttpsClient.client }

    suspend fun getLyrics(
        context: Context,
        title: String,
        artist: String,
        durationSeconds: Int = 0
    ): SongLyrics? = withContext(Dispatchers.IO) {
        val cleanTitle = cleanMusicTitle(title)
        val cleanArtist = cleanMusicArtist(artist)
        if (cleanTitle.isBlank()) return@withContext null

        // Check local disk cache first
        val cached = getCachedLyrics(context, cleanTitle, cleanArtist)
        if (cached != null) return@withContext cached

        // 1. Try exact match /api/get
        val exactMatch = fetchExactLyrics(cleanTitle, cleanArtist, durationSeconds)
        if (exactMatch != null) {
            saveCachedLyrics(context, cleanTitle, cleanArtist, exactMatch)
            return@withContext exactMatch
        }

        // 2. Fallback to /api/search?q=
        val searchMatch = searchLyrics(cleanTitle, cleanArtist)
        if (searchMatch != null) {
            saveCachedLyrics(context, cleanTitle, cleanArtist, searchMatch)
            return@withContext searchMatch
        }

        null
    }

    private fun cleanMusicTitle(title: String): String {
        return title
            .replace(Regex("\\.[a-zA-Z0-9]{2,4}$"), "")
            .replace(Regex("(?i)\\b(official\\s*video|official\\s*audio|lyric\\s*video|remastered|remaster|hd|hq|audio|video|lyrics)\\b"), "")
            .replace(Regex("\\[.*?\\]|\\(.*?\\)"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun cleanMusicArtist(artist: String): String {
        if (artist.equals("<unknown>", ignoreCase = true) || artist.contains("unknown", ignoreCase = true)) {
            return ""
        }
        return artist.trim()
    }

    private fun fetchExactLyrics(title: String, artist: String, duration: Int): SongLyrics? {
        try {
            val encTitle = URLEncoder.encode(title, "UTF-8")
            val encArtist = if (artist.isNotBlank()) URLEncoder.encode(artist, "UTF-8") else ""
            var url = "https://lrclib.net/api/get?track_name=$encTitle"
            if (encArtist.isNotBlank()) {
                url += "&artist_name=$encArtist"
            }
            if (duration > 0) {
                url += "&duration=$duration"
            }

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "AllRounderMediaPlayer/1.0 (https://github.com)")
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                return parseLyricsJson(body, title, artist)
            }
        } catch (e: Exception) {
            Log.d(TAG, "fetchExactLyrics failed: ${e.message}")
            return null
        }
    }

    private fun searchLyrics(title: String, artist: String): SongLyrics? {
        try {
            val query = if (artist.isNotBlank()) "$title $artist" else title
            val encQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://lrclib.net/api/search?q=$encQuery"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "AllRounderMediaPlayer/1.0 (https://github.com)")
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val arr = JSONArray(body)
                if (arr.length() == 0) return null

                // Look for first item with syncedLyrics
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val synced = item.optString("syncedLyrics")
                    if (synced.isNotBlank()) {
                        return parseLyricsJson(item.toString(), title, artist)
                    }
                }
                // Fallback to first item with plainLyrics
                return parseLyricsJson(arr.getJSONObject(0).toString(), title, artist)
            }
        } catch (e: Exception) {
            Log.d(TAG, "searchLyrics failed: ${e.message}")
            return null
        }
    }

    private fun parseLyricsJson(jsonStr: String, defaultTitle: String, defaultArtist: String): SongLyrics? {
        val json = JSONObject(jsonStr)
        val track = json.optString("trackName", defaultTitle)
        val artist = json.optString("artistName", defaultArtist)
        val synced = json.optString("syncedLyrics")
        val plain = json.optString("plainLyrics")

        if (synced.isBlank() && plain.isBlank()) return null

        val lines = if (synced.isNotBlank()) parseLrc(synced) else emptyList()
        return SongLyrics(
            title = track,
            artist = artist,
            lines = lines,
            plainLyrics = plain.ifBlank { null }
        )
    }

    fun parseLrc(lrcText: String): List<LyricLine> {
        val list = mutableListOf<LyricLine>()
        val regex = Regex("\\[(\\d{1,2}):(\\d{2}(?:\\.\\d+)?)\\](.*)")
        for (line in lrcText.lines()) {
            val match = regex.find(line.trim()) ?: continue
            val min = match.groupValues[1].toLongOrNull() ?: 0L
            val sec = match.groupValues[2].toFloatOrNull() ?: 0f
            val timeMs = (min * 60_000L) + (sec * 1000f).toLong()
            val text = match.groupValues[3].trim()
            if (text.isNotBlank()) {
                list.add(LyricLine(timeMs, text))
            }
        }
        return list.sortedBy { it.timeMs }
    }

    private fun getCacheFile(context: Context, title: String, artist: String): File {
        val dir = File(context.cacheDir, "lyrics")
        if (!dir.exists()) dir.mkdirs()
        val safe = "${artist}_$title".replace(Regex("[^a-zA-Z0-9.-]"), "_").take(80)
        return File(dir, "$safe.lrc")
    }

    private fun getCachedLyrics(context: Context, title: String, artist: String): SongLyrics? {
        val file = getCacheFile(context, title, artist)
        if (!file.exists()) return null
        return try {
            val content = file.readText()
            val lines = parseLrc(content)
            SongLyrics(title, artist, lines, plainLyrics = content)
        } catch (_: Exception) {
            null
        }
    }

    private fun saveCachedLyrics(context: Context, title: String, artist: String, lyrics: SongLyrics) {
        try {
            val file = getCacheFile(context, title, artist)
            val lrcData = lyrics.lines.joinToString("\n") { line ->
                val min = line.timeMs / 60_000
                val sec = (line.timeMs % 60_000) / 1000
                val cs = (line.timeMs % 1000) / 10
                String.format(java.util.Locale.US, "[%02d:%02d.%02d] %s", min, sec, cs, line.text)
            }.ifBlank { lyrics.plainLyrics ?: "" }
            file.writeText(lrcData)
        } catch (_: Exception) {}
    }
}
