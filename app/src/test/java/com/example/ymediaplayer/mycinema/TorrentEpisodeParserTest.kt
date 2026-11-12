package com.example.ymediaplayer.mycinema

import com.example.ymediaplayer.mycinema.data.TorrentEpisodeParser
import com.example.ymediaplayer.mycinema.data.TorrentSource
import org.junit.Assert.*
import org.junit.Test

class TorrentEpisodeParserTest {

    private fun dummySource(
        title: String,
        parsedSeason: Int? = null,
        parsedEpisode: Int? = null,
        parsedEpisodeEnd: Int? = null,
        parsedSeasons: List<Int> = emptyList(),
        isSeasonPack: Boolean = false,
        isCompleteSeries: Boolean = false
    ) = TorrentSource(
        title = title,
        quality = "1080p",
        size = "1.5 GB",
        magnet = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567",
        seeds = 100,
        parsedSeason = parsedSeason,
        parsedEpisode = parsedEpisode,
        parsedEpisodeEnd = parsedEpisodeEnd,
        parsedSeasons = parsedSeasons,
        isSeasonPack = isSeasonPack,
        isCompleteSeries = isCompleteSeries
    )

    @Test
    fun parsesExactSeasonAndEpisodeTags() {
        val info = TorrentEpisodeParser.parse("Dexter.Resurrection.S02E01.1080p.WEB-DL.DDP5.1.H264")
        assertEquals(2, info.primarySeason)
        assertEquals(1, info.primaryEpisode)
        assertNull(info.episodeEnd)
        assertEquals(listOf(2), info.seasons)
        assertFalse(info.isPack)
        assertFalse(info.isCompleteSeries)
    }

    @Test
    fun detectsSeasonOnlyPackAndExcludesWrongSeason() {
        val title = "Dexter: Resurrection (2025) S01 (1080p AMZN WEB-DL x265 10bit EAC3 5.1 Silence) [QxR]"
        val info = TorrentEpisodeParser.parse(title)
        assertEquals(1, info.primarySeason)
        assertNull(info.primaryEpisode)
        assertEquals(listOf(1), info.seasons)
        assertTrue(info.isPack)
        assertFalse(info.isCompleteSeries)

        val src = dummySource(
            title = title,
            parsedSeason = info.primarySeason,
            parsedSeasons = info.seasons,
            isSeasonPack = info.isPack
        )
        assertTrue(TorrentEpisodeParser.matchesSeason(src, 1))
        assertFalse(TorrentEpisodeParser.matchesSeason(src, 2))
        assertFalse(TorrentEpisodeParser.matchesRequestedEpisode(src, targetSeason = 2, targetEpisode = 1))
        assertTrue(TorrentEpisodeParser.matchesRequestedEpisode(src, targetSeason = 1, targetEpisode = 4))
    }

    @Test
    fun parsesMultiSeasonPackCorrectly() {
        val title = "Dexter Resurrection S01-S02 Complete 1080p WEB-DL"
        val info = TorrentEpisodeParser.parse(title)
        assertEquals(listOf(1, 2), info.seasons)
        assertNull(info.primaryEpisode)
        assertTrue(info.isPack)

        val src = dummySource(
            title = title,
            parsedSeason = info.primarySeason,
            parsedSeasons = info.seasons,
            isSeasonPack = info.isPack
        )
        assertTrue(TorrentEpisodeParser.matchesSeason(src, 1))
        assertTrue(TorrentEpisodeParser.matchesSeason(src, 2))
        assertFalse(TorrentEpisodeParser.matchesSeason(src, 3))
        assertTrue(TorrentEpisodeParser.matchesRequestedEpisode(src, targetSeason = 2, targetEpisode = 1))
    }

    @Test
    fun season1FilterDoesNotMatchSeason10() {
        val title = "Show.Name.S10E01.1080p.WEB-DL.mkv"
        val info = TorrentEpisodeParser.parse(title)
        assertEquals(listOf(10), info.seasons)
        assertEquals(10, info.primarySeason)
        assertEquals(1, info.primaryEpisode)

        val src = dummySource(title = title, parsedSeason = 10, parsedEpisode = 1, parsedSeasons = listOf(10))
        assertFalse(TorrentEpisodeParser.matchesSeason(src, 1))
        assertTrue(TorrentEpisodeParser.matchesSeason(src, 10))
        assertFalse(TorrentEpisodeParser.matchesRequestedEpisode(src, targetSeason = 1, targetEpisode = 1))
    }

    @Test
    fun seasonWordWithEpisodeWordIsNotMisclassifiedAsPack() {
        val title = "Dexter Resurrection Season 2 Episode 1 1080p Hindi"
        val info = TorrentEpisodeParser.parse(title)
        assertEquals(2, info.primarySeason)
        assertEquals(1, info.primaryEpisode)
        assertEquals(listOf(2), info.seasons)
        assertFalse(info.isPack)
    }

    @Test
    fun episodeRangePackMatchesIncludedEpisodesOnly() {
        val title = "Dexter.Resurrection.S02E01-E05.1080p.WEB-DL"
        val info = TorrentEpisodeParser.parse(title)
        assertEquals(2, info.primarySeason)
        assertEquals(1, info.primaryEpisode)
        assertEquals(5, info.episodeEnd)
        assertTrue(info.isPack)

        val src = dummySource(
            title = title,
            parsedSeason = info.primarySeason,
            parsedEpisode = info.primaryEpisode,
            parsedEpisodeEnd = info.episodeEnd,
            parsedSeasons = info.seasons,
            isSeasonPack = info.isPack
        )
        assertTrue(TorrentEpisodeParser.matchesRequestedEpisode(src, targetSeason = 2, targetEpisode = 1))
        assertTrue(TorrentEpisodeParser.matchesRequestedEpisode(src, targetSeason = 2, targetEpisode = 5))
        assertFalse(TorrentEpisodeParser.matchesRequestedEpisode(src, targetSeason = 2, targetEpisode = 6))
        assertFalse(TorrentEpisodeParser.matchesRequestedEpisode(src, targetSeason = 1, targetEpisode = 1))
    }

    @Test
    fun completeSeriesMatchesAnySeasonOrEpisode() {
        val title = "Dexter Resurrection Complete Series 1080p BluRay"
        val info = TorrentEpisodeParser.parse(title)
        assertTrue(info.isCompleteSeries)
        assertTrue(info.isPack)

        val src = dummySource(title = title, isSeasonPack = true, isCompleteSeries = true)
        assertTrue(TorrentEpisodeParser.matchesSeason(src, 2))
        assertTrue(TorrentEpisodeParser.matchesRequestedEpisode(src, targetSeason = 2, targetEpisode = 1))
    }

    @Test
    fun untaggedBehaviorDiffersByProviderFlag() {
        val untagged = dummySource(title = "Dexter Resurrection (2025) 1080p WEB-DL x265")
        assertFalse(TorrentEpisodeParser.matchesRequestedEpisode(untagged, targetSeason = 2, targetEpisode = 1, allowUntagged = false))
        assertTrue(TorrentEpisodeParser.matchesRequestedEpisode(untagged, targetSeason = 2, targetEpisode = 1, allowUntagged = true))
    }

    @Test
    fun parseFileEpisodeExtractsFromPathAndLeadingDigits() {
        assertEquals(Pair(2, 1), TorrentEpisodeParser.parseFileEpisode("Season 02/Dexter.S02E01.mkv"))
        assertEquals(Pair(2, 3), TorrentEpisodeParser.parseFileEpisode("Dexter S02/03 - Episode Three.mkv"))
        assertEquals(Pair(1, 5), TorrentEpisodeParser.parseFileEpisode("1x05.mp4"))
    }
}
