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
            when (filter.uppercase()) {
                "THEATRICAL" -> {
                    val url = "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&primary_release_date.gte=$todayStr&with_release_type=2|3&sort_by=popularity.desc&include_adult=false&page=1"
                    val request = Request.Builder().url(url).build()
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use emptyList<TmdbItem>()
                        val body = response.body?.string() ?: return@use emptyList<TmdbItem>()
                        val parsed = json.decodeFromString<TmdbSearchResponse>(body)
                        parsed.results
                            .filter { (it.releaseDate ?: "") >= todayStr }
                            .map { it.copy(mediaType = "movie", ottPlatformHint = "Theatres") }
                    }
                }
                "OTT" -> {
                    // Direct-to-Digital / OTT premiere only:
                    // Exclude any movie with a theatrical release (types 2,3) using without_release_type=2|3
                    val digitalOnlyUrl = "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&primary_release_date.gte=$todayStr&with_release_type=4|6&without_release_type=2|3&sort_by=popularity.desc&include_adult=false&page=1"
                    val ottItems = mutableListOf<TmdbItem>()
                    try {
                        val req1 = Request.Builder().url(digitalOnlyUrl).build()
                        httpClient.newCall(req1).execute().use { response ->
                            if (response.isSuccessful) {
                                response.body?.string()?.let {
                                    val parsed = json.decodeFromString<TmdbSearchResponse>(it)
                                    ottItems.addAll(parsed.results.filter { item -> (item.releaseDate ?: "") >= todayStr })
                                }
                            }
                        }
                    } catch (_: Exception) {}

                    // Also fetch upcoming titles with major streaming watch providers in India & global (strictly without theatrical release)
                    val providersUrl = "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&primary_release_date.gte=$todayStr&with_watch_providers=8|119|337|350|122|220|237|232&watch_region=IN&without_release_type=2|3&sort_by=popularity.desc&include_adult=false&page=1"
                    try {
                        val req2 = Request.Builder().url(providersUrl).build()
                        httpClient.newCall(req2).execute().use { response ->
                            if (response.isSuccessful) {
                                response.body?.string()?.let {
                                    val parsed = json.decodeFromString<TmdbSearchResponse>(it)
                                    val existingIds = ottItems.map { item -> item.id }.toSet()
                                    ottItems.addAll(parsed.results.filter { item -> (item.releaseDate ?: "") >= todayStr && !existingIds.contains(item.id) })
                                }
                            }
                        }
                    } catch (_: Exception) {}

                    // Deduplicate and tag with official OTT platform hint
                    ottItems.distinctBy { it.id }.map { item ->
                        val ov = (item.overview ?: "").lowercase()
                        val t = item.displayName.lowercase()
                        val platform = when {
                            ov.contains("netflix") || t.contains("netflix") -> "Netflix"
                            ov.contains("prime video") || ov.contains("amazon") -> "Prime Video"
                            ov.contains("hotstar") || ov.contains("disney+") -> "Disney+ Hotstar"
                            ov.contains("apple tv") -> "Apple TV+"
                            ov.contains("jiocinema") -> "JioCinema"
                            ov.contains("zee5") -> "Zee5"
                            ov.contains("sonyliv") -> "SonyLIV"
                            else -> "Direct OTT"
                        }
                        item.copy(mediaType = "movie", ottPlatformHint = platform)
                    }
                }
                else -> {
                    // ALL upcoming
                    val url = "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&primary_release_date.gte=$todayStr&sort_by=popularity.desc&include_adult=false&page=1"
                    val request = Request.Builder().url(url).build()
                    val allList = httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) emptyList<TmdbItem>()
                        else {
                            val body = response.body?.string() ?: ""
                            val parsed = json.decodeFromString<TmdbSearchResponse>(body)
                            parsed.results.filter { (it.releaseDate ?: "") >= todayStr }.map { it.copy(mediaType = "movie") }
                        }
                    }
                    if (allList.isNotEmpty()) allList else {
                        val fallbackUrl = "https://api.themoviedb.org/3/movie/upcoming?api_key=$apiKey&page=1"
                        val fallbackReq = Request.Builder().url(fallbackUrl).build()
                        httpClient.newCall(fallbackReq).execute().use { response ->
                            if (!response.isSuccessful) return@withContext emptyList()
                            val body = response.body?.string() ?: return@withContext emptyList()
                            val parsed = json.decodeFromString<TmdbSearchResponse>(body)
                            parsed.results
                                .filter { (it.releaseDate ?: "") >= todayStr }
                                .map { it.copy(mediaType = "movie") }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TmdbApiClient", "Error fetching upcoming: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchCinemaExtendedInfo(
        isSeries: Boolean,
        id: Int,
        releaseDate: String?
    ): CinemaExtendedInfo = withContext(Dispatchers.IO) {
        if (id <= 0 || apiKey.isBlank()) return@withContext CinemaExtendedInfo()
        try {
            val endpoint = if (isSeries) "tv" else "movie"
            val url = "https://api.themoviedb.org/3/$endpoint/$id?api_key=$apiKey&append_to_response=watch/providers,credits,images"
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext CinemaExtendedInfo()
                val body = response.body?.string() ?: return@withContext CinemaExtendedInfo()
                val root = org.json.JSONObject(body)

                val budget = root.optLong("budget", 0L)
                val revenue = root.optLong("revenue", 0L)
                val status = root.optString("status").takeIf { it.isNotBlank() }

                // Parse Watch Providers
                val wpObj = root.optJSONObject("watch/providers")?.optJSONObject("results")
                val inWp = wpObj?.optJSONObject("IN") ?: wpObj?.optJSONObject("US") ?: wpObj?.let {
                    if (it.keys().hasNext()) it.optJSONObject(it.keys().next()) else null
                }
                val tmdbWatchLink = inWp?.optString("link")?.takeIf { it.isNotBlank() }

                val providersList = mutableListOf<TmdbWatchProviderItem>()
                val flatrateArray = inWp?.optJSONArray("flatrate") ?: inWp?.optJSONArray("rent")
                if (flatrateArray != null) {
                    for (i in 0 until flatrateArray.length()) {
                        val p = flatrateArray.optJSONObject(i) ?: continue
                        providersList.add(
                            TmdbWatchProviderItem(
                                providerId = p.optInt("provider_id"),
                                providerName = p.optString("provider_name"),
                                logoPath = p.optString("logo_path").takeIf { it.isNotBlank() }
                            )
                        )
                    }
                }

                // Networks (for TV series like Netflix, HBO, Disney+, Amazon)
                val networksList = root.optJSONArray("networks")
                val primaryNetwork = if (networksList != null && networksList.length() > 0) {
                    networksList.getJSONObject(0).optString("name")
                } else null

                if (providersList.isEmpty() && primaryNetwork != null) {
                    providersList.add(
                        TmdbWatchProviderItem(
                            providerId = 0,
                            providerName = primaryNetwork,
                            logoPath = networksList?.getJSONObject(0)?.optString("logo_path")
                        )
                    )
                }

                // Parse Top Cast
                val topCast = mutableListOf<TmdbCastMember>()
                val creditsObj = root.optJSONObject("credits") ?: root.optJSONObject("aggregate_credits")
                val castArr = creditsObj?.optJSONArray("cast")
                if (castArr != null) {
                    val limit = minOf(16, castArr.length())
                    for (i in 0 until limit) {
                        val cObj = castArr.optJSONObject(i) ?: continue
                        val name = cObj.optString("name").ifBlank { cObj.optString("original_name") }
                        if (name.isBlank()) continue
                        val character = cObj.optString("character").ifBlank {
                            cObj.optJSONArray("roles")?.optJSONObject(0)?.optString("character") ?: ""
                        }
                        val profilePath = cObj.optString("profile_path").takeIf { it.isNotBlank() }
                        topCast.add(
                            TmdbCastMember(
                                id = cObj.optInt("id"),
                                name = name,
                                character = character,
                                profilePath = profilePath
                            )
                        )
                    }
                }

                // Parse Gallery Photos & Posters
                val galleryImages = mutableListOf<TmdbMediaImage>()
                val imagesObj = root.optJSONObject("images")
                val backdrops = imagesObj?.optJSONArray("backdrops")
                val posters = imagesObj?.optJSONArray("posters")

                if (backdrops != null) {
                    val bLimit = minOf(12, backdrops.length())
                    for (i in 0 until bLimit) {
                        val b = backdrops.optJSONObject(i) ?: continue
                        val path = b.optString("file_path")
                        if (path.isNotBlank()) {
                            galleryImages.add(TmdbMediaImage(filePath = path, isBackdrop = true))
                        }
                    }
                }

                if (posters != null) {
                    val pLimit = minOf(12, posters.length())
                    for (i in 0 until pLimit) {
                        val p = posters.optJSONObject(i) ?: continue
                        val path = p.optString("file_path")
                        if (path.isNotBlank()) {
                            galleryImages.add(TmdbMediaImage(filePath = path, isBackdrop = false))
                        }
                    }
                }

                // Parse Title Logo
                var titleLogoPath: String? = null
                val logos = imagesObj?.optJSONArray("logos")
                if (logos != null && logos.length() > 0) {
                    var bestLogo: String? = null
                    for (i in 0 until logos.length()) {
                        val lObj = logos.optJSONObject(i) ?: continue
                        val lang = lObj.optString("iso_639_1")
                        val path = lObj.optString("file_path")
                        if (path.isNotBlank()) {
                            if (lang.equals("en", ignoreCase = true) || bestLogo == null) {
                                bestLogo = path
                                if (lang.equals("en", ignoreCase = true)) break
                            }
                        }
                    }
                    titleLogoPath = bestLogo
                }

                // Parse Seasons (for TV Series)
                val seasonsList = mutableListOf<TmdbSeasonInfo>()
                val seasonsArr = root.optJSONArray("seasons")
                if (seasonsArr != null) {
                    for (i in 0 until seasonsArr.length()) {
                        val sObj = seasonsArr.optJSONObject(i) ?: continue
                        val sNum = sObj.optInt("season_number")
                        val sName = sObj.optString("name").ifBlank { "Season $sNum" }
                        val epCount = sObj.optInt("episode_count", 0)
                        val poster = sObj.optString("poster_path").takeIf { it.isNotBlank() }
                        val overview = sObj.optString("overview").takeIf { it.isNotBlank() }
                        seasonsList.add(
                            TmdbSeasonInfo(
                                seasonNumber = sNum,
                                name = sName,
                                episodeCount = epCount,
                                posterPath = poster,
                                overview = overview
                            )
                        )
                    }
                }

                // Parse Collection / Prequels & Sequels
                var collectionName: String? = null
                var collectionId: Int? = null
                val previousParts = mutableListOf<TmdbItem>()

                val collObj = root.optJSONObject("belongs_to_collection")
                if (collObj != null) {
                    collectionId = collObj.optInt("id")
                    collectionName = collObj.optString("name")
                    if (collectionId > 0) {
                        try {
                            val collUrl = "https://api.themoviedb.org/3/collection/$collectionId?api_key=$apiKey"
                            val collReq = Request.Builder().url(collUrl).build()
                            httpClient.newCall(collReq).execute().use { collResp ->
                                if (collResp.isSuccessful) {
                                    val collBody = collResp.body?.string()
                                    if (!collBody.isNullOrBlank()) {
                                        val collJson = org.json.JSONObject(collBody)
                                        val partsArr = collJson.optJSONArray("parts")
                                        if (partsArr != null) {
                                            for (j in 0 until partsArr.length()) {
                                                val part = partsArr.optJSONObject(j) ?: continue
                                                val partId = part.optInt("id")
                                                if (partId == id) continue // Skip the current movie itself
                                                val partDate = part.optString("release_date")
                                                previousParts.add(
                                                    TmdbItem(
                                                        id = partId,
                                                        title = part.optString("title"),
                                                        mediaType = "movie",
                                                        posterPath = part.optString("poster_path").takeIf { it.isNotBlank() },
                                                        backdropPath = part.optString("backdrop_path").takeIf { it.isNotBlank() },
                                                        overview = part.optString("overview"),
                                                        releaseDate = partDate,
                                                        voteAverage = part.optDouble("vote_average", 0.0)
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("TmdbApiClient", "Error fetching collection: ${e.message}")
                        }
                    }
                }

                // Sort chronological by release date
                previousParts.sortBy { it.releaseDate ?: "" }

                val officialPlatform = providersList.firstOrNull()?.providerName ?: primaryNetwork
                val isDirectOtt = isSeries || (officialPlatform != null && providersList.isNotEmpty())

                CinemaExtendedInfo(
                    budget = budget,
                    revenue = revenue,
                    status = status,
                    collectionName = collectionName,
                    collectionId = collectionId,
                    previousParts = previousParts,
                    topCast = topCast,
                    galleryImages = galleryImages,
                    watchProviders = providersList,
                    isDirectOtt = isDirectOtt,
                    officialPlatformName = officialPlatform,
                    releaseDate = releaseDate ?: root.optString("release_date"),
                    tmdbWatchLink = tmdbWatchLink,
                    seasons = seasonsList,
                    logoPath = titleLogoPath
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("TmdbApiClient", "Error fetching extended info: ${e.message}")
            CinemaExtendedInfo()
        }
    }

    suspend fun fetchSeasonEpisodes(tvId: Int, seasonNumber: Int): List<TmdbEpisodeItem> = withContext(Dispatchers.IO) {
        if (tvId <= 0 || apiKey.isBlank()) return@withContext emptyList()
        try {
            val url = "https://api.themoviedb.org/3/tv/$tvId/season/$seasonNumber?api_key=$apiKey"
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val root = org.json.JSONObject(body)
                val episodesArr = root.optJSONArray("episodes") ?: return@withContext emptyList()
                val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                val list = mutableListOf<TmdbEpisodeItem>()
                for (i in 0 until episodesArr.length()) {
                    val epObj = episodesArr.optJSONObject(i) ?: continue
                    val epNum = epObj.optInt("episode_number")
                    val name = epObj.optString("name").ifBlank { "Episode $epNum" }
                    val overview = epObj.optString("overview").takeIf { it.isNotBlank() }
                    val airDate = epObj.optString("air_date").takeIf { it.isNotBlank() }
                    val stillPath = epObj.optString("still_path").takeIf { it.isNotBlank() }
                    val voteAvg = epObj.optDouble("vote_average", 0.0)
                    val released = airDate != null && airDate <= todayStr
                    list.add(
                        TmdbEpisodeItem(
                            seasonNumber = seasonNumber,
                            episodeNumber = epNum,
                            name = name,
                            overview = overview,
                            airDate = airDate,
                            stillPath = stillPath,
                            voteAverage = voteAvg,
                            released = released
                        )
                    )
                }
                list
            }
        } catch (e: Exception) {
            android.util.Log.e("TmdbApiClient", "Error fetching season episodes: ${e.message}")
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
                val trailer = parsed.results
                    .filter { it.site.equals("YouTube", ignoreCase = true) && it.key.isNotBlank() }
                    .maxByOrNull { item ->
                        var score = 0
                        if (item.official) score += 20
                        if (item.type.equals("Trailer", ignoreCase = true)) score += 10
                        if (item.name.contains("Official Trailer", ignoreCase = true)) score += 15
                        else if (item.name.contains("Trailer", ignoreCase = true)) score += 5
                        else if (item.type.equals("Teaser", ignoreCase = true)) score += 2
                        score
                    }
                trailer?.key
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchItemById(isSeries: Boolean, tmdbId: Int): TmdbItem? = withContext(Dispatchers.IO) {
        if (tmdbId <= 0 || apiKey.isBlank()) return@withContext null
        try {
            val endpoint = if (isSeries) "tv" else "movie"
            val url = "https://api.themoviedb.org/3/$endpoint/$tmdbId?api_key=$apiKey"
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val item = json.decodeFromString<TmdbItem>(body)
                item.copy(mediaType = if (isSeries) "tv" else "movie")
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchTitleLogo(type: String, tmdbId: Int): String? = withContext(Dispatchers.IO) {
        if (tmdbId <= 0 || apiKey.isBlank()) return@withContext null
        try {
            val endpoint = if (type.lowercase() == "tv" || type.lowercase() == "series") "tv" else "movie"
            val url = "https://api.themoviedb.org/3/$endpoint/$tmdbId/images?api_key=$apiKey"
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val root = org.json.JSONObject(body)
                val logos = root.optJSONArray("logos") ?: return@withContext null
                var bestLogo: String? = null
                for (i in 0 until logos.length()) {
                    val lObj = logos.optJSONObject(i) ?: continue
                    val lang = lObj.optString("iso_639_1")
                    val path = lObj.optString("file_path")
                    if (path.isNotBlank()) {
                        if (lang.equals("en", ignoreCase = true) || bestLogo == null) {
                            bestLogo = path
                            if (lang.equals("en", ignoreCase = true)) break
                        }
                    }
                }
                bestLogo
            }
        } catch (_: Exception) {
            null
        }
    }
}
