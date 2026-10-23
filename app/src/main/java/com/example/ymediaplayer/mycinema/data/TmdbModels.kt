package com.example.ymediaplayer.mycinema.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TmdbSearchResponse(
    val page: Int = 1,
    val results: List<TmdbItem> = emptyList(),
    @SerialName("total_results") val totalResults: Int = 0,
    @SerialName("total_pages") val totalPages: Int = 0
)

@Serializable
data class TmdbItem(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    @SerialName("media_type") val mediaType: String? = "movie",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val overview: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0
) {
    val displayName: String
        get() = (title ?: name ?: "Untitled").trim()

    val displayYear: String
        get() = (releaseDate ?: firstAirDate ?: "").take(4)

    val isSeries: Boolean
        get() = mediaType?.lowercase() == "tv" || mediaType?.lowercase() == "series"

    val fullPosterUrl: String?
        get() = posterPath?.let { if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/w780$it" }

    val fullBackdropUrl: String?
        get() = backdropPath?.let { if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/w1280$it" }

    val ratingFormatted: String
        get() = if (voteAverage > 0) String.format(java.util.Locale.US, "%.1f", voteAverage) else "—"

    val formattedReleaseDate: String
        get() {
            val raw = releaseDate ?: firstAirDate ?: return ""
            return try {
                val parts = raw.split("-")
                if (parts.size == 3) {
                    val year = parts[0]
                    val month = when (parts[1]) {
                        "01" -> "Jan"; "02" -> "Feb"; "03" -> "Mar"; "04" -> "Apr"
                        "05" -> "May"; "06" -> "Jun"; "07" -> "Jul"; "08" -> "Aug"
                        "09" -> "Sep"; "10" -> "Oct"; "11" -> "Nov"; "12" -> "Dec"
                        else -> parts[1]
                    }
                    val day = parts[2].trimStart('0')
                    "$day $month $year"
                } else raw
            } catch (_: Exception) { raw }
        }
}

@Serializable
data class CinemaWatchHistoryItem(
    val id: Int,
    val title: String,
    val posterUrl: String? = null,
    val isSeries: Boolean = false,
    val year: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val progressMs: Long = 0L,
    val durationMs: Long = 0L
) {
    val progressPercent: Int
        get() = if (durationMs > 0L) ((progressMs * 100) / durationMs).toInt().coerceIn(0, 100) else 0

    val timeAgoFormatted: String
        get() {
            val diffSec = (System.currentTimeMillis() - timestamp) / 1000
            return when {
                diffSec < 60 -> "Just now"
                diffSec < 3600 -> "${diffSec / 60}m ago"
                diffSec < 86400 -> "${diffSec / 3600}h ago"
                diffSec < 604800 -> "${diffSec / 86400}d ago"
                else -> "${diffSec / 604800}w ago"
            }
        }

    fun toTmdbItem(): TmdbItem {
        return TmdbItem(
            id = id,
            title = if (!isSeries) title else null,
            name = if (isSeries) title else null,
            mediaType = if (isSeries) "tv" else "movie",
            posterPath = posterUrl,
            backdropPath = null,
            overview = "",
            releaseDate = if (!isSeries) year else null,
            firstAirDate = if (isSeries) year else null,
            voteAverage = 0.0
        )
    }
}

@Serializable
data class TmdbExternalIds(
    val id: Int = 0,
    @SerialName("imdb_id") val imdbId: String? = null
)

@Serializable
data class TmdbVideosResponse(
    val id: Int = 0,
    val results: List<TmdbVideoItem> = emptyList()
)

@Serializable
data class TmdbVideoItem(
    val key: String = "",
    val site: String = "",
    val type: String = "",
    val name: String = "",
    val official: Boolean = false
)
