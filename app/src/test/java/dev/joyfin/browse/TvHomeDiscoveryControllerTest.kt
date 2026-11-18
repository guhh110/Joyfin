package dev.joyfin.browse

import dev.joyfin.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TvHomeDiscoveryControllerTest {
    private val user = Session("http://server", "token", "user", "User")
    private val movie = Media("movie", "Movie", "Movie")

    @Test fun onlyFetchesWhenTvHomeIsActiveAndRefreshesOnReturn() = runTest {
        var calls = 0
        val controller = TvHomeDiscoveryController(this, { user }, { calls++; listOf(movie) }, {})
        controller.refresh()
        controller.setActive(false)
        runCurrent()
        assertEquals(0, calls)
        controller.setActive(true)
        advanceUntilIdle()
        assertEquals(listOf(movie), controller.state.value.items)
        controller.setActive(true)
        assertEquals(1, calls)
        controller.setActive(false)
        controller.setActive(true)
        assertEquals(listOf(movie), controller.state.value.items)
        advanceUntilIdle()
        assertEquals(2, calls)
    }

    @Test fun refreshFailureKeepsContentAndRetryCanSuccessfullyClearIt() = runTest {
        var fail = false
        var rows = listOf(movie)
        val controller = TvHomeDiscoveryController(this, { user }, {
            delay(50)
            if (fail) error("offline")
            rows
        }, {})
        controller.setActive(true)
        advanceUntilIdle()
        fail = true
        controller.refresh()
        assertTrue(controller.state.value.loading)
        assertEquals(listOf(movie), controller.state.value.items)
        advanceUntilIdle()
        assertEquals("offline", controller.state.value.error)
        assertEquals(listOf(movie), controller.state.value.items)
        fail = false
        rows = emptyList()
        controller.refresh()
        assertEquals("offline", controller.state.value.error)
        assertEquals(listOf(movie), controller.state.value.items)
        advanceUntilIdle()
        assertTrue(controller.state.value.loaded)
        assertTrue(controller.state.value.items.isEmpty())
        assertNull(controller.state.value.error)
    }

    @Test fun leavingHomeDiscardsCancellationUnawareResponse() = runTest {
        val controller = TvHomeDiscoveryController(this, { user }, {
            withContext(NonCancellable) { delay(100) }
            listOf(movie)
        }, {})
        controller.setActive(true)
        runCurrent()
        controller.setActive(false)
        advanceUntilIdle()
        assertFalse(controller.state.value.loading)
        assertFalse(controller.state.value.loaded)
        assertTrue(controller.state.value.items.isEmpty())
    }

    @Test fun accountSwitchClearsOldContentAndRejectsOlderResponses() = runTest {
        var owner = user
        var calls = 0
        val controller = TvHomeDiscoveryController(this, { owner }, {
            val sequence = ++calls
            withContext(NonCancellable) { delay(if (sequence == 1) 200 else 20) }
            listOf(movie.copy(id = "account$sequence"))
        }, {})
        controller.setActive(true)
        runCurrent()
        owner = user.copy(userId = "second")
        controller.setActive(true)
        assertTrue(controller.state.value.items.isEmpty())
        advanceUntilIdle()
        assertEquals("account2", controller.state.value.items.single().id)
        controller.clear()
        assertFalse(controller.state.value.loaded)
        assertTrue(controller.state.value.items.isEmpty())
    }

    @Test fun supersededRequestCannotOverwriteANewerRefresh() = runTest {
        var calls = 0
        val controller = TvHomeDiscoveryController(this, { user }, {
            val sequence = ++calls
            withContext(NonCancellable) { delay(if (sequence == 1) 200 else 20) }
            listOf(movie.copy(id = "response$sequence"))
        }, {})
        controller.setActive(true)
        runCurrent()
        controller.refresh()
        advanceUntilIdle()
        assertEquals("response2", controller.state.value.items.single().id)
        assertTrue(controller.state.value.loaded)
        assertFalse(controller.state.value.loading)
    }

    @Test fun unauthorizedExpiresTheSessionInsteadOfPublishingARegionalError() = runTest {
        var expired = false
        val controller = TvHomeDiscoveryController(this, { user }, { throw ApiException(401, "expired") }, { expired = true })
        controller.setActive(true)
        advanceUntilIdle()
        assertTrue(expired)
        assertNull(controller.state.value.error)
    }
}
