package dev.joyfin.browse

import dev.joyfin.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrackingControllerTest {
    private val user = Session("http://server", "token", "u", "User")
    private fun series(id: String) = Media(id, id, "Series", favorite = true)
    private fun episode(id: String) = Media("e$id", "Episode", "Episode", seriesId = id, season = 1, episode = 1)

    @Test fun allFavoritePagesAreReadAndDuplicatesAndOtherKindsExcluded() = runTest {
        val starts = mutableListOf<Int>()
        val controller = TrackingController(this, { user }, { start ->
            starts += start
            if (start == 0) MediaPage(listOf(series("a"), series("b")), 5)
            else MediaPage(listOf(series("b"), series("c"), Media("movie", "Movie", "Movie")), 5)
        }, { listOf(episode(it)) }, { _, _ -> }, { fail("Unexpected authentication failure") })
        controller.refresh(all = true)
        advanceUntilIdle()
        assertEquals(listOf(0, 2), starts)
        assertEquals(listOf("a", "b", "c"), controller.state.value.entries.map { it.series.id })
        assertEquals(3, controller.state.value.entries.count { it.progress != null })
        assertFalse(controller.state.value.loading)
    }

    @Test fun concurrencyIsBoundedAndAStreamingResultDoesNotWaitForSlowSeries() = runTest {
        var active = 0
        var peak = 0
        val gate = CompletableDeferred<Unit>()
        val controller = TrackingController(this, { user }, { MediaPage((1..10).map { series("$it") }, 10) }, {
            active++
            peak = maxOf(peak, active)
            if (it == "1") gate.await() else delay(10)
            active--
            listOf(episode(it))
        }, { _, _ -> }, {})
        controller.refresh(all = true)
        runCurrent()
        advanceTimeBy(11)
        runCurrent()
        assertTrue(controller.state.value.loaded)
        assertTrue(controller.state.value.entries.any { it.progress != null })
        assertTrue(controller.state.value.loading)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(4, peak)
        assertEquals(10, controller.state.value.entries.count { it.progress != null })
    }

    @Test fun homeLoadsCandidatesAndTrackingContinuesTheSharedCache() = runTest {
        var lists = 0
        val controller = TrackingController(this, { user }, {
            lists++
            MediaPage((1..20).map { series(it.toString().padStart(2, '0')) }, 20)
        }, { listOf(episode(it)) }, { _, _ -> }, {}, { testScheduler.currentTime })
        controller.refresh()
        advanceUntilIdle()
        assertEquals(8, controller.state.value.entries.count { it.progress != null })
        assertEquals(6, controller.state.value.carousel.size)
        controller.refresh(all = true)
        advanceUntilIdle()
        assertEquals(1, lists)
        assertEquals(20, controller.state.value.entries.count { it.progress != null })
        controller.refresh(all = true)
        advanceUntilIdle()
        assertEquals(1, lists)
        controller.refresh(all = true, force = true)
        advanceUntilIdle()
        assertEquals(2, lists)
    }

    @Test fun oneSeriesFailureDoesNotBecomeCaughtUpOrBlockOtherEntriesAndCanRetry() = runTest {
        var fail = true
        val controller = TrackingController(this, { user }, { MediaPage(listOf(series("a"), series("b")), 2) }, {
            if (it == "a" && fail) error("offline")
            listOf(episode(it))
        }, { _, _ -> }, {})
        controller.refresh(all = true)
        advanceUntilIdle()
        assertNull(controller.state.value.entries.first().progress)
        assertEquals("offline", controller.state.value.entries.first().error)
        assertEquals(TrackingStatus.WAITING, controller.state.value.entries.last().progress!!.status)
        fail = false
        controller.retry("a")
        advanceUntilIdle()
        assertNull(controller.state.value.entries.first().error)
        assertNotNull(controller.state.value.entries.first().progress)
    }

    @Test fun enteringTrackingRefreshesCachedEntriesThatHomeDidNotRevalidate() = runTest {
        val reads = mutableMapOf<String, Int>()
        val controller = TrackingController(this, { user }, {
            MediaPage((1..20).map { series(it.toString().padStart(2, '0')) }, 20)
        }, { id -> reads[id] = (reads[id] ?: 0) + 1; listOf(episode(id)) }, { _, _ -> }, {}, { testScheduler.currentTime })
        controller.refresh(all = true)
        advanceUntilIdle()
        assertEquals(1, reads["20"])
        controller.refresh(force = true)
        advanceUntilIdle()
        assertEquals(1, reads["20"])
        controller.refresh(all = true)
        advanceUntilIdle()
        assertEquals(2, reads["20"])
    }

    @Test fun refreshFailureKeepsLastContentAndIsNotAnEmptyResult() = runTest {
        var fail = false
        val controller = TrackingController(this, { user }, {
            if (fail) error("offline")
            MediaPage(listOf(series("a")), 1)
        }, { listOf(episode(it)) }, { _, _ -> }, {})
        controller.refresh(all = true)
        advanceUntilIdle()
        fail = true
        controller.refresh(force = true)
        advanceUntilIdle()
        assertEquals("offline", controller.state.value.error)
        assertFalse(controller.state.value.empty)
        assertEquals("a", controller.state.value.carousel.single().series.id)
    }

    @Test fun switchingAccountsDiscardsLateResponsesAndClearsCache() = runTest {
        var session: Session? = user
        val gate = CompletableDeferred<Unit>()
        val controller = TrackingController(this, { session }, {
            withContext(NonCancellable) { gate.await() }
            MediaPage(listOf(series("private")), 1)
        }, { listOf(episode(it)) }, { _, _ -> }, {})
        controller.refresh(all = true)
        runCurrent()
        controller.clear()
        session = user.copy(userId = "other")
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(TrackingUiState(), controller.state.value)
    }

    @Test fun failedUnfavoriteKeepsEntryAndSuccessfulRemovalIsServerConfirmed() = runTest {
        var list = listOf(series("a"), series("b"))
        var fail = true
        val controller = TrackingController(this, { user }, { MediaPage(list, list.size) },
            { listOf(episode(it)) }, { id, selected ->
                assertFalse(selected)
                if (fail) error("denied")
                list = list.filterNot { it.id == id }
            }, {})
        controller.refresh(all = true)
        advanceUntilIdle()
        controller.remove("a")
        advanceUntilIdle()
        assertEquals(2, controller.state.value.entries.size)
        assertEquals("denied", controller.state.value.actionError)
        fail = false
        controller.remove("a")
        advanceUntilIdle()
        assertEquals(listOf("b"), controller.state.value.entries.map { it.series.id })
        assertNull(controller.state.value.removingId)
    }
}
