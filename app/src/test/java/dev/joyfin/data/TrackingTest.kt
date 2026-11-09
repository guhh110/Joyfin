package dev.joyfin.data

import dev.joyfin.browse.TrackingUiState
import org.junit.Assert.*
import org.junit.Test

class TrackingTest {
    private fun episode(n: Int, season: Int = 1, played: Boolean = false, resume: Long = 0, date: Long? = null) =
        Media("$season-$n", "E$n", "Episode", season = season, episode = n, played = played,
            ticks = 100, resumeTicks = resume, lastPlayedAt = date)

    @Test fun newSeriesStartsAtFirstNumberedEpisodeAndCountsUnwatched() {
        val progress = trackingProgress(listOf(episode(1, 0), episode(2), episode(1)))
        assertEquals(TrackingStatus.WAITING, progress.status)
        assertEquals("1-1", progress.target!!.id)
        assertEquals(2, progress.unplayed)
    }

    @Test fun latestActivityWinsOverOldResumePositionsAndFollowsAcrossSeasons() {
        val progress = trackingProgress(listOf(episode(1, resume = 20, date = 5),
            episode(9, played = true, date = 10), episode(1, season = 2)))
        assertEquals("2-1", progress.target!!.id)
        assertEquals(TrackingStatus.WATCHING, progress.status)
        val resumed = trackingProgress(listOf(episode(1, resume = 20, date = 11), episode(9, played = true, date = 10)))
        assertEquals("1-1", resumed.target!!.id)
    }

    @Test fun latestEpisodeCanBeCaughtUpWithOlderGapsAndNewEpisodeReactivatesIt() {
        val all = listOf(episode(1), episode(8, played = true))
        val caught = trackingProgress(all)
        assertEquals(TrackingStatus.CAUGHT_UP, caught.status)
        assertNull(caught.target)
        assertEquals(1, caught.unplayed)
        val updated = trackingProgress(all + episode(9))
        assertEquals(TrackingStatus.WATCHING, updated.status)
        assertEquals("1-9", updated.target!!.id)
    }

    @Test fun endPositionIsFinishedAndFullyWatchedNeverOffersReplay() {
        val progress = trackingProgress(listOf(episode(1, played = true), episode(2, resume = 100)))
        assertEquals(TrackingStatus.CAUGHT_UP, progress.status)
        assertNull(progress.target)
        assertEquals(0, progress.unplayed)
    }

    @Test fun unavailableSpecialsOnlyAndVirtualEpisodesAreHandled() {
        assertEquals(TrackingStatus.UNAVAILABLE, trackingProgress(emptyList()).status)
        assertEquals(TrackingStatus.UNAVAILABLE, trackingProgress(listOf(episode(1).copy(virtual = true))).status)
        assertEquals("0-1", trackingProgress(listOf(episode(1, 0))).target!!.id)
        val all = listOf(episode(1, played = true), episode(2).copy(virtual = true), episode(1, 0))
        assertEquals(TrackingStatus.CAUGHT_UP, trackingProgress(all).status)
    }

    @Test fun carouselPrefersPlayableEntriesThenCaughtUpAndLimitsToSix() {
        fun entry(id: String, status: TrackingStatus, date: Long?) = TrackingEntry(
            Media(id, id, "Series"), TrackingProgress(status, lastPlayedAt = date))
        val all = (1..6).map { entry("caught$it", TrackingStatus.CAUGHT_UP, 100) } +
            entry("watching", TrackingStatus.WATCHING, 1) + entry("new", TrackingStatus.WAITING, null) +
            entry("unavailable", TrackingStatus.UNAVAILABLE, 200)
        val carousel = TrackingUiState(entries = all, loaded = true).carousel
        assertEquals(6, carousel.size)
        assertEquals(listOf("watching", "new"), carousel.take(2).map { it.series.id })
        assertEquals(6, carousel.map { it.series.id }.distinct().size)
    }
}
