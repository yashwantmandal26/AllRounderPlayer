package com.example.ymediaplayer.util

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoThumbnailFrameTest {
    @Test fun longAndUnknownVideosUseThreeSeconds() {
        assertEquals(3_000_000L, VideoThumbnailFrame.timeMicros(60_000))
        assertEquals(3_000_000L, VideoThumbnailFrame.timeMicros(0))
    }
    @Test fun shortVideosStayInsideClip() {
        assertEquals(1_500_000L, VideoThumbnailFrame.timeMicros(3000))
        assertEquals(250_000L, VideoThumbnailFrame.timeMicros(500))
        assertEquals(0L, VideoThumbnailFrame.timeMicros(1))
    }
}
