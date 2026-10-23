package com.example.ymediaplayer.mycinema.data

import com.example.ymediaplayer.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.net.URLEncoder

class TmdbApiClient(
    private val apiKey: String = BuildConfig.TMDB_API_KEY
) {
    private val httpClient = DnsOverHttpsClient.client
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val imdbIdCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    suspend fun searchMulti(query: String): List<TmdbItem> = withContext(Dispatchers.IO) {
        if (query.isBlank() || apiKey.isBlank()) return@withContext emptyList()
        try {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "https://api.themoviedb.org/3/search/multi?api_key=$apiKey&query=$encodedQuery&include_adult=false&page=1"
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val parsed = json.decodeFromString<TmdbSearchResponse>(body)
                parsed.results.filter { it.mediaType == "movie" || it.mediaType == "tv" }
            }
        } catch (e: Exception) {
            android.util.Log.e("TmdbApiClient", "Error searching multi: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchTrending(isSeries: Boolean = false): List<TmdbItem> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()
        try {
            val endpoint = if (isSeries) "tv" else "movie"
            val url = "https://api.themoviedb.org/3/trending/$endpoint/week?api_key=$apiKey"
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val parsed = json.decodeFromString<TmdbSearchResponse>(body)
                parsed.results.map { it.copy(mediaType = if (isSeries) "tv" else "movie") }
            }
        } catch (e: Exception) {
            android.util.Log.e("TmdbApiClient", "Error fetching trending: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchTrendingInIndia(isSeries: Boolean = false): List<TmdbItem> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()
        try {
            val endpoint = if (isSeries) "tv" else "movie"
            val url = "https://api.themoviedb.org/3/discover/$endpoint?api_key=$apiKey&region=IN&with_original_language=hi|te|ta|ml|kn&sort_by=popularity.desc&vote_count.gte=10"
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val parsed = json.decodeFromString<TmdbSearchResponse>(body)
                parsed.results.map { it.copy(mediaType = if (isSeries) "tv" else "movie") }
            }
        } catch (e: Exception) {
            android.util.Log.e("TmdbApiClient", "Error fetching trending India: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchRecentlyReleased(): List<TmdbItem> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()
        try {
            coroutineScope {
                val movieDeferred = async {
                    try {
                        val moviesUrl = "https://api.themoviedb.org/3/movie/now_playing?api_key=$apiKey&page=1"
                        val mReq = Request.Builder().url(moviesUrl).build()
                        httpClient.newCall(mReq).execute().use { resp ->
                            if (resp.isSuccessful) {
                                resp.body?.string()?.let {
                                    json.decodeFromString<TmdbSearchResponse>(it).results.map { item -> item.copy(mediaType = "movie") }
                                } ?: emptyList()
                            } else emptyList()
                        }
                    } catch (_: Exception) { emptyList() }
                }

                val tvDeferred = async {
                    try {
                        val tvUrl = "https://api.themoviedb.org/3/tv/on_the_air?api_key=$apiKey&page=1"
                        val tvReq = Request.Builder().url(tvUrl).build()
                        httpClient.newCall(tvReq).execute().use { resp ->
                            if (resp.isSuccessful) {
                                resp.body?.string()?.let {
                                    json.decodeFromString<TmdbSearchResponse>(it).results.map { item -> item.copy(mediaType = "tv") }
                                } ?: emptyList()
                            } else emptyList()
                        }
                    } catch (_: Exception) { emptyList() }
                }

                val mList = movieDeferred.await()
                val tvList = tvDeferred.await()

                val combined = mutableListOf<TmdbItem>()
                val maxLen = maxOf(mList.size, tvList.size)
                for (i in 0 until maxLen) {
                    if (i < mList.size) combined.add(mList[i])
                    if (i < tvList.size) combined.add(tvList[i])
                }
                combined
            }
        } catch (e: Exception) {
            android.util.Log.e("TmdbApiClient", "Error fetching recently released: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchUpcomingMovies(filter: String = "ALL"): List<TmdbItem> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        try {
            val releaseTypeParam = when (filter.uppercase()) {
                "THEATRICAL" -> "&with_release_type=2|3"
                "OTT" -> "&with_release_type=4"
                else -> ""
            }
            val url = "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&primary_release_date.gte=$todayStr$releaseTypeParam&sort_by=popularity.desc&include_adult=false&page=1"
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val parsed = json.decodeFromString<TmdbSearchResponse>(body)
                val list = parsed.results
                    .filter { item ->
                        val rDate = item.releaseDate ?: ""
                        rDate.isNotBlank() && rDate >= todayStr
                    }
                    .map { it.copy(mediaType = "movie") }
                if (list.isNotEmpty()) return@withContext list
            }

            // Fallback to /movie/upcoming with strict future date filtering
            if (filter.uppercase() == "ALL") {
                val fallbackUrl = "https://api.themoviedb.org/3/movie/upcoming?api_key=$apiKey&page=1"
                val fallbackReq = Request.Builder().url(fallbackUrl).build()
                httpClient.newCall(fallbackReq).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    val body = response.body?.string() ?: return@withContext emptyList()
                    val parsed = json.decodeFromString<TmdbSearchResponse>(body)
                    parsed.results
                        .filter { item ->
                            val rDate = item.releaseDate ?: ""
                            rDate.isNotBlank() && rDate >= todayStr
                        }
                        .map { it.copy(mediaType = "movie") }
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("TmdbApiClient", "Error fetching upcoming: ${e.message}")
            emptyList()
        }
    }

    suspend fun getImdbIdForTmdb(isSeries: Boolean, tmdbId: Int): String = withContext(Dispatchers.IO) {
        if (tmdbId <= 0 || apiKey.isBlank()) return@withContext ""
        val cacheKey = "${if (isSeries) "tv" else "movie"}_$tmdbId"
        imdbIdCache[cacheKey]?.let { return@withContext it }

        try {
            val endpoint = if (isSeries) "tv" else "movie"
            val url = "https://api.themoviedb.org/3/$endpoint/$tmdbId/external_ids?api_key=$apiKey"
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext ""
                val body = response.body?.string() ?: return@withContext ""
                val parsed = json.decodeFromString<TmdbExternalIds>(body)
                val imdbId = parsed.imdbId?.trim() ?: ""
                if (imdbId.startsWith("tt", ignoreCase = true)) {
                    imdbIdCache[cacheKey] = imdbId
                    return@withContext imdbId
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TmdbApiClient", "Error getting IMDB id: ${e.message}")
        }
        ""
    }

    suspend fun fetchTrailerYoutubeKey(isSeries: Boolean, tmdbId: Int): String? = withContext(Dispatchers.IO) {
        if (tmdbId <= 0 || apiKey.isBlank()) return@withContext null
        try {
            val endpoint = if (isSeries) "tv" else "movie"
            val url = "https://api.themoviedb.org/3/$endpoint/$tmdbId/videos?api_key=$apiKey"
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val parsed = json.decodeFromString<TmdbVideosResponse>(body)
                val trailer = parsed.results.firstOrNull {
                    it.site.equals("YouTube", ignoreCase = true) &&
                    (it.type.equals("Trailer", ignoreCase = true) || it.type.equals("Teaser", ignoreCase = true))
                }
                trailer?.key
            }
        } catch (_: Exception) {
            null
        }
    }
}
