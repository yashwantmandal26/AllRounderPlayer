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
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    val ottPlatformHint: String? = null
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

    val isUnreleased: Boolean
        get() {
            val raw = releaseDate ?: firstAirDate ?: return false
            if (raw.isBlank()) return false
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            return raw > today
        }

    val daysUntilRelease: Int?
        get() {
            val raw = releaseDate ?: firstAirDate ?: return null
            return try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                val relDate = sdf.parse(raw) ?: return null
                val today = sdf.parse(sdf.format(java.util.Date())) ?: return null
                val diff = (relDate.time - today.time) / (1000 * 60 * 60 * 24)
                if (diff >= 0) diff.toInt() else null
            } catch (_: Exception) { null }
        }

    val countdownLabel: String
        get() {
            val days = daysUntilRelease ?: return formattedReleaseDate
            return when {
                days == 0 -> "Releasing Today!"
                days == 1 -> "Releasing Tomorrow!"
                days < 7 -> "Releasing in $days days"
                days < 30 -> "In ${days / 7} weeks"
                days < 60 -> "In 1 month"
                else -> "In ${days / 30} months"
            }
        }

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

@Serializable
data class TmdbWatchProviderItem(
    @SerialName("provider_id") val providerId: Int = 0,
    @SerialName("provider_name") val providerName: String = "",
    @SerialName("logo_path") val logoPath: String? = null
) {
    val fullLogoUrl: String?
        get() = logoPath?.let { "https://image.tmdb.org/t/p/w185$it" }
}

@Serializable
data class CinemaExtendedInfo(
    val budget: Long = 0L,
    val revenue: Long = 0L,
    val status: String? = null,
    val collectionName: String? = null,
    val collectionId: Int? = null,
    val previousParts: List<TmdbItem> = emptyList(),
    val watchProviders: List<TmdbWatchProviderItem> = emptyList(),
    val isDirectOtt: Boolean = false,
    val officialPlatformName: String? = null,
    val releaseDate: String? = null,
    val tmdbWatchLink: String? = null
) {
    val budgetFormatted: String
        get() = formatCurrencyWithInr(budget)

    val revenueFormatted: String
        get() = if (revenue > 0) formatCurrencyWithInr(revenue) else "Pending / Undisclosed"

    val boxOfficeVerdict: BoxOfficeVerdict?
        get() {
            if (budget <= 0L && revenue <= 0L) return null
            if (revenue <= 0L) return BoxOfficeVerdict.PENDING
            val ratio = revenue.toDouble() / budget.coerceAtLeast(1L).toDouble()
            return when {
                ratio >= 3.0 -> BoxOfficeVerdict.ALL_TIME_BLOCKBUSTER
                ratio >= 2.2 -> BoxOfficeVerdict.SUPERHIT
                ratio >= 1.5 -> BoxOfficeVerdict.HIT
                ratio >= 1.0 -> BoxOfficeVerdict.AVERAGE
                else -> BoxOfficeVerdict.UNDERPERFORMED
            }
        }
}

enum class BoxOfficeVerdict(val label: String, val colorHex: Long) {
    ALL_TIME_BLOCKBUSTER("🔥 ALL-TIME BLOCKBUSTER", 0xFF00E676),
    SUPERHIT("🌟 SUPERHIT", 0xFF00E5FF),
    HIT("📈 HIT / PROFITABLE", 0xFF26A69A),
    AVERAGE("⚖️ AVERAGE / RECOVERED", 0xFFFFB300),
    UNDERPERFORMED("📉 UNDERPERFORMED", 0xFFFF5252),
    PENDING("⏳ PENDING RELEASE", 0xFFAB47BC)
}

fun formatCurrencyWithInr(amountUsd: Long): String {
    if (amountUsd <= 0L) return "Not Disclosed"
    val usdStr = when {
        amountUsd >= 1_000_000_000 -> String.format(java.util.Locale.US, "$%.2fB", amountUsd / 1_000_000_000.0)
        amountUsd >= 1_000_000 -> String.format(java.util.Locale.US, "$%.1fM", amountUsd / 1_000_000.0)
        amountUsd >= 1_000 -> String.format(java.util.Locale.US, "$%,d", amountUsd)
        else -> "$$amountUsd"
    }
    // Approx INR calculation: 1 USD ≈ 84 INR. 1 Crore = 10,000,000 INR
    val inrCrores = (amountUsd * 84.0) / 10_000_000.0
    val inrStr = when {
        inrCrores >= 1.0 -> String.format(java.util.Locale.US, " (~₹%.0f Cr)", inrCrores)
        else -> ""
    }
    return "$usdStr$inrStr"
}
