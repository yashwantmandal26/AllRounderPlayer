package com.example.ymediaplayer.mycinema.data

import android.content.Context
import com.example.ymediaplayer.YMediaApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class MyCinemaRepository(
    private val tmdbClient: TmdbApiClient = TmdbApiClient(),
    private val scraperClient: TorrentScraperClient = TorrentScraperClient()
) {
    companion object {
        private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L // 6 hours cache TTL

        private var cachedRecentlyReleased: Pair<Long, List<TmdbItem>>? = null
        private var cachedTrendingMovies: Pair<Long, List<TmdbItem>>? = null
        private var cachedTrendingIndia: Pair<Long, List<TmdbItem>>? = null
        private var cachedTrendingSeries: Pair<Long, List<TmdbItem>>? = null
        private var cachedUpcomingMovies: Pair<Long, List<TmdbItem>>? = null
        private val cachedUpcomingByFilter = ConcurrentHashMap<String, Pair<Long, List<TmdbItem>>>()

        private val sourcesCache = ConcurrentHashMap<String, List<TorrentSource>>()

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

        private fun getDiskCache(key: String, ttlMs: Long = CACHE_TTL_MS): List<TmdbItem>? {
            return try {
                val prefs = YMediaApplication.instance.getSharedPreferences("tmdb_feed_cache", Context.MODE_PRIVATE)
                val timestamp = prefs.getLong("time_$key", 0L)
                if (System.currentTimeMillis() - timestamp < ttlMs) {
                    val raw = prefs.getString("data_$key", null) ?: return null
                    json.decodeFromString(ListSerializer(TmdbItem.serializer()), raw)
                } else null
            } catch (_: Exception) { null }
        }

        private fun saveDiskCache(key: String, items: List<TmdbItem>) {
            if (items.isEmpty()) return
            try {
                val prefs = YMediaApplication.instance.getSharedPreferences("tmdb_feed_cache", Context.MODE_PRIVATE)
                val raw = json.encodeToString(ListSerializer(TmdbItem.serializer()), items)
                prefs.edit().putLong("time_$key", System.currentTimeMillis()).putString("data_$key", raw).apply()
            } catch (_: Exception) {}
        }
    }

    suspend fun search(query: String): List<TmdbItem> = tmdbClient.searchMulti(query)

    suspend fun getRecentlyReleased(forceRefresh: Boolean = false): List<TmdbItem> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedRecentlyReleased?.let { now - it.first < CACHE_TTL_MS } == true) {
            return cachedRecentlyReleased!!.second
        }
        if (!forceRefresh) {
            getDiskCache("recently_released")?.let { diskItems ->
                cachedRecentlyReleased = Pair(now, diskItems)
                return diskItems
            }
        }
        val items = tmdbClient.fetchRecentlyReleased()
        if (items.isNotEmpty()) {
            cachedRecentlyReleased = Pair(now, items)
            saveDiskCache("recently_released", items)
        }
        return items
    }

    suspend fun getTrendingMovies(forceRefresh: Boolean = false): List<TmdbItem> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedTrendingMovies?.let { now - it.first < CACHE_TTL_MS } == true) {
            return cachedTrendingMovies!!.second
        }
        if (!forceRefresh) {
            getDiskCache("trending_movies")?.let { diskItems ->
                cachedTrendingMovies = Pair(now, diskItems)
                return diskItems
            }
        }
        val items = tmdbClient.fetchTrending(isSeries = false)
        if (items.isNotEmpty()) {
            cachedTrendingMovies = Pair(now, items)
            saveDiskCache("trending_movies", items)
        }
        return items
    }

    suspend fun getTrendingSeries(forceRefresh: Boolean = false): List<TmdbItem> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedTrendingSeries?.let { now - it.first < CACHE_TTL_MS } == true) {
            return cachedTrendingSeries!!.second
        }
        if (!forceRefresh) {
            getDiskCache("trending_series")?.let { diskItems ->
                cachedTrendingSeries = Pair(now, diskItems)
                return diskItems
            }
        }
        val items = tmdbClient.fetchTrending(isSeries = true)
        if (items.isNotEmpty()) {
            cachedTrendingSeries = Pair(now, items)
            saveDiskCache("trending_series", items)
        }
        return items
    }

    suspend fun getTrendingIndia(forceRefresh: Boolean = false): List<TmdbItem> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedTrendingIndia?.let { now - it.first < CACHE_TTL_MS } == true) {
            return cachedTrendingIndia!!.second
        }
        if (!forceRefresh) {
            getDiskCache("trending_india")?.let { diskItems ->
                cachedTrendingIndia = Pair(now, diskItems)
                return diskItems
            }
        }
        val items = tmdbClient.fetchTrendingInIndia(isSeries = false)
        if (items.isNotEmpty()) {
            cachedTrendingIndia = Pair(now, items)
            saveDiskCache("trending_india", items)
        }
        return items
    }

    suspend fun getUpcomingMovies(filter: String = "ALL", forceRefresh: Boolean = false): List<TmdbItem> {
        val now = System.currentTimeMillis()
        val key = filter.uppercase()
        val cached = cachedUpcomingByFilter[key]
        if (!forceRefresh && cached?.let { now - it.first < CACHE_TTL_MS } == true) {
            return cached.second
        }
        if (!forceRefresh) {
            getDiskCache("upcoming_$key")?.let { diskItems ->
                cachedUpcomingByFilter[key] = Pair(now, diskItems)
                return diskItems
            }
        }
        val items = tmdbClient.fetchUpcomingMovies(filter)
        if (items.isNotEmpty()) {
            cachedUpcomingByFilter[key] = Pair(now, items)
            saveDiskCache("upcoming_$key", items)
        }
        return items
    }

    suspend fun getSources(item: TmdbItem): List<TorrentSource> = withContext(Dispatchers.IO) {
        val cacheKey = "${if (item.isSeries) "series" else "movie"}_${item.id}"
        sourcesCache[cacheKey]?.let { return@withContext it }

        val imdbId = tmdbClient.getImdbIdForTmdb(item.isSeries, item.id)
        if (imdbId.isBlank()) return@withContext emptyList()

        val sources = scraperClient.fetchAllSources(imdbId, item.displayName, item.isSeries)
        if (sources.isNotEmpty()) {
            sourcesCache[cacheKey] = sources
        }
        sources
    }

    suspend fun getTrailerYoutubeKey(isSeries: Boolean, tmdbId: Int): String? =
        tmdbClient.fetchTrailerYoutubeKey(isSeries, tmdbId)

    fun clearCache() {
        cachedRecentlyReleased = null
        cachedTrendingMovies = null
        cachedTrendingIndia = null
        cachedTrendingSeries = null
        cachedUpcomingMovies = null
        cachedUpcomingByFilter.clear()
        sourcesCache.clear()
        try {
            val prefs = YMediaApplication.instance.getSharedPreferences("tmdb_feed_cache", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
        } catch (_: Exception) {}
        try {
            val dir = File(YMediaApplication.instance.cacheDir, "mycinema_http_cache")
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        } catch (_: Exception) {}
    }
}
