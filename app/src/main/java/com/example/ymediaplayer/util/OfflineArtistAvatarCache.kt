package com.example.ymediaplayer.util

import android.content.Context
import android.net.Uri
import com.example.ymediaplayer.data.MusicItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Caches artist portraits from the network, with embedded track artwork as an offline fallback. */
object OfflineArtistAvatarCache {
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    suspend fun cacheArtists(
        context: Context,
        artists: List<Pair<String, List<MusicItem>>>
    ): Map<String, Uri> = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, "artist_avatars")
        if (!directory.exists() && !directory.mkdirs()) return@withContext emptyMap()

        buildMap {
            artists.forEach { (artist, songs) ->
                val candidates = songs.take(4)
                val signature = buildString {
                    append(artist.trim().lowercase())
                    candidates.forEach { append('|').append(it.id).append(':').append(it.albumId) }
                }
                val target = File(directory, "${signature.sha256()}.jpg")
                if (!target.isFile || target.length() == 0L) {
                    val artwork = candidates.firstNotNullOfOrNull { song ->
                        MediaArtworkHelper.getAudioArtworkBytes(context, song.uri, song.albumArtUri)
                    } ?: return@forEach

                    val temporary = File(directory, "${target.name}.tmp")
                    try {
                        temporary.outputStream().use { it.write(artwork) }
                        if (!temporary.renameTo(target)) {
                            if (!target.exists()) temporary.copyTo(target, overwrite = true)
                            temporary.delete()
                        }
                    } catch (_: Exception) {
                        temporary.delete()
                        return@forEach
                    }
                }
                if (target.isFile && target.length() > 0L) put(artist, Uri.fromFile(target))
            }
        }
    }

    /** Fetches artist portraits online and stores them locally so they remain visible offline. */
    suspend fun fetchOnlineArtistAvatars(
        context: Context,
        artists: List<Pair<String, List<MusicItem>>>
    ): Map<String, Uri> = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, "artist_avatars")
        if (!directory.exists() && !directory.mkdirs()) return@withContext emptyMap()

        coroutineScope {
            artists.map { (artist, _) ->
                async {
                    val cleanName = artist.trim()
                    if (cleanName.isBlank() || cleanName.equals("Unknown Artist", ignoreCase = true)) {
                        return@async null
                    }

                    val imageFile = File(directory, "online-${cleanName.lowercase().sha256()}.img")
                    if (!imageFile.isFile || imageFile.length() == 0L) {
                        val imageUrl = findArtistImageUrl(cleanName) ?: return@async null
                        val imageBytes = downloadImage(imageUrl) ?: return@async null
                        val temporary = File(directory, "${imageFile.name}.tmp")
                        try {
                            temporary.outputStream().use { it.write(imageBytes) }
                            if (!temporary.renameTo(imageFile)) {
                                if (!imageFile.exists()) temporary.copyTo(imageFile, overwrite = true)
                                temporary.delete()
                            }
                        } catch (_: Exception) {
                            temporary.delete()
                            return@async null
                        }
                    }

                    if (imageFile.isFile && imageFile.length() > 0L) {
                        cleanName to Uri.fromFile(imageFile)
                    } else null
                }
            }.awaitAll().filterNotNull().toMap()
        }
    }

    private fun findArtistImageUrl(artist: String): String? {
        findTheAudioDbImageUrl(artist)?.let { return it }
        return findWikipediaArtistImageUrl(artist)
    }

    private fun findTheAudioDbImageUrl(artist: String): String? {
        val query = try {
            URLEncoder.encode(artist, "UTF-8")
        } catch (_: Exception) {
            artist.replace(" ", "%20")
        }
        val request = Request.Builder()
            .url("https://www.theaudiodb.com/api/v1/json/123/search.php?s=$query")
            .header("Accept", "application/json")
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val artistRows = JSONObject(body).optJSONArray("artists") ?: return null
                val expected = artist.normalizedForMatching()
                var bestMatch: JSONObject? = null
                var bestScore = 0
                for (index in 0 until artistRows.length()) {
                    val candidate = artistRows.optJSONObject(index) ?: continue
                    val candidateName = candidate.optString("strArtist").normalizedForMatching()
                    if (candidateName.isBlank()) continue
                    val expectedCompact = expected.filterNot(Char::isWhitespace)
                    val candidateCompact = candidateName.filterNot(Char::isWhitespace)
                    val score = when {
                        candidateName == expected -> 100
                        candidateCompact == expectedCompact -> 100
                        expected.contains(candidateName) || candidateName.contains(expected) -> 80
                        else -> expected.split(' ').intersect(candidateName.split(' ').toSet()).size * 10
                    }
                    if (score > bestScore) {
                        bestScore = score
                        bestMatch = candidate
                    }
                }
                if (bestScore < 60) return null
                bestMatch?.optString("strArtistThumb")
                    ?.takeIf { it.startsWith("https://", ignoreCase = true) }
                    ?: bestMatch?.optString("strArtistFanart")
                        ?.takeIf { it.startsWith("https://", ignoreCase = true) }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun findWikipediaArtistImageUrl(artist: String): String? {
        val query = try {
            URLEncoder.encode(artist, "UTF-8")
        } catch (_: Exception) {
            artist.replace(" ", "%20")
        }
        val request = Request.Builder()
            .url("https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=$query&gsrnamespace=0&gsrlimit=8&prop=pageimages&piprop=thumbnail&pithumbsize=320&format=json")
            .header("Accept", "application/json")
            .header("User-Agent", "AllRounderMediaPlayer/1.0 (artist artwork lookup)")
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages") ?: return null
                val expected = artist.normalizedForMatching()
                val expectedCompact = expected.filterNot(Char::isWhitespace)
                var bestUrl: String? = null
                var bestScore = 0
                val pageKeys = pages.keys()
                while (pageKeys.hasNext()) {
                    val page = pages.optJSONObject(pageKeys.next()) ?: continue
                    val title = page.optString("title").normalizedForMatching()
                    val titleCompact = title.filterNot(Char::isWhitespace)
                    val score = when {
                        titleCompact == expectedCompact -> 100
                        title.contains(expected) || expected.contains(title) -> 80
                        else -> expected.split(' ').intersect(title.split(' ').toSet()).size * 10
                    }
                    val imageUrl = page.optJSONObject("thumbnail")?.optString("source")
                        ?.takeIf { it.startsWith("https://", ignoreCase = true) }
                    if (imageUrl != null && score > bestScore) {
                        bestScore = score
                        bestUrl = imageUrl
                    }
                }
                bestUrl.takeIf { bestScore >= 60 }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun downloadImage(url: String): ByteArray? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "AllRounderMediaPlayer/1.0 (artist artwork lookup)")
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                if (body.contentLength() > 8L * 1024L * 1024L) return null
                body.bytes().takeIf { bytes ->
                    bytes.isNotEmpty() && bytes.size <= 8 * 1024 * 1024
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun String.normalizedForMatching(): String =
        lowercase().filter { it.isLetterOrDigit() || it.isWhitespace() }.trim().replace(Regex("\\s+"), " ")

    private fun String.sha256(): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
        val hex = "0123456789abcdef"
        return buildString(bytes.size * 2) {
            bytes.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(hex[value ushr 4])
                append(hex[value and 0x0f])
            }
        }
    }
}
