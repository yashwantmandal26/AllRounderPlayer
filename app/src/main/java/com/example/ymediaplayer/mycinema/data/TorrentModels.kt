package com.example.ymediaplayer.mycinema.data

import kotlinx.serialization.Serializable

@Serializable
data class TorrentSource(
    val title: String,
    val quality: String,
    val size: String,
    val sizeBytes: Long = 0L,
    val magnet: String,
    val seeds: Int,
    val peers: Int = 0,
    val type: String = "web",
    val provider: String = "Torrentio",
    val isHindi: Boolean = false,
    val isTamil: Boolean = false,
    val isTelugu: Boolean = false,
    val isKannada: Boolean = false,
    val isBengali: Boolean = false,
    val isMalayalam: Boolean = false,
    val isEnglish: Boolean = false,
    val isKorean: Boolean = false,
    val isHevc: Boolean = false,
    val parsedSeason: Int? = null,
    val parsedEpisode: Int? = null,
    val parsedEpisodeEnd: Int? = null,
    val parsedSeasons: List<Int> = emptyList(),
    val isSeasonPack: Boolean = false,
    val isCompleteSeries: Boolean = false
) {
    val hasHindi: Boolean
        get() = isHindi || title.contains("hindi", ignoreCase = true) || title.contains("hin.", ignoreCase = true) || title.contains("[hin", ignoreCase = true)

    val hasTamil: Boolean
        get() = isTamil || title.contains("tamil", ignoreCase = true) || title.contains("tam.", ignoreCase = true) || title.contains("[tam", ignoreCase = true)

    val hasTelugu: Boolean
        get() = isTelugu || title.contains("telugu", ignoreCase = true) || title.contains("tel.", ignoreCase = true) || title.contains("[tel", ignoreCase = true)

    val hasKannada: Boolean
        get() = isKannada || title.contains("kannada", ignoreCase = true) || title.contains("kan.", ignoreCase = true) || title.contains("[kan", ignoreCase = true)

    val hasBengali: Boolean
        get() = isBengali || title.contains("bangla", ignoreCase = true) || title.contains("bengali", ignoreCase = true) || title.contains("ben.", ignoreCase = true) || title.contains("[ben", ignoreCase = true)

    val hasMalayalam: Boolean
        get() = isMalayalam || title.contains("malayalam", ignoreCase = true) || title.contains("mal.", ignoreCase = true) || title.contains("[mal", ignoreCase = true)

    val hasEnglish: Boolean
        get() = isEnglish || title.contains("english", ignoreCase = true) || title.contains("eng.", ignoreCase = true) || title.contains("[eng", ignoreCase = true)

    val hasKorean: Boolean
        get() = isKorean || title.contains("korean", ignoreCase = true) || title.contains("kor.", ignoreCase = true) || title.contains("[kor", ignoreCase = true) || title.contains("한국", ignoreCase = true) || title.contains("🇰🇷")

    val normalizedQuality: String
        get() = when {
            quality.contains("2160", ignoreCase = true) || quality.contains("4k", ignoreCase = true) -> "2160p"
            quality.contains("1080", ignoreCase = true) -> "1080p"
            quality.contains("720", ignoreCase = true) -> "720p"
            quality.contains("480", ignoreCase = true) -> "480p"
            else -> "1080p"
        }

    val isDualAudio: Boolean
        get() = title.contains("dual", ignoreCase = true) || title.contains("multi", ignoreCase = true)

    val isHdr: Boolean
        get() = title.contains("hdr", ignoreCase = true) || title.contains("dovi", ignoreCase = true) || title.contains("dolby", ignoreCase = true)

    val ripType: String?
        get() = when {
            title.contains("remux", ignoreCase = true) -> "REMUX"
            title.contains("bluray", ignoreCase = true) || title.contains("bdrip", ignoreCase = true) -> "BluRay"
            title.contains("web-dl", ignoreCase = true) || title.contains("webrip", ignoreCase = true) || title.contains("web", ignoreCase = true) -> "WEB-DL"
            title.contains("hdrip", ignoreCase = true) -> "HDRip"
            title.contains("cam", ignoreCase = true) || title.contains("hdcam", ignoreCase = true) -> "CAM"
            else -> null
        }

    val parsedSizeBytes: Long
        get() = if (sizeBytes > 0) sizeBytes else parseSizeToBytes(size)

    val speedLabel: String
        get() = when {
            seeds >= 1500 -> "⚡ ULTRA"
            seeds >= 400 -> "🚀 FAST"
            seeds >= 50 -> "🏎️ GOOD"
            seeds >= 15 -> "📶 MODERATE"
            else -> "⏳ SLOW"
        }

    val estimatedSpeedFormatted: String
        get() = when {
            seeds >= 1500 -> "⚡ ~15-25 MB/s"
            seeds >= 500 -> "⚡ ~8-15 MB/s"
            seeds >= 150 -> "🚀 ~4-8 MB/s"
            seeds >= 40 -> "🏎️ ~1.5-4 MB/s"
            seeds >= 15 -> "📶 ~500KB-1.5MB/s"
            else -> "⏳ ~100-400 KB/s"
        }

    val estimatedEtaFormatted: String
        get() {
            val bytes = if (sizeBytes > 0) sizeBytes else parseSizeToBytes(size)
            if (bytes <= 0) return ""
            val estBytesPerSec = when {
                seeds >= 1500 -> 18L * 1024 * 1024
                seeds >= 500 -> 10L * 1024 * 1024
                seeds >= 150 -> 5L * 1024 * 1024
                seeds >= 40 -> 2L * 1024 * 1024
                seeds >= 15 -> 800L * 1024
                else -> 250L * 1024
            }
            val seconds = bytes / estBytesPerSec
            return when {
                seconds < 60 -> "~${seconds}s"
                seconds < 3600 -> "~${(seconds / 60).coerceAtLeast(1)}m"
                else -> {
                    val hrs = seconds / 3600
                    val mins = (seconds % 3600) / 60
                    if (mins > 0) "~${hrs}h ${mins}m" else "~${hrs}h"
                }
            }
        }

    val healthScore: Double
        get() {
            var score = seeds * 3.0 + peers * 0.4
            if (normalizedQuality == "2160p") score += 15.0
            if (normalizedQuality == "1080p") score += 10.0
            if (isHindi) score += 20.0
            return score
        }

    private fun parseSizeToBytes(sizeStr: String): Long {
        val clean = sizeStr.trim().uppercase()
        val numStr = clean.filter { it.isDigit() || it == '.' }
        val num = numStr.toDoubleOrNull() ?: return 0L
        return when {
            clean.contains("GB") || clean.contains("GIB") -> (num * 1024 * 1024 * 1024).toLong()
            clean.contains("MB") || clean.contains("MIB") -> (num * 1024 * 1024).toLong()
            clean.contains("KB") || clean.contains("KIB") -> (num * 1024).toLong()
            else -> num.toLong()
        }
    }
}

data class ParsedTorrentEpisodeInfo(
    val primarySeason: Int? = null,
    val primaryEpisode: Int? = null,
    val episodeEnd: Int? = null,
    val seasons: List<Int> = emptyList(),
    val isPack: Boolean = false,
    val isCompleteSeries: Boolean = false
)

object TorrentEpisodeParser {
    private val SXXEXX_REGEX = Regex(
        """(?i)\bS(\d{1,2})[.\s_-]*E(\d{1,3})(?:\s*[-~–to]+\s*[Ee]?(\d{1,3}))?\b"""
    )
    private val SXE_CROSS_REGEX = Regex(
        """(?i)(?<![\dA-Za-z])(\d{1,2})x(\d{1,3})(?:\s*-\s*(\d{1,3}))?\b"""
    )
    private val SEASON_EP_WORD_REGEX = Regex(
        """(?i)\bSeason[.\s_-]*(\d{1,2})[.\s_-]*(?:Episode|Ep)[.\s_-]*(\d{1,3})\b"""
    )
    private val SEASON_RANGE_REGEX = Regex(
        """(?i)\b(?:Seasons?|S)[.\s_-]*(\d{1,2})(?![.\s_-]*[Ee]\d)\s*(?:-|–|to)\s*(?:Seasons?|S)?[.\s_-]*(\d{1,2})\b(?![.\s_-]*[Ee]\d|[pP]|bit|k|mb|gb)"""
    )
    private val SINGLE_S_TOKEN_REGEX = Regex(
        """(?i)\bS(\d{1,2})\b(?![.\s_-]*(?:E\d|\d{1,3}x))"""
    )
    private val SINGLE_SEASON_WORD_REGEX = Regex(
        """(?i)\bSeason[.\s_-]*(\d{1,2})\b"""
    )
    private val STANDALONE_EP_REGEX = Regex(
        """(?i)\b(?:Episode|Ep)[.\s_-]*(\d{1,3})\b"""
    )
    private val COMPLETE_SERIES_REGEX = Regex(
        """(?i)\b(complete\s+series|full\s+series|all\s+seasons|complete\s+collection)\b"""
    )
    private val PACK_KEYWORD_REGEX = Regex(
        """(?i)\b(pack|batch|complete|seasons)\b"""
    )

    fun parse(
        title: String,
        fallbackSeason: Int? = null,
        fallbackEpisode: Int? = null
    ): ParsedTorrentEpisodeInfo {
        val seasonsSet = linkedSetOf<Int>()
        var primarySeason: Int? = null
        var primaryEpisode: Int? = null
        var episodeEnd: Int? = null

        val sxxMatch = SXXEXX_REGEX.find(title)
        if (sxxMatch != null) {
            primarySeason = sxxMatch.groupValues[1].toIntOrNull()
            primaryEpisode = sxxMatch.groupValues[2].toIntOrNull()
            episodeEnd = sxxMatch.groupValues.getOrNull(3)?.toIntOrNull()
        } else {
            val crossMatch = SXE_CROSS_REGEX.findAll(title).firstOrNull { m ->
                val ep = m.groupValues[2].toIntOrNull()
                ep != null && ep != 264 && ep != 265
            }
            if (crossMatch != null) {
                primarySeason = crossMatch.groupValues[1].toIntOrNull()
                primaryEpisode = crossMatch.groupValues[2].toIntOrNull()
                episodeEnd = crossMatch.groupValues.getOrNull(3)?.toIntOrNull()
            } else {
                val wordMatch = SEASON_EP_WORD_REGEX.find(title)
                if (wordMatch != null) {
                    primarySeason = wordMatch.groupValues[1].toIntOrNull()
                    primaryEpisode = wordMatch.groupValues[2].toIntOrNull()
                }
            }
        }

        primarySeason?.let { if (it in 1..99) seasonsSet.add(it) }

        SEASON_RANGE_REGEX.findAll(title).forEach { m ->
            val start = m.groupValues[1].toIntOrNull()
            val end = m.groupValues[2].toIntOrNull()
            if (start != null && end != null && start in 1..99 && end in start..99 && (end - start) <= 40) {
                for (s in start..end) seasonsSet.add(s)
            }
        }

        SINGLE_S_TOKEN_REGEX.findAll(title).forEach { m ->
            val s = m.groupValues[1].toIntOrNull()
            if (s != null && s in 1..99) seasonsSet.add(s)
        }

        SINGLE_SEASON_WORD_REGEX.findAll(title).forEach { m ->
            val s = m.groupValues[1].toIntOrNull()
            if (s != null && s in 1..99) seasonsSet.add(s)
        }

        if (primaryEpisode == null) {
            STANDALONE_EP_REGEX.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { ep ->
                primaryEpisode = ep
            }
        }

        if (seasonsSet.isEmpty() && fallbackSeason != null && fallbackSeason > 0) {
            seasonsSet.add(fallbackSeason)
        }
        if (primarySeason == null) {
            primarySeason = seasonsSet.firstOrNull() ?: fallbackSeason?.takeIf { it > 0 }
        }
        if (primaryEpisode == null && fallbackEpisode != null && fallbackEpisode > 0) {
            primaryEpisode = fallbackEpisode
        }

        val isCompleteSeries = COMPLETE_SERIES_REGEX.containsMatchIn(title) ||
            (seasonsSet.isEmpty() && Regex("""(?i)\bcomplete\b""").containsMatchIn(title))
        val hasPackKeyword = PACK_KEYWORD_REGEX.containsMatchIn(title)
        val hasEpisodeRange = primaryEpisode != null && episodeEnd != null && episodeEnd > primaryEpisode
        val isMultiSeason = seasonsSet.size > 1
        val isSeasonWithoutEpisode = seasonsSet.isNotEmpty() && primaryEpisode == null

        val isPack = isCompleteSeries || isMultiSeason || hasEpisodeRange || isSeasonWithoutEpisode ||
            (hasPackKeyword && primaryEpisode == null)

        return ParsedTorrentEpisodeInfo(
            primarySeason = primarySeason,
            primaryEpisode = primaryEpisode,
            episodeEnd = episodeEnd,
            seasons = seasonsSet.toList().sorted(),
            isPack = isPack,
            isCompleteSeries = isCompleteSeries
        )
    }

    fun isPackSource(title: String): Boolean = parse(title).isPack

    fun isPackSource(source: TorrentSource): Boolean {
        return source.isSeasonPack || source.isCompleteSeries || source.parsedSeasons.size > 1 ||
            parse(source.title, source.parsedSeason, source.parsedEpisode).isPack
    }

    fun detectedSeasons(source: TorrentSource): List<Int> {
        if (source.parsedSeasons.isNotEmpty()) return source.parsedSeasons
        val parsed = parse(source.title, source.parsedSeason, source.parsedEpisode)
        return parsed.seasons
    }

    fun matchesSeason(source: TorrentSource, targetSeason: Int): Boolean {
        if (source.isCompleteSeries) return true
        val parsed = if (source.parsedSeasons.isNotEmpty() || source.parsedSeason != null || source.isSeasonPack) {
            ParsedTorrentEpisodeInfo(
                primarySeason = source.parsedSeason,
                primaryEpisode = source.parsedEpisode,
                episodeEnd = source.parsedEpisodeEnd,
                seasons = if (source.parsedSeasons.isNotEmpty()) source.parsedSeasons else listOfNotNull(source.parsedSeason),
                isPack = source.isSeasonPack,
                isCompleteSeries = source.isCompleteSeries
            )
        } else {
            parse(source.title)
        }
        if (parsed.isCompleteSeries) return true
        return targetSeason in parsed.seasons
    }

    fun matchesRequestedEpisode(
        source: TorrentSource,
        targetSeason: Int,
        targetEpisode: Int,
        allowUntagged: Boolean = false
    ): Boolean {
        val parsed = parse(source.title, source.parsedSeason, source.parsedEpisode)
        val effectiveComplete = source.isCompleteSeries || parsed.isCompleteSeries
        val effectiveSeasons = (source.parsedSeasons + parsed.seasons + listOfNotNull(source.parsedSeason, parsed.primarySeason))
            .filter { it > 0 }.distinct()
        val effectiveEp = source.parsedEpisode ?: parsed.primaryEpisode
        val effectiveEpEnd = source.parsedEpisodeEnd ?: parsed.episodeEnd
        val effectivePack = source.isSeasonPack || parsed.isPack

        if (effectiveComplete) return true

        if (effectiveSeasons.isNotEmpty()) {
            if (targetSeason !in effectiveSeasons) return false
            if (effectiveSeasons.size > 1 && effectiveEp == null) return true
            if (effectiveEp != null) {
                return if (effectiveEpEnd != null && effectiveEpEnd >= effectiveEp) {
                    targetEpisode in effectiveEp..effectiveEpEnd
                } else {
                    effectiveEp == targetEpisode
                }
            }
            // Single season pack (e.g. S02 complete without E01)
            return true
        }

        // No season explicitly parsed
        return effectivePack || allowUntagged
    }

    /**
     * Parses (season, episode) from a file path inside a pack (checks filename first, then path folders).
     */
    fun parseFileEpisode(pathOrName: String): Pair<Int?, Int?> {
        val parsedFull = parse(pathOrName)
        if (parsedFull.primaryEpisode != null) {
            return Pair(parsedFull.primarySeason, parsedFull.primaryEpisode)
        }
        // Fallback: leading episode digits in filename like "01 - Pilot.mkv" or "02.mp4"
        val fileName = pathOrName.substringAfterLast('/').substringAfterLast('\\')
        val leadingNum = Regex("""^(\d{1,3})[\s._-]""").find(fileName)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return Pair(parsedFull.primarySeason, leadingNum)
    }
}
