package com.example.ymediaplayer.mycinema.data

import kotlinx.coroutines.*
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.regex.Pattern

class TorrentScraperClient {

    private val httpClient = DnsOverHttpsClient.client

    companion object {
        val EXTRA_TRACKERS = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.demonii.com:1337/announce",
            "udp://open.stealth.si:80/announce",
            "udp://tracker.torrent.eu.org:451/announce",
            "udp://exodus.desync.com:6969/announce",
            "udp://tracker.moeking.me:6969/announce",
            "udp://tracker.bitsearch.to:1337/announce",
            "udp://tracker.dler.org:6969/announce",
            "udp://9.rarbg.com:2810/announce",
            "udp://open.tracker.cl:1337/announce",
            "udp://tracker.tryhackx.org:6969/announce"
        )

        private val HINDI_REGEX = Pattern.compile(
            "(?i)\\b(hindi|hin[._ -]?eng|eng[._ -]?hin|dual[._ -]?audio|multi[._ -]?audio|हिंदी|dubbed)\\b"
        )
        private val HEVC_REGEX = Pattern.compile("(?i)\\b(hevc|x265|h\\.?265|10bit)\\b")
        private val QUALITY_REGEX = Pattern.compile("(?i)\\b(2160p|4k|1080p|720p|480p)\\b")
        private val SEEDS_REGEX = Pattern.compile("(?:👤|Seeders:|Seeds:)\\s*([0-9]+)", Pattern.CASE_INSENSITIVE)
        private val SIZE_REGEX = Pattern.compile("([0-9.]+)\\s*(TB|GB|MB|KB|GiB|MiB|KiB)", Pattern.CASE_INSENSITIVE)
    }

    suspend fun fetchAllSources(
        imdbId: String,
        fallbackTitle: String,
        isSeries: Boolean
    ): List<TorrentSource> = withContext(Dispatchers.IO) {
        if (imdbId.isBlank() || !imdbId.startsWith("tt", ignoreCase = true)) {
            return@withContext emptyList()
        }

        val deferredResults = listOf(
            async { fetchTorrentioSources(imdbId, fallbackTitle, isSeries, isHindiSpecific = false) },
            async { fetchTorrentioSources(imdbId, fallbackTitle, isSeries, isHindiSpecific = true) },
            async { fetchMediaFusionSources(imdbId, fallbackTitle, isSeries) },
            async { if (!isSeries) fetchYtsSources(imdbId, fallbackTitle) else emptyList() },
            async { if (isSeries) fetchEztvSources(imdbId) else emptyList() }
        )

        val allResults = deferredResults.awaitAll().flatten()

        // Deduplicate by infoHash and sort by health score
        val uniqueSources = mutableMapOf<String, TorrentSource>()
        for (source in allResults) {
            val hash = extractInfoHash(source.magnet)
            if (hash != null) {
                val existing = uniqueSources[hash]
                if (existing == null || source.seeds > existing.seeds) {
                    uniqueSources[hash] = source
                }
            }
        }

        uniqueSources.values.sortedByDescending { it.healthScore }
    }

    private fun extractInfoHash(magnet: String): String? {
        val match = Regex("xt=urn:btih:([a-zA-Z0-9]+)", RegexOption.IGNORE_CASE).find(magnet)
        return match?.groupValues?.getOrNull(1)?.lowercase()
    }

    private fun buildMagnet(infoHash: String, title: String): String {
        val encodedTitle = try {
            URLEncoder.encode(title, "UTF-8")
        } catch (_: Exception) {
            "Video"
        }
        val trackerString = EXTRA_TRACKERS.joinToString("") { "&tr=" + URLEncoder.encode(it, "UTF-8") }
        return "magnet:?xt=urn:btih:$infoHash&dn=$encodedTitle$trackerString"
    }

    private suspend fun fetchTorrentioSources(
        imdbId: String,
        fallbackTitle: String,
        isSeries: Boolean,
        isHindiSpecific: Boolean
    ): List<TorrentSource> = withContext(Dispatchers.IO) {
        try {
            val endpoint = if (isSeries) "series" else "movie"
            val baseUrl = if (isHindiSpecific) {
                "https://torrentio.strem.fun/sort=seeders|language=hindi/stream/$endpoint/$imdbId.json"
            } else {
                "https://torrentio.strem.fun/sort=seeders|qualityfilter=cam,screener/stream/$endpoint/$imdbId.json"
            }

            val request = Request.Builder().url(baseUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val root = JSONObject(body)
                val streams = root.optJSONArray("streams") ?: return@withContext emptyList()
                parseStreams(streams, fallbackTitle, if (isHindiSpecific) "Torrentio Hindi" else "Torrentio", isHindiSpecific)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.w("TorrentScraper", "Torrentio (${if (isHindiSpecific) "Hindi" else "Main"}) failed: ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchMediaFusionSources(
        imdbId: String,
        fallbackTitle: String,
        isSeries: Boolean
    ): List<TorrentSource> = withContext(Dispatchers.IO) {
        try {
            val endpoint = if (isSeries) "series" else "movie"
            val url = "https://mediafusion.elfhosted.com/stream/$endpoint/$imdbId.json"
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val root = JSONObject(body)
                val streams = root.optJSONArray("streams") ?: return@withContext emptyList()
                parseStreams(streams, fallbackTitle, "MediaFusion", false)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.w("TorrentScraper", "MediaFusion failed: ${e.message}")
            emptyList()
        }
    }

    private fun parseStreams(
        streams: JSONArray,
        fallbackTitle: String,
        provider: String,
        forceHindi: Boolean
    ): List<TorrentSource> {
        val list = mutableListOf<TorrentSource>()

        for (i in 0 until streams.length()) {
            val item = streams.optJSONObject(i) ?: continue
            val infoHash = item.optString("infoHash")
            if (infoHash.isBlank()) continue

            val rawTitle = item.optString("title", "")
            val rawName = item.optString("name", "")
            val fullText = "$rawName\n$rawTitle"

            // Parse quality
            val qualityMatch = QUALITY_REGEX.matcher(fullText)
            val quality = if (qualityMatch.find()) qualityMatch.group(1)!!.uppercase() else "1080P"

            // Parse seeders
            val seedMatch = SEEDS_REGEX.matcher(fullText)
            val seeds = if (seedMatch.find()) seedMatch.group(1)!!.toIntOrNull() ?: 0 else 0
            if (seeds == 0) continue

            // Parse size
            val sizeMatch = SIZE_REGEX.matcher(fullText)
            val sizeStr = if (sizeMatch.find()) "${sizeMatch.group(1)} ${sizeMatch.group(2)!!.uppercase()}" else "—"

            // Clean title
            val lines = rawTitle.split("\n").filter { it.isNotBlank() }
            var cleanTitle = lines.firstOrNull { !it.contains("💾") && !it.contains("👤") && !it.contains("⚙️") }
                ?: rawTitle.lines().firstOrNull() ?: fallbackTitle
            cleanTitle = cleanTitle.replace(Regex("💾.*|👤.*|⚙️.*"), "").trim()
            if (cleanTitle.isBlank()) cleanTitle = fallbackTitle

            val isHindi = forceHindi || HINDI_REGEX.matcher(fullText).find()
            val isHevc = HEVC_REGEX.matcher(fullText).find()
            val magnet = buildMagnet(infoHash, cleanTitle)

            list.add(
                TorrentSource(
                    title = cleanTitle,
                    quality = quality,
                    size = sizeStr,
                    magnet = magnet,
                    seeds = seeds,
                    peers = (seeds * 0.2).toInt(),
                    type = "web",
                    provider = provider,
                    isHindi = isHindi,
                    isHevc = isHevc
                )
            )
        }
        return list
    }

    private suspend fun fetchYtsSources(imdbId: String, fallbackTitle: String): List<TorrentSource> = withContext(Dispatchers.IO) {
        val mirrors = listOf("yts.mx", "yts.rs", "yts.do")
        for (domain in mirrors) {
            try {
                val url = "https://$domain/api/v2/list_movies.json?query_term=$imdbId&sort_by=seeds"
                val request = Request.Builder().url(url).build()
                val sources = httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body?.string() ?: return@use null
                    val root = JSONObject(body)
                    if (root.optString("status") != "ok") return@use null

                    val data = root.optJSONObject("data") ?: return@use null
                    val movies = data.optJSONArray("movies") ?: return@use null
                    if (movies.length() == 0) return@use null

                    val movie = movies.getJSONObject(0)
                    val movieTitle = movie.optString("title_long", fallbackTitle)
                    val torrents = movie.optJSONArray("torrents") ?: return@use null

                    val list = mutableListOf<TorrentSource>()
                    for (j in 0 until torrents.length()) {
                        val t = torrents.getJSONObject(j)
                        val hash = t.optString("hash")
                        val seeds = t.optInt("seeds", 0)
                        if (hash.isBlank() || seeds == 0) continue

                        val quality = t.optString("quality", "1080p")
                        val size = t.optString("size", "—")
                        val isHevc = HEVC_REGEX.matcher(movieTitle).find()
                        val isHindi = HINDI_REGEX.matcher(movieTitle).find()
                        val magnet = buildMagnet(hash, movieTitle)

                        list.add(
                            TorrentSource(
                                title = "$movieTitle [$quality] (YTS)",
                                quality = quality.uppercase(),
                                size = size,
                                magnet = magnet,
                                seeds = seeds,
                                peers = t.optInt("peers", 0),
                                type = "web",
                                provider = "YTS",
                                isHindi = isHindi,
                                isHevc = isHevc
                            )
                        )
                    }
                    list
                }
                if (!sources.isNullOrEmpty()) return@withContext sources
            } catch (_: Exception) { }
        }
        emptyList()
    }

    private suspend fun fetchEztvSources(imdbId: String): List<TorrentSource> = withContext(Dispatchers.IO) {
        val numericId = imdbId.removePrefix("tt")
        val mirrors = listOf("eztvx.to", "eztv.re", "eztv.tf")
        for (domain in mirrors) {
            try {
                val url = "https://$domain/api/get-torrents?imdb_id=$numericId&limit=100&page=1"
                val request = Request.Builder().url(url).build()
                val sources = httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body?.string() ?: return@use null
                    val root = JSONObject(body)
                    val torrents = root.optJSONArray("torrents") ?: return@use null

                    val list = mutableListOf<TorrentSource>()
                    for (j in 0 until torrents.length()) {
                        val t = torrents.getJSONObject(j)
                        val magnet = t.optString("magnet_url")
                        val seeds = t.optInt("seeds", 0)
                        if (magnet.isBlank() || seeds == 0) continue

                        val rawTitle = t.optString("title", "Episode")
                        val qualityMatch = QUALITY_REGEX.matcher(rawTitle)
                        val quality = if (qualityMatch.find()) qualityMatch.group(1)!!.uppercase() else "720P"

                        val sizeBytes = t.optLong("size_bytes", 0L)
                        val sizeStr = formatBytes(sizeBytes)
                        val isHindi = HINDI_REGEX.matcher(rawTitle).find()
                        val isHevc = HEVC_REGEX.matcher(rawTitle).find()

                        list.add(
                            TorrentSource(
                                title = rawTitle,
                                quality = quality,
                                size = sizeStr,
                                sizeBytes = sizeBytes,
                                magnet = magnet,
                                seeds = seeds,
                                peers = t.optInt("peers", 0),
                                type = "web",
                                provider = "EZTV",
                                isHindi = isHindi,
                                isHevc = isHevc
                            )
                        )
                    }
                    list
                }
                if (!sources.isNullOrEmpty()) return@withContext sources
            } catch (_: Exception) { }
        }
        emptyList()
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "—"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = Math.min(digitGroups, units.size - 1)
        val value = bytes / Math.pow(1024.0, index.toDouble())
        return String.format(java.util.Locale.US, "%.1f %s", value, units[index])
    }
}
