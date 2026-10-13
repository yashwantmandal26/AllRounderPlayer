package com.example.ymediaplayer.ui

internal data class SubtitleLine(val startMs: Long, val endMs: Long, val text: String)

internal object SubtitleTimeline {
    private val timing = Regex("((?:\\d+:)?\\d{2}:\\d{2}[.,]\\d{3})\\s*-->\\s*((?:\\d+:)?\\d{2}:\\d{2}[.,]\\d{3})")

    fun parse(text: String): List<SubtitleLine> = text.removePrefix("\uFEFF").replace("\r\n", "\n")
        .split(Regex("\n[ \\t]*\n"))
        .mapNotNull { block ->
            val lines = block.lines()
            val index = lines.indexOfFirst { timing.containsMatchIn(it) }
            if (index < 0) return@mapNotNull null
            val match = timing.find(lines[index]) ?: return@mapNotNull null
            val start = timestamp(match.groupValues[1]) ?: return@mapNotNull null
            val end = timestamp(match.groupValues[2]) ?: return@mapNotNull null
            val content = lines.drop(index + 1).joinToString("\n").replace(Regex("<[^>]*>"), "")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").trim()
            if (end <= start || content.isBlank()) null else SubtitleLine(start, end, content)
        }.take(20_000).sortedBy { it.startMs }

    private fun timestamp(value: String): Long? = try {
        val parts = value.replace(',', '.').split(':', '.')
        val p = parts.map { it.toLong() }
        if (p.size == 4) ((p[0] * 60 + p[1]) * 60 + p[2]) * 1000 + p[3]
        else (p[0] * 60 + p[1]) * 1000 + p[2]
    } catch (_: NumberFormatException) { null }

    fun active(lines: List<SubtitleLine>, positionMs: Long, delayMs: Long): List<SubtitleLine> {
        val time = positionMs - delayMs
        return lines.filter { time >= it.startMs && time < it.endMs }
    }
}
