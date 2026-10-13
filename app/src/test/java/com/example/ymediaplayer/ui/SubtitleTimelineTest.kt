package com.example.ymediaplayer.ui

import org.junit.Assert.*
import org.junit.Test

class SubtitleTimelineTest {
    @Test fun parsesSrtAndKeepsMultilineUnicodeText() {
        val lines = SubtitleTimeline.parse("\uFEFF1\r\n00:00:01,250 --> 00:00:03,000\r\n<b>Hello</b> &amp; नमस्ते\r\nSecond line\r\n")
        assertEquals(listOf(SubtitleLine(1250, 3000, "Hello & नमस्ते\nSecond line")), lines)
    }

    @Test fun parsesVttWithCueSettingsAndSkipsHeader() {
        val lines = SubtitleTimeline.parse("WEBVTT\n\nintro\n00:01.000 --> 00:02.500 align:start\nFirst\n\n00:03.000 --> 00:04.000\nSecond")
        assertEquals(2, lines.size)
        assertEquals(2500L, lines.first().endMs)
    }

    @Test fun positiveAndNegativeDelayUseTheSameSearchTimeline() {
        val lines = listOf(SubtitleLine(1000, 2000, "Dialogue"))
        assertTrue(SubtitleTimeline.active(lines, 1000, 500).isEmpty())
        assertEquals(lines, SubtitleTimeline.active(lines, 1500, 500))
        assertEquals(lines, SubtitleTimeline.active(lines, 500, -500))
        assertTrue(SubtitleTimeline.active(lines, 2000, 0).isEmpty())
    }

    @Test fun preservesOverlappingCuesAndRejectsInvalidIntervals() {
        val lines = SubtitleTimeline.parse("1\n00:00:01,000 --> 00:00:04,000\nA\n\n2\n00:00:02,000 --> 00:00:03,000\nB\n\n3\n00:00:04,000 --> 00:00:02,000\nInvalid")
        assertEquals(2, lines.size)
        assertEquals(2, SubtitleTimeline.active(lines, 2500, 0).size)
    }
}
