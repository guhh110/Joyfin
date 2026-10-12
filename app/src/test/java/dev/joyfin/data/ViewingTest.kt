package dev.joyfin.data

import org.junit.Assert.*
import org.junit.Test

class ViewingTest {
    private fun episode(id: String, season: Int, index: Int, resume: Long = 0, played: Boolean = false) =
        Media(id, id, "Episode", season = season, episode = index, ticks = 100, resumeTicks = resume, played = played)

    @Test fun resumeTakesPriorityOverUnwatchedEarlierEpisodeAndSpecial() {
        val resume = episode("resume", 2, 3, resume = 40)
        assertEquals(resume, preferredEpisode(listOf(episode("special", 0, 1), episode("earlier", 1, 1), resume)))
    }
    @Test fun outOfOrderListStartsWithFirstUnwatchedNumberedEpisode() {
        val first = episode("first", 1, 2)
        assertEquals(first, preferredEpisode(listOf(episode("later", 2, 1), episode("special", 0, 1), first, episode("watched", 1, 1, played = true))))
    }
    @Test fun fullyWatchedSeriesReplaysNumberedSeasonBeforeSpecials() {
        val first = episode("first", 1, 1, played = true)
        assertEquals(first, preferredEpisode(listOf(episode("special", 0, 1, played = true), first)))
    }
    @Test fun emptyAndSpecialsOnlyLibrariesAreSupported() {
        assertNull(preferredEpisode(emptyList()))
        val special = episode("special", 0, 1)
        assertEquals(special, preferredEpisode(listOf(special)))
    }
    @Test fun seasonDoesNotInventWatchCountsWhenMetadataIsMissing() {
        val season = Media("s", "季", "Season", childCount = 12)
        assertEquals("12 集", season.seasonSummary())
        assertEquals("12 集 · 已看 4 集", season.copy(unplayedCount = 8).seasonSummary())
        assertEquals("12 集 · 已看完", season.copy(unplayedCount = 0).seasonSummary())
    }
}
