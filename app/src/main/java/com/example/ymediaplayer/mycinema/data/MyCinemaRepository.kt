package com.example.ymediaplayer.mycinema.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MyCinemaRepository(
    private val tmdbClient: TmdbApiClient = TmdbApiClient(),
    private val scraperClient: TorrentScraperClient = TorrentScraperClient()
) {
    companion object {
        private const val CACHE_TTL_MS = 4 * 60 * 60 * 1000L // 4 hours in-memory TTL

        private var cachedRecentlyReleased: Pair<Long, List<TmdbItem>>? = null
        private var cachedTrendingMovies: Pair<Long, List<TmdbItem>>? = null
        private var cachedTrendingIndia: Pair<Long, List<TmdbItem>>? = null
        private var cachedTrendingSeries: Pair<Long, List<TmdbItem>>? = null
        private var cachedUpcomingMovies: Pair<Long, List<TmdbItem>>? = null

        private val sourcesCache = java.util.concurrent.ConcurrentHashMap<String, List<TorrentSource>>()
    }

    suspend fun search(query: String): List<TmdbItem> = tmdbClient.searchMulti(query)

    suspend fun getRecentlyReleased(forceRefresh: Boolean = false): List<TmdbItem> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedRecentlyReleased?.let { now - it.first < CACHE_TTL_MS } == true) {
            return cachedRecentlyReleased!!.second
        }
        val items = tmdbClient.fetchRecentlyReleased()
        if (items.isNotEmpty()) {
            cachedRecentlyReleased = Pair(now, items)
        }
        return items
    }

    suspend fun getTrendingMovies(forceRefresh: Boolean = false): List<TmdbItem> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedTrendingMovies?.let { now - it.first < CACHE_TTL_MS } == true) {
            return cachedTrendingMovies!!.second
        }
        val items = tmdbClient.fetchTrending(isSeries = false)
        if (items.isNotEmpty()) {
            cachedTrendingMovies = Pair(now, items)
        }
        return items
    }

    suspend fun getTrendingSeries(forceRefresh: Boolean = false): List<TmdbItem> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedTrendingSeries?.let { now - it.first < CACHE_TTL_MS } == true) {
            return cachedTrendingSeries!!.second
        }
        val items = tmdbClient.fetchTrending(isSeries = true)
        if (items.isNotEmpty()) {
            cachedTrendingSeries = Pair(now, items)
        }
        return items
    }

    suspend fun getTrendingIndia(forceRefresh: Boolean = false): List<TmdbItem> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedTrendingIndia?.let { now - it.first < CACHE_TTL_MS } == true) {
            return cachedTrendingIndia!!.second
        }
        val items = tmdbClient.fetchTrendingInIndia(isSeries = false)
        if (items.isNotEmpty()) {
            cachedTrendingIndia = Pair(now, items)
        }
        return items
    }

    suspend fun getUpcomingMovies(forceRefresh: Boolean = false): List<TmdbItem> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedUpcomingMovies?.let { now - it.first < CACHE_TTL_MS } == true) {
            return cachedUpcomingMovies!!.second
        }
        val items = tmdbClient.fetchUpcomingMovies()
        if (items.isNotEmpty()) {
            cachedUpcomingMovies = Pair(now, items)
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
}
