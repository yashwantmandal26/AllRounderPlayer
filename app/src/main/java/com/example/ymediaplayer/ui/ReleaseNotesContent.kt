package com.example.ymediaplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ymediaplayer.theme.LocalAppColors

/** A small, native renderer for release notes fetched from GitHub. It never executes HTML or URLs. */
@Composable
fun ReleaseNotesContent(
    markdown: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val colors = LocalAppColors.current
    val blocks = remember(markdown) { parseReleaseNotes(markdown) }
    val bodyStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = if (compact) 13.sp else 14.sp,
        lineHeight = if (compact) 19.sp else 21.sp
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
    ) {
        blocks.forEach { block ->
            when (block.kind) {
                ReleaseNotesBlockKind.Heading -> Text(
                    text = block.content,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    style = bodyStyle.copy(
                        fontSize = when (block.headingLevel) {
                            1 -> if (compact) 17.sp else 19.sp
                            2 -> if (compact) 15.sp else 17.sp
                            else -> if (compact) 14.sp else 15.sp
                        }
                    ),
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .semantics { heading() }
                )

                ReleaseNotesBlockKind.Bullet, ReleaseNotesBlockKind.Numbered -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = (block.indent.coerceAtMost(3) * 12).dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = block.marker.orEmpty(),
                        color = colors.accentBlue,
                        fontWeight = FontWeight.SemiBold,
                        style = bodyStyle
                    )
                    Text(
                        text = block.content,
                        color = colors.textPrimary,
                        style = bodyStyle,
                        modifier = Modifier.weight(1f)
                    )
                }

                ReleaseNotesBlockKind.Code -> Text(
                    text = block.content,
                    color = colors.textSecondary,
                    style = bodyStyle.copy(fontFamily = FontFamily.Monospace)
                )

                ReleaseNotesBlockKind.Paragraph -> Text(
                    text = block.content,
                    color = colors.textPrimary,
                    style = bodyStyle
                )
            }
        }
    }
}

internal enum class ReleaseNotesBlockKind { Paragraph, Heading, Bullet, Numbered, Code }

internal data class ReleaseNotesBlock(
    val kind: ReleaseNotesBlockKind,
    val content: AnnotatedString,
    val headingLevel: Int = 0,
    val marker: String? = null,
    val indent: Int = 0
)

private val releaseHeading = Regex("^ {0,3}(#{1,6})\\s+(.+?)\\s*$")
private val releaseList = Regex("^(\\s*)([-+*]|\\d+[.)])\\s+(.+)$")
private val releaseFence = Regex("^\\s*(`{3,}|~{3,}).*$")
private val releaseSeparator = Regex("^\\s{0,3}(?:-{3,}|\\*{3,}|_{3,})\\s*$")
private const val markdownEscapable = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"

/** Parses the subset commonly used in release notes; unsupported syntax remains readable text. */
internal fun parseReleaseNotes(markdown: String): List<ReleaseNotesBlock> {
    val blocks = mutableListOf<ReleaseNotesBlock>()
    val pending = StringBuilder()
    var pendingKind = ReleaseNotesBlockKind.Paragraph
    var pendingMarker: String? = null
    var pendingIndent = 0
    var fence: String? = null
    val code = StringBuilder()

    fun flushParagraph() {
        if (pending.isNotEmpty()) {
            blocks += ReleaseNotesBlock(
                pendingKind,
                parseReleaseNotesInline(pending.toString()),
                marker = pendingMarker,
                indent = pendingIndent
            )
            pending.clear()
        }
        pendingKind = ReleaseNotesBlockKind.Paragraph
        pendingMarker = null
        pendingIndent = 0
    }

    fun flushCode() {
        if (code.isNotEmpty()) {
            blocks += ReleaseNotesBlock(
                ReleaseNotesBlockKind.Code,
                AnnotatedString(code.toString().trimEnd('\n'))
            )
            code.clear()
        }
    }

    markdown.replace("\r\n", "\n").replace('\r', '\n').lineSequence().forEach { line ->
        val activeFence = fence
        if (activeFence != null) {
            val trimmed = line.trim()
            if (trimmed.length >= activeFence.length && trimmed.all { it == activeFence.first() }) {
                flushCode()
                fence = null
            } else {
                code.append(line).append('\n')
            }
            return@forEach
        }

        val fenceMatch = releaseFence.matchEntire(line)
        val heading = releaseHeading.matchEntire(line)
        val list = releaseList.matchEntire(line)
        when {
            fenceMatch != null -> {
                flushParagraph()
                fence = fenceMatch.groupValues[1]
            }
            line.isBlank() || releaseSeparator.matches(line) -> flushParagraph()
            heading != null -> {
                flushParagraph()
                blocks += ReleaseNotesBlock(
                    ReleaseNotesBlockKind.Heading,
                    parseReleaseNotesInline(heading.groupValues[2].replace(Regex("\\s+#+$"), "")),
                    headingLevel = heading.groupValues[1].length
                )
            }
            list != null -> {
                flushParagraph()
                val marker = list.groupValues[2]
                pendingKind = if (marker.first().isDigit()) {
                    ReleaseNotesBlockKind.Numbered
                } else {
                    ReleaseNotesBlockKind.Bullet
                }
                pendingMarker = if (pendingKind == ReleaseNotesBlockKind.Bullet) "•" else marker
                pendingIndent = list.groupValues[1].sumOf { if (it == '\t') 2 else 1 } / 2
                pending.append(list.groupValues[3].trim())
            }
            else -> {
                if (pending.isNotEmpty()) pending.append(' ')
                pending.append(line.trim())
            }
        }
    }
    flushParagraph()
    flushCode()
    return blocks
}

internal fun parseReleaseNotesInline(markdown: String): AnnotatedString =
    parseInlineSegment(markdown, 0, null, 0).text

private data class InlineSegment(val text: AnnotatedString, val next: Int, val closed: Boolean)

private fun parseInlineSegment(
    source: String,
    start: Int,
    closing: String?,
    depth: Int
): InlineSegment {
    // Release notes are remote content. Bound nesting so malformed input cannot exhaust the stack.
    if (depth >= 16) return InlineSegment(AnnotatedString(source.substring(start)), source.length, false)
    var index = start
    var isClosed = false
    val text = buildAnnotatedString {
        while (index < source.length) {
            val char = source[index]
            if (char == '\\' && source.getOrNull(index + 1)?.let { it in markdownEscapable } == true) {
                append(source[index + 1])
                index += 2
                continue
            }

            if (closing != null && source.startsWith(closing, index) &&
                index > start && !source[index - 1].isWhitespace() &&
                !(closing.length == 1 && markerRun(source, index) == 2) &&
                !(closing.first() == '_' && isInsideWord(source, index, closing.length))
            ) {
                index += closing.length
                isClosed = true
                break
            }

            if (char == '`') {
                val delimiter = "`".repeat(markerRun(source, index))
                val end = source.indexOf(delimiter, index + delimiter.length)
                if (end >= 0) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                        append(source.substring(index + delimiter.length, end))
                    }
                    index = end + delimiter.length
                    continue
                }
            }

            val labelStart = when {
                char == '[' -> index
                char == '!' && source.getOrNull(index + 1) == '[' -> index + 1
                else -> -1
            }
            if (labelStart >= 0) {
                val labelEnd = matchingBracket(source, labelStart, '[', ']')
                if (labelEnd >= 0 && source.getOrNull(labelEnd + 1) == '(') {
                    val destinationEnd = matchingBracket(source, labelEnd + 1, '(', ')')
                    if (destinationEnd >= 0) {
                        // Use the human-readable label only. Do not load images or activate remote URIs.
                        append(parseInlineSegment(source.substring(labelStart + 1, labelEnd), 0, null, depth + 1).text)
                        index = destinationEnd + 1
                        continue
                    }
                }
            }

            if (char == '*' || char == '_') {
                val length = markerRun(source, index).coerceAtMost(3)
                val delimiter = char.toString().repeat(length)
                val next = source.getOrNull(index + length)
                if (next != null && !next.isWhitespace() &&
                    !(char == '_' && isInsideWord(source, index, length))
                ) {
                    val nested = parseInlineSegment(source, index + length, delimiter, depth + 1)
                    if (nested.closed) {
                        withStyle(
                            SpanStyle(
                                fontWeight = if (length >= 2) FontWeight.Bold else null,
                                fontStyle = if (length == 1 || length == 3) FontStyle.Italic else null
                            )
                        ) { append(nested.text) }
                    } else {
                        append(delimiter)
                        append(nested.text)
                    }
                    index = nested.next
                    continue
                }
            }
            append(char)
            index++
        }
    }
    return InlineSegment(text, index, isClosed)
}

private fun markerRun(source: String, index: Int): Int {
    var end = index + 1
    while (end < source.length && source[end] == source[index]) end++
    return end - index
}

private fun isInsideWord(source: String, index: Int, length: Int): Boolean =
    source.getOrNull(index - 1)?.isLetterOrDigit() == true &&
        source.getOrNull(index + length)?.isLetterOrDigit() == true

private fun matchingBracket(source: String, start: Int, open: Char, close: Char): Int {
    var depth = 0
    var index = start
    while (index < source.length) {
        if (source[index] == '\\') {
            index += 2
            continue
        }
        when (source[index]) {
            open -> depth++
            close -> {
                depth--
                if (depth == 0) return index
            }
        }
        index++
    }
    return -1
}
