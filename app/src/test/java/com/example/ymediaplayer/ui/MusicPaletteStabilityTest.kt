package com.example.ymediaplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicPaletteStabilityTest {
    @Test fun artworkSeedWithMinimumIntHashDoesNotCrash() {
        val seed = "polygenelubricants"
        assertEquals(Int.MIN_VALUE, seed.hashCode())
        assertEquals(getProceduralPalette(seed), getProceduralPalette(seed))
    }
}
