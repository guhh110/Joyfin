package dev.joyfin.playback

import dev.joyfin.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NextEpisodePreparationTest {
    private val current = Media("s1e24", "Finale", "Episode", seriesId = "show", season = 1, episode = 24)
    private val next = Media("s2e1", "Return", "Episode", seriesId = "show", season = 2, episode = 1)

    @Test fun discoveryWaitsForReadyAndRepeatedLoadsOfTheSameVersionDoNotRestartIt() = runTest {
        var finds = 0
        val controller = NextEpisodePreparation(this, { finds++; next }, { error("Too early") }, {}, {}, { testScheduler.currentTime })
        controller.open(current, "primary")
        advanceUntilIdle()
        assertEquals(0, finds)
        controller.start(); controller.start()
        advanceUntilIdle()
        assertEquals(next, controller.state.next)
        controller.open(current.copy(name = "New metadata"), "primary"); controller.start()
        advanceUntilIdle()
        assertEquals(1, finds)
    }

    @Test fun fullMetadataIsFetchedOnceNearTheEndWhilePlayingAndExpiresWithoutCachingPlaybackChoices() = runTest {
        var loads = 0
        val full = next.copy(sources = listOf(MediaSource(id = "next-version")))
        val controller = NextEpisodePreparation(this, { next }, { loads++; full }, {}, {}, { testScheduler.currentTime })
        controller.open(current, "primary"); controller.start(); advanceUntilIdle()
        controller.tick(1, 1_440_000, true)
        controller.tick(1_400_000, 1_440_000, false)
        advanceUntilIdle()
        assertEquals(0, loads)
        controller.tick(1_320_000, 1_440_000, true)
        controller.tick(1_321_000, 1_440_000, true)
        advanceUntilIdle()
        assertEquals(1, loads)
        assertTrue(controller.state.prepared)
        assertEquals(full, controller.preparedFor(next))
        assertNull(controller.preparedFor(current))
        advanceTimeBy(120_001)
        assertNull(controller.preparedFor(next))
    }

    @Test fun changingVersionRejectsLateDiscoveryAndDoesNotPublishItsAuthenticationError() = runTest {
        var finds = 0
        var expired = 0
        val controller = NextEpisodePreparation(this, {
            if (++finds == 1) withContext(NonCancellable) { delay(1000); throw ApiException(401, "old") }
            next
        }, { next }, {}, { expired++ }, { testScheduler.currentTime })
        controller.open(current, "old"); controller.start(); runCurrent()
        controller.open(current, "new"); controller.start(); advanceUntilIdle()
        assertEquals(next, controller.state.next)
        assertEquals(0, expired)
        assertNull(controller.state.error)
    }

    @Test fun switchingEpisodeRejectsLateFullMetadataAndClearsTheOldCache() = runTest {
        val controller = NextEpisodePreparation(this, { next }, {
            withContext(NonCancellable) { delay(1000) }; next
        }, {}, {}, { testScheduler.currentTime })
        controller.open(current, "primary"); controller.start(); runCurrent()
        controller.tick(900, 1000, true); runCurrent()
        controller.open(next, "other-version")
        advanceUntilIdle()
        assertNull(controller.state.next)
        assertFalse(controller.state.prepared)
        assertNull(controller.preparedFor(next))
    }

    @Test fun preparationFailureKeepsNextEpisodeUsableAndRetryNeverLoopsAutomatically() = runTest {
        var loads = 0
        val controller = NextEpisodePreparation(this, { next }, {
            if (++loads == 1) error("Offline") else next
        }, {}, {}, { testScheduler.currentTime })
        controller.open(current, "primary"); controller.start(); advanceUntilIdle()
        controller.tick(900, 1000, true); advanceUntilIdle()
        assertEquals(next, controller.state.next)
        assertNotNull(controller.state.error)
        repeat(3) { controller.tick(950, 1000, true); advanceUntilIdle() }
        assertEquals(1, loads)
        controller.retry(); controller.retry(); advanceUntilIdle()
        assertEquals(2, loads)
        assertNull(controller.state.error)
        assertEquals(next, controller.preparedFor(next))
    }

    @Test fun discoveryFailureCanRetryButNoNextEpisodeDoesNotTriggerAnyItemRequest() = runTest {
        var fails = true
        val controller = NextEpisodePreparation(this, { if (fails) error("Offline") else null },
            { error("There is no next episode") }, {}, {}, { testScheduler.currentTime })
        controller.open(current, "primary"); controller.start(); advanceUntilIdle()
        assertNotNull(controller.state.error)
        fails = false
        controller.retry(); advanceUntilIdle()
        controller.tick(990, 1000, true); advanceUntilIdle()
        assertNull(controller.state.next)
        assertNull(controller.state.error)
    }

    @Test fun awaitDiscoveryPreservesCrossSeasonTargetAndClearCancelsPendingResults() = runTest {
        val controller = NextEpisodePreparation(this, { delay(1000); next }, { next }, {}, {}, { testScheduler.currentTime })
        controller.open(current, "primary"); controller.start()
        val target = async { controller.awaitNext() }
        advanceUntilIdle()
        assertEquals(next, target.await())
        controller.clear()
        assertNull(controller.state.next)
        assertNull(controller.preparedFor(next))
        assertNull(controller.awaitNext())
    }

    @Test fun unexpectedSeriesOrItemIdentityIsNeverCachedOrUsedForContinuation() = runTest {
        val controller = NextEpisodePreparation(this, { next }, { next.copy(seriesId = "foreign") }, {}, {}, { testScheduler.currentTime })
        controller.open(current, "primary"); controller.start(); advanceUntilIdle()
        controller.tick(900, 1000, true); advanceUntilIdle()
        assertNotNull(controller.state.error)
        assertNull(controller.preparedFor(next))
        assertEquals(next, controller.state.next)
        val foreign = NextEpisodePreparation(this, { next.copy(seriesId = "foreign") }, { next }, {}, {}, { 0 })
        foreign.open(current, "primary"); foreign.start(); advanceUntilIdle()
        assertNull(foreign.state.next)
    }

    @Test fun selectingAnotherVersionInvalidatesPendingWorkBeforeTheNewStreamIsNegotiated() = runTest {
        val controller = NextEpisodePreparation(this, {
            withContext(NonCancellable) { delay(1000) }; next
        }, { next }, {}, {}, { testScheduler.currentTime })
        controller.open(current, "primary"); controller.start(); runCurrent()
        controller.retain(current.id, "alternate")
        advanceUntilIdle()
        assertNull(controller.state.next)
        assertFalse(controller.state.loading)
        controller.open(current, "primary"); controller.start(); advanceUntilIdle()
        controller.retain(current.id, "primary")
        assertEquals(next, controller.state.next)
    }

    @Test fun currentAuthenticationFailureExpiresSessionAndMoviesDoNotStartDiscovery() = runTest {
        var expired = 0
        var finds = 0
        val controller = NextEpisodePreparation(this, { finds++; throw ApiException(401, "Expired") },
            { next }, {}, { expired++ }, { 0 })
        controller.open(current.copy(type = "Movie"), "primary"); controller.start(); advanceUntilIdle()
        assertEquals(0, finds)
        controller.open(current, "primary"); controller.start(); advanceUntilIdle()
        assertEquals(1, expired)
    }

    @Test fun versionHintUsesUniqueNamesAndContainerInsteadOfCopyingAnOldSourceIdOrGuessing() {
        val hint = EpisodeVersionHint.from(MediaSource(id = "old-id", name = "  UHD ", container = "mkv"))!!
        val matching = MediaSource(id = "new-id", name = "uhd", container = "MKV")
        assertEquals("new-id", hint.match(listOf(MediaSource(id = "default", name = "HD"), matching)))
        assertNull(hint.match(listOf(matching, matching.copy(id = "duplicate"))))
        assertNull(hint.match(listOf(matching.copy(container = "mp4"))))
        assertNull(hint.match(listOf(MediaSource(id = "old-id", name = "Different"))))
        assertNull(EpisodeVersionHint.from(MediaSource(id = "unnamed")))
    }
}
