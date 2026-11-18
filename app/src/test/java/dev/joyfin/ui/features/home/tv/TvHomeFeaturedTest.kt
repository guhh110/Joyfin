package dev.joyfin.ui.features.home.tv

import dev.joyfin.browse.TrackingUiState
import dev.joyfin.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TvHomeFeaturedTest {
    private fun movie(id: String, time: Long = 0) = Media(id, id, "Movie", ticks = 100,
        resumeTicks = 10, lastPlayedAt = time)
    private fun discovery(id: String) = movie(id).copy(resumeTicks = 0)

    @Test fun balancesThreeContinuationsAndThreeDiscoveriesInAlternatingOrder() {
        val resume = (1..8).map { movie("resume$it", (10 - it).toLong()) }
        val discover = (1..8).map { discovery("new$it") }
        val items = tvHomeFeaturedItems(resume, null, discover)
        assertEquals(listOf("resume1", "new1", "resume2", "new2", "resume3", "new3"), items.map { it.key })
    }

    @Test fun fillsMissingSlotsWithoutRepeatingWorksOrIncludingOtherVideoTypes() {
        val resume = listOf(movie("resume"))
        val discover = listOf(discovery("music").copy(type = "MusicVideo"), discovery("video").copy(type = "Video")) +
            (1..8).map { discovery("new$it") }
        assertEquals(listOf("resume", "new1", "new2", "new3", "new4", "new5"),
            tvHomeFeaturedItems(resume, null, discover).map { it.key })
        assertEquals(6, tvHomeFeaturedItems((1..8).map { movie("r$it") }, null, emptyList()).size)
        assertEquals(listOf("new1"), tvHomeFeaturedItems(emptyList(), null,
            listOf(discovery("new1"), discovery("new1"))).map { it.key })
    }

    @Test fun excludesEntireContinuationPoolFromDiscoveryBeforeApplyingTheQuota() {
        val resume = (1..5).map { movie("resume$it", (10 - it).toLong()) }
        val discover = resume.map { it.copy(resumeTicks = 0) } + (1..3).map { discovery("new$it") }
        assertEquals(listOf("resume1", "new1", "resume2", "new2", "resume3", "new3"),
            tvHomeFeaturedItems(resume, null, discover).map { it.key })
    }

    @Test fun usesLatestSeriesActivityAndPrefersExplicitResumeOnATie() {
        val series = Media("series", "剧集", "Series", overview = "完整介绍")
        val resumed = Media("e1", "第一集", "Episode", seriesId = series.id, season = 1, episode = 1,
            ticks = 100, resumeTicks = 25, lastPlayedAt = 10)
        val next = resumed.copy(id = "e2", episode = 2, resumeTicks = 0)
        fun tracked(time: Long) = TrackingUiState(entries = listOf(TrackingEntry(series,
            TrackingProgress(TrackingStatus.WATCHING, next, lastPlayedAt = time))))
        val newer = tvHomeFeaturedItems(listOf(resumed), tracked(11), listOf(series)).single()
        assertEquals("e2", newer.playback?.id)
        assertEquals(TvHomeFeaturedReason.NEXT_EPISODE, newer.reason)
        assertSame(series, newer.details)
        assertEquals("完整介绍", newer.media.overview)
        assertEquals("e1", tvHomeFeaturedItems(listOf(resumed), tracked(10), emptyList()).single().playback?.id)
        assertEquals("e1", tvHomeFeaturedItems(listOf(resumed), tracked(9), emptyList()).single().playback?.id)
    }

    @Test fun excludesUnstartedCaughtUpUnavailableAndFinishedContinuationTargets() {
        val statuses = TrackingStatus.entries.map { status ->
            val series = Media(status.name, status.name, "Series")
            TrackingEntry(series, TrackingProgress(status,
                if (status == TrackingStatus.WATCHING || status == TrackingStatus.WAITING)
                    Media("e-${status.name}", "单集", "Episode") else null))
        }
        val invalid = listOf(movie("played").copy(played = true), movie("finished").copy(resumeTicks = 100),
            movie("virtual").copy(virtual = true), movie("not-started").copy(resumeTicks = 0))
        assertEquals(listOf(TrackingStatus.WATCHING.name),
            tvHomeFeaturedItems(invalid, TrackingUiState(entries = statuses), emptyList()).map { it.key })
    }

    @Test fun discoveryPreservesMetadataAndSeriesOpenDetailsWhileMoviesPlay() {
        val series = Media("series", "剧集", "Series", overview = "剧集介绍", rating = 8.8, backdropTag = "image")
        val items = tvHomeFeaturedItems(emptyList(), null, listOf(series, discovery("movie"),
            discovery("played").copy(played = true), discovery("virtual").copy(virtual = true)))
        assertSame(series, items.first().media)
        assertNull(items.first().playback)
        assertEquals("查看剧集", items.first().playLabel)
        assertEquals("movie", items.last().playback?.id)
        assertEquals("播放", items.last().playLabel)
        assertEquals(2, items.size)
    }

    @Test fun selectionSurvivesLateSourcesReorderingAndMetadataRefresh() {
        val original = tvHomeFeaturedItems(listOf(movie("r1"), movie("r2")), null, emptyList())
        val state = TvHomeFeaturedState("r2", 1)
        state.update(emptyList())
        assertEquals("r2", state.selectedKey)
        state.update(original)
        state.select("r2")
        state.update(tvHomeFeaturedItems(listOf(movie("r2", 10), movie("r1")), null,
            listOf(discovery("new"))))
        assertEquals("r2", state.current?.key)
        assertEquals(0, state.selectedIndex)
        state.select("new")
        state.update(original)
        assertEquals("r2", state.current?.key)
    }

    @Test fun retainsAnEligibleSelectionEvenWhenLateSourcesPushItPastTheQuota() {
        val resume = (1..6).map { movie("r$it", (10 - it).toLong()) }
        val discovery = (1..6).map { discovery("d$it") }
        val selectedResume = tvHomeFeaturedItems(resume, null, discovery, "r6")
        assertEquals(6, selectedResume.size)
        assertEquals(3, selectedResume.count { it.reason == TvHomeFeaturedReason.RESUME })
        assertTrue(selectedResume.any { it.key == "r6" })
        val selectedDiscovery = tvHomeFeaturedItems(resume, null, discovery, "d6")
        assertTrue(selectedDiscovery.any { it.key == "d6" })
        assertEquals(6, selectedDiscovery.map { it.key }.distinct().size)
    }

    @Test fun discoversPartiallyWatchedMoviesOutsideTheContinuationPoolButNotCompletedOnes() {
        val items = tvHomeFeaturedItems(emptyList(), null,
            listOf(movie("partial"), movie("complete").copy(resumeTicks = 100)))
        assertEquals(listOf("partial"), items.map { it.key })
    }

    @Test fun rotatesAfterTenSecondsAndInputStopsThePendingRotation() = runTest {
        val state = TvHomeFeaturedState()
        state.update(tvHomeFeaturedItems(emptyList(), null, listOf(discovery("a"), discovery("b"))))
        val rotation = launch { state.advanceAfterIdle("a") }
        advanceTimeBy(9_999)
        assertEquals("a", state.current?.key)
        advanceTimeBy(1)
        runCurrent()
        assertEquals("b", state.current?.key)
        rotation.join()
        launch { state.advanceAfterIdle("b") }
        advanceTimeBy(5_000)
        state.onInput()
        advanceUntilIdle()
        assertEquals("b", state.current?.key)
    }

    @Test fun cancelledIdleTimerAndChangedSelectionCannotAdvanceTheWrongWork() = runTest {
        val state = TvHomeFeaturedState()
        state.update(tvHomeFeaturedItems(emptyList(), null, listOf(discovery("a"), discovery("b"))))
        val backgrounded = launch { state.advanceAfterIdle("a") }
        advanceTimeBy(5_000)
        backgrounded.cancelAndJoin()
        advanceTimeBy(10_000)
        assertEquals("a", state.current?.key)
        launch { state.advanceAfterIdle("a") }
        runCurrent()
        state.select("b")
        advanceUntilIdle()
        assertEquals("b", state.current?.key)
    }
}
