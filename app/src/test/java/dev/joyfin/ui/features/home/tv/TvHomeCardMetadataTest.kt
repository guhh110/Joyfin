package dev.joyfin.ui.features.home.tv

import dev.joyfin.data.Media
import org.junit.Assert.*
import org.junit.Test

class TvHomeCardMetadataTest {
    private val minute = 600_000_000L

    @Test fun resumedEpisodeOnlyShowsEpisodeAndRemainingTime() {
        val episode = Media("episode", "很长的单集名称", "Episode", seriesName = "作品名",
            season = 5, episode = 140, year = 2026, rating = 8.8,
            ticks = 45 * minute, resumeTicks = 20 * minute, overview = "不显示简介")
        assertEquals("S5 E140 · 剩余 25 分钟", tvHomeCardMetadata(episode))
    }

    @Test fun movieKeepsOneCompactLineAndRoundsRemainingTimeUp() {
        val movie = Media("movie", "电影", "Movie", year = 2026, rating = 8.8,
            ticks = 120 * minute, resumeTicks = 80 * minute - 1)
        assertEquals("2026 · 8.8 分 · 剩余 41 分钟", tvHomeCardMetadata(movie))
        assertEquals("2026 · 8.8 分 · 120 分钟", tvHomeCardMetadata(movie.copy(resumeTicks = 0)))
    }

    @Test fun seriesDoesNotShowAggregateRuntimeOrInventMissingMetadata() {
        val series = Media("series", "剧集", "Series", year = 2025, rating = 9.1, ticks = 900 * minute)
        assertEquals("2025 · 9.1 分", tvHomeCardMetadata(series))
        assertEquals("", tvHomeCardMetadata(series.copy(year = 0, rating = null)))
        assertEquals("", tvHomeCardMetadata(series.copy(type = "CollectionFolder")))
    }

    @Test fun missingEpisodeNumbersAndDurationDoNotCreatePlaceholders() {
        val episode = Media("episode", "单集", "Episode")
        assertEquals("", tvHomeCardMetadata(episode))
        assertEquals("第 2 集", tvHomeCardMetadata(episode.copy(episode = 2)))
        assertEquals("S1 E2 · 30 分钟", tvHomeCardMetadata(episode.copy(
            season = 1, episode = 2, ticks = 30 * minute)))
    }

    @Test fun invalidAndCompletedProgressNeverShowsZeroOrNegativeRemainingTime() {
        val movie = Media("movie", "电影", "Movie", ticks = 30 * minute, resumeTicks = 30 * minute)
        assertEquals("30 分钟", tvHomeCardMetadata(movie))
        assertEquals("30 分钟", tvHomeCardMetadata(movie.copy(resumeTicks = 40 * minute)))
        assertEquals("30 分钟", tvHomeCardMetadata(movie.copy(resumeTicks = minute, played = true)))
        assertEquals("剩余 1 分钟", tvHomeCardMetadata(movie.copy(resumeTicks = movie.ticks - 1)))
        assertEquals("", tvHomeCardMetadata(movie.copy(ticks = 0)))
    }

    @Test fun invalidRatingsAndYearsAreOmitted() {
        val movie = Media("movie", "电影", "Movie", year = -1)
        for (rating in listOf(null, Double.NaN, Double.POSITIVE_INFINITY, -1.0, 0.0, 10.1)) {
            assertEquals("", tvHomeCardMetadata(movie.copy(rating = rating)))
        }
        assertEquals("10.0 分", tvHomeCardMetadata(movie.copy(rating = 10.0)))
    }
}
