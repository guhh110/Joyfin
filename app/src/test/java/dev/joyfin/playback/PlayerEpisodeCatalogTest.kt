package dev.joyfin.playback

import dev.joyfin.data.Media
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerEpisodeCatalogTest {
    private val episode = Media("e1", "Episode", "Episode", seriesId = "show", seasonId = "s1")

    @Test fun switchingBackAndPlayingAnotherEpisodeReuseFreshSeasonCache() = runTest {
        val calls = mutableListOf<String?>()
        var seasonsCalls = 0
        val catalog = PlayerEpisodeCatalog(this, { seasonsCalls++; emptyList() }, { _, season ->
            calls += season; listOf(episode.copy(id = "$season-e1"))
        }, {}, {}, { testScheduler.currentTime })
        catalog.open(episode)
        assertTrue(catalog.state.loading)
        advanceUntilIdle()
        catalog.select("s2")
        advanceUntilIdle()
        catalog.select("s1")
        assertEquals("s1-e1", catalog.state.episodes.single().id)
        assertFalse(catalog.state.loading)
        catalog.open(episode.copy(id = "e2"))
        advanceUntilIdle()
        assertEquals(listOf("s1", "s2"), calls)
        assertEquals(1, seasonsCalls)
    }

    @Test fun staleCacheRemainsVisibleDuringRefreshAndAfterFailureThenCanRetry() = runTest {
        var fail = false
        val catalog = PlayerEpisodeCatalog(this, { emptyList() }, { _, _ ->
            delay(1000); if (fail) error("offline"); listOf(episode)
        }, {}, {}, { testScheduler.currentTime })
        catalog.open(episode)
        advanceUntilIdle()
        advanceTimeBy(61_000)
        fail = true
        catalog.select("s1")
        assertTrue(catalog.state.loading)
        assertEquals(listOf(episode), catalog.state.episodes)
        advanceUntilIdle()
        assertNotNull(catalog.state.error)
        assertEquals(listOf(episode), catalog.state.episodes)
        fail = false
        catalog.retry()
        advanceUntilIdle()
        assertNull(catalog.state.error)
    }

    @Test fun slowSeasonDiscoveryDoesNotDelayEpisodesAndLateSelectionCannotOverwriteCurrentSeason() = runTest {
        val catalog = PlayerEpisodeCatalog(this, { delay(5000); emptyList() }, { _, season ->
            if (season == "s1") withContext(NonCancellable) { delay(1000) }
            listOf(episode.copy(id = "$season-e1"))
        }, {}, {}, { testScheduler.currentTime })
        catalog.open(episode)
        runCurrent()
        catalog.select("s2")
        runCurrent()
        assertFalse(catalog.state.loading)
        assertTrue(catalog.state.seasonsLoading)
        assertEquals("s2-e1", catalog.state.episodes.single().id)
        advanceUntilIdle()
        assertEquals("s2", catalog.state.seasonId)
        assertEquals("s2-e1", catalog.state.episodes.single().id)
    }

    @Test fun emptySuccessIsDifferentFromLoadingAndFailure() = runTest {
        var fail = true
        val catalog = PlayerEpisodeCatalog(this, { emptyList() }, { _, _ ->
            if (fail) error("offline"); emptyList()
        }, {}, {}, { testScheduler.currentTime })
        catalog.open(episode)
        assertTrue(catalog.state.loading)
        advanceUntilIdle()
        assertFalse(catalog.state.loading)
        assertNotNull(catalog.state.error)
        fail = false
        catalog.retry()
        advanceUntilIdle()
        assertTrue(catalog.state.episodes.isEmpty())
        assertNull(catalog.state.error)
        assertFalse(catalog.state.loading)
    }

    @Test fun cacheIsBoundedAndNeverTransfersBetweenSeries() = runTest {
        var calls = 0
        val catalog = PlayerEpisodeCatalog(this, { emptyList() }, { show, season ->
            calls++; listOf(episode.copy(id = "$show:$season"))
        }, {}, {}, { testScheduler.currentTime })
        catalog.open(episode)
        advanceUntilIdle()
        for (i in 2..7) { catalog.select("s$i"); advanceUntilIdle() }
        catalog.select("s1")
        assertTrue(catalog.state.loading)
        assertTrue(catalog.state.episodes.isEmpty())
        advanceUntilIdle()
        assertEquals(8, calls)
        catalog.open(episode.copy(seriesId = "other"))
        assertTrue(catalog.state.episodes.isEmpty())
        advanceUntilIdle()
        assertEquals("other:s1", catalog.state.episodes.single().id)
    }

    @Test fun watchedStatusRemainsOnFreshCachedEpisodeAndSuspendingRejectsLateResult() = runTest {
        val catalog = PlayerEpisodeCatalog(this, { emptyList() }, { _, season ->
            if (season == "s2") withContext(NonCancellable) { delay(1000) }
            listOf(episode)
        }, {}, {}, { testScheduler.currentTime })
        catalog.open(episode)
        advanceUntilIdle()
        catalog.markWatched("e1")
        catalog.select("s2")
        runCurrent()
        catalog.suspendRequests()
        advanceUntilIdle()
        catalog.open(episode)
        assertTrue(catalog.state.episodes.single().played)
        assertFalse(catalog.state.loading)
    }
}
