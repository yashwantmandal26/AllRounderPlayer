package com.example.ymediaplayer.player

import android.content.Context
import android.util.Log
import com.example.ymediaplayer.mycinema.data.DnsOverHttpsClient
import com.example.ymediaplayer.mycinema.data.TmdbApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder

data class OnlineSubtitle(
    val id: String,
    val url: String,
    val languageCode: String,
    val languageName: String,
    val fileName: String,
    val releaseName: String?
)

object OnlineSubtitleManager {
    private const val TAG = "OnlineSubtitleManager"
    private val httpClient by lazy { DnsOverHttpsClient.client }
    private val tmdbClient by lazy { TmdbApiClient() }

    fun cleanMediaTitle(raw: String): String {
        var s = raw
        // Remove file extension
        s = s.replace(Regex("\\.[a-zA-Z0-9]{2,4}$"), "")
        // Replace punctuation / separators
        s = s.replace('.', ' ').replace('_', ' ').replace('-', ' ')
        // Remove video quality, rip type, audio codecs, group tags
        s = s.replace(Regex("(?i)\\b(1080p|720p|4k|2160p|bluray|blu-ray|web-?dl|webrip|hdr|hdr10|hevc|x264|x265|h264|h265|aac|ddp|ac3|dts|yify|yts|proper|repack|dvdrip|brrip|bdrip|remux)\\b.*"), "")
        // Trim multiple spaces
        return s.replace(Regex("\\s+"), " ").trim()
    }

    suspend fun searchOnlineSubtitles(
        title: String,
        explicitImdbId: String? = null
    ): List<OnlineSubtitle> = withContext(Dispatchers.IO) {
        val imdbId = if (!explicitImdbId.isNullOrBlank() && explicitImdbId.startsWith("tt")) {
            explicitImdbId
        } else {
            resolveImdbId(title)
        }
        if (imdbId.isBlank()) return@withContext emptyList()

        fetchSubtitlesForImdb(imdbId)
    }

    private suspend fun resolveImdbId(query: String): String {
        val clean = cleanMediaTitle(query)
        if (clean.isBlank()) return ""

        // Try Cinemeta movie search
        try {
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val url = "https://v3-cinemeta.strem.io/catalog/movie/top/search=$encoded.json"
            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val json = JSONObject(body)
                    val metas = json.optJSONArray("metas")
                    if (metas != null && metas.length() > 0) {
                        val id = metas.getJSONObject(0).optString("id", "")
                        if (id.startsWith("tt")) return id
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Cinemeta movie search failed: ${e.message}")
        }

        // Try Cinemeta series search
        try {
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val url = "https://v3-cinemeta.strem.io/catalog/series/top/search=$encoded.json"
            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val json = JSONObject(body)
                    val metas = json.optJSONArray("metas")
                    if (metas != null && metas.length() > 0) {
                        val id = metas.getJSONObject(0).optString("id", "")
                        if (id.startsWith("tt")) return id
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Cinemeta series search failed: ${e.message}")
        }

        // Try TMDB search as fallback
        try {
            val results = tmdbClient.searchMulti(clean)
            val top = results.firstOrNull()
            if (top != null) {
                val imdb = tmdbClient.getImdbIdForTmdb(top.mediaType == "tv", top.id)
                if (imdb.startsWith("tt")) return imdb
            }
        } catch (e: Exception) {
            Log.d(TAG, "TMDB resolution failed: ${e.message}")
        }

        return ""
    }

    suspend fun fetchSubtitlesForImdb(imdbId: String): List<OnlineSubtitle> = withContext(Dispatchers.IO) {
        val url = "https://opensubtitles-v3.strem.io/subtitles/movie/$imdbId.json"
        try {
            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(body)
                val subsArray = json.optJSONArray("subtitles") ?: return@withContext emptyList()

                val list = mutableListOf<OnlineSubtitle>()
                for (i in 0 until subsArray.length()) {
                    val item = subsArray.getJSONObject(i)
                    val subUrl = item.optString("url")
                    if (subUrl.isBlank()) continue

                    val langCode = item.optString("lang", "eng").lowercase()
                    val langName = languageCodeToName(langCode)
                    val fileName = item.optString("subtitleFileName", "subtitle_${i + 1}.srt")
                    val releaseName = item.optString("movieReleaseName", "")

                    list.add(
                        OnlineSubtitle(
                            id = item.optString("id", i.toString()),
                            url = subUrl,
                            languageCode = langCode,
                            languageName = langName,
                            fileName = fileName,
                            releaseName = releaseName.ifBlank { null }
                        )
                    )
                }
                list
            }
        } catch (e: Exception) {
            Log.e(TAG, "OpenSubtitles fetch failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun downloadSubtitle(context: Context, subtitle: OnlineSubtitle): File? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(subtitle.url).header("User-Agent", "Mozilla/5.0").build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val bytes = resp.body?.bytes() ?: return@withContext null

                val subDir = File(context.cacheDir, "subtitles")
                if (!subDir.exists()) subDir.mkdirs()

                val cleanName = subtitle.fileName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                val safeName = "${subtitle.languageCode}_${subtitle.id}_$cleanName"
                val finalFile = if (safeName.endsWith(".srt", ignoreCase = true)) {
                    File(subDir, safeName)
                } else {
                    File(subDir, "$safeName.srt")
                }

                finalFile.writeBytes(bytes)
                finalFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "Subtitle download failed: ${e.message}")
            null
        }
    }

    fun languageCodeToName(code: String): String = when (code.lowercase()) {
        "eng", "en" -> "English"
        "hin", "hi" -> "Hindi"
        "spa", "es" -> "Spanish"
        "fra", "fr" -> "French"
        "ger", "de" -> "German"
        "ita", "it" -> "Italian"
        "rus", "ru" -> "Russian"
        "ara", "ar" -> "Arabic"
        "tam", "ta" -> "Tamil"
        "tel", "te" -> "Telugu"
        "mal", "ml" -> "Malayalam"
        "ben", "bn" -> "Bengali"
        "kan", "kn" -> "Kannada"
        "mar", "mr" -> "Marathi"
        "pan", "pa" -> "Punjabi"
        "kor", "ko" -> "Korean"
        "jpn", "ja" -> "Japanese"
        "zho", "zh", "chi" -> "Chinese"
        "por", "pt" -> "Portuguese"
        "ind", "id" -> "Indonesian"
        "tur", "tr" -> "Turkish"
        "vie", "vi" -> "Vietnamese"
        "tha", "th" -> "Thai"
        "pol", "pl" -> "Polish"
        "nld", "nl" -> "Dutch"
        "swe", "sv" -> "Swedish"
        "ell", "el" -> "Greek"
        "heb", "he" -> "Hebrew"
        else -> code.uppercase()
    }
}
