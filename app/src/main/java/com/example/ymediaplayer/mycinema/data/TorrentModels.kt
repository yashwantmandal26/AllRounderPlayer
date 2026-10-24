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
    val isHevc: Boolean = false,
    val parsedSeason: Int? = null,
    val parsedEpisode: Int? = null,
    val isSeasonPack: Boolean = false
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
