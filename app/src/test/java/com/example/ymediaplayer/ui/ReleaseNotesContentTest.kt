package com.example.ymediaplayer.ui

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesContentTest {
    @Test
    fun parsesHeadingsParagraphsAndWindowsLineEndings() {
        val blocks = parseReleaseNotes("## What's new ##\r\n\r\nPlayback is smoother\r\nand controls are clearer.\r\n\r\n### Audio")

        assertEquals(3, blocks.size)
        assertEquals(ReleaseNotesBlockKind.Heading, blocks[0].kind)
        assertEquals(2, blocks[0].headingLevel)
        assertEquals("What's new", blocks[0].content.text)
        assertEquals("Playback is smoother and controls are clearer.", blocks[1].content.text)
        assertEquals(3, blocks[2].headingLevel)
    }

    @Test
    fun preservesListNumbersAndContinuationTextForHangingIndent() {
        val blocks = parseReleaseNotes("- **Picture:** compare with the original\n  by holding the preview button.\n  + *Sharper* details\n7. Resume playback\n\t8) Save audio settings")

        assertEquals(4, blocks.size)
        assertEquals(ReleaseNotesBlockKind.Bullet, blocks[0].kind)
        assertEquals("•", blocks[0].marker)
        assertEquals("Picture: compare with the original by holding the preview button.", blocks[0].content.text)
        assertEquals(1, blocks[1].indent)
        assertEquals(ReleaseNotesBlockKind.Numbered, blocks[2].kind)
        assertEquals("7.", blocks[2].marker)
        assertEquals("8)", blocks[3].marker)
        assertEquals(1, blocks[3].indent)
        assertEquals(FontWeight.Bold, blocks[0].content.spanStyles.single().item.fontWeight)
    }

    @Test
    fun nestedBoldAndItalicApplyToCorrectWordsWithoutShowingMarkers() {
        val text = parseReleaseNotesInline("**Sharp *details*** with _clear audio_")

        assertEquals("Sharp details with clear audio", text.text)
        val bold = text.spanStyles.single { it.item.fontWeight == FontWeight.Bold }
        assertEquals(0, bold.start)
        assertEquals(13, bold.end)
        val italic = text.spanStyles.filter { it.item.fontStyle == FontStyle.Italic }
        assertEquals(listOf("details", "clear audio"), italic.map { text.text.substring(it.start, it.end) })
    }

    @Test
    fun supportsTripleEmphasisAndBoldNestedInsideItalic() {
        val combined = parseReleaseNotesInline("***New features***")
        assertEquals("New features", combined.text)
        assertEquals(FontWeight.Bold, combined.spanStyles.single().item.fontWeight)
        assertEquals(FontStyle.Italic, combined.spanStyles.single().item.fontStyle)

        val nested = parseReleaseNotesInline("*Clear **dialogue***")
        assertEquals("Clear dialogue", nested.text)
        assertEquals("dialogue", nested.spanStyles.single { it.item.fontWeight == FontWeight.Bold }.let {
            nested.text.substring(it.start, it.end)
        })
        assertEquals(0, nested.spanStyles.single { it.item.fontStyle == FontStyle.Italic }.start)
    }

    @Test
    fun preservesFilenamesAndMalformedEmphasisAndSupportsEscapedMarkers() {
        assertEquals("my_video_file.mp4", parseReleaseNotesInline("my_video_file.mp4").text)
        assertEquals("**unfinished", parseReleaseNotesInline("**unfinished").text)
        val escaped = parseReleaseNotesInline("\\*literal stars\\* and C:\\Media")
        assertEquals("*literal stars* and C:\\Media", escaped.text)
        assertTrue(escaped.spanStyles.isEmpty())
    }

    @Test
    fun rendersLinkAndImageLabelsWithoutActivatingOrFetchingDestinations() {
        val text = parseReleaseNotesInline(
            "[**Release notes**](https://example.com/releases/v1_(stable)) and " +
                "[unsafe](javascript:alert(1)) ![Preview](https://example.com/picture.png)"
        )

        assertEquals("Release notes and unsafe Preview", text.text)
        assertEquals(FontWeight.Bold, text.spanStyles.single().item.fontWeight)
        assertTrue(text.getStringAnnotations(0, text.length).isEmpty())
        assertEquals("[broken](destination", parseReleaseNotesInline("[broken](destination").text)
    }

    @Test
    fun inlineAndFencedCodeKeepFormattingCharactersLiteral() {
        val inline = parseReleaseNotesInline("Use `**Original**` to compare")
        assertEquals("Use **Original** to compare", inline.text)
        assertEquals(FontFamily.Monospace, inline.spanStyles.single().item.fontFamily)

        val blocks = parseReleaseNotes("Before\n\n```text\n**literal**\nsecond line\n```\n\nAfter")
        assertEquals(3, blocks.size)
        assertEquals(ReleaseNotesBlockKind.Code, blocks[1].kind)
        assertEquals("**literal**\nsecond line", blocks[1].content.text)
        assertTrue(blocks[1].content.spanStyles.isEmpty())
    }

    @Test
    fun emptyNotesAndSeparatorsDoNotProduceEmptyRows() {
        assertTrue(parseReleaseNotes(" \r\n\n").isEmpty())
        val blocks = parseReleaseNotes("First\n\n---\n\nSecond\n\n***")
        assertEquals(listOf("First", "Second"), blocks.map { it.content.text })
    }

    @Test
    fun unterminatedCodeFenceStillShowsItsContent() {
        val block = parseReleaseNotes("```\n**Still readable**").single()
        assertEquals(ReleaseNotesBlockKind.Code, block.kind)
        assertEquals("**Still readable**", block.content.text)
    }
}
