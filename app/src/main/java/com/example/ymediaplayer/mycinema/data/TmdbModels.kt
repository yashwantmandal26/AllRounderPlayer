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
    val type: String = ""
)
