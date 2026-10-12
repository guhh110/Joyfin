package dev.joyfin.browse

import dev.joyfin.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeContentLoaderTest {
    @Test
    fun publishesFastRegionsWhileSlowRegionIsStillWaiting() = runTest {
        val completed = mutableListOf<HomeRegion>()
        val loader =
            HomeContentLoader(
                this,
                { region ->
                    if (region == HomeRegion.RESUME) delay(10_000)
                    emptyList()
                },
                { region, items, _ -> if (items != null) completed += region },
                {},
            )
        loader.load()
        runCurrent()
        assertEquals(setOf(HomeRegion.LATEST, HomeRegion.LIBRARIES), completed.toSet())
        advanceUntilIdle()
        assertEquals(3, completed.size)
    }

    @Test
    fun failureIsLocalAndRetryDoesNotRestartSuccessfulNeighbours() = runTest {
        val calls = mutableListOf<HomeRegion>()
        val errors = mutableMapOf<HomeRegion, String?>()
        var fail = true
        val loader =
            HomeContentLoader(
                this,
                { region ->
                    calls += region
                    if (region == HomeRegion.LATEST && fail) error("offline")
                    emptyList()
                },
                { region, _, error -> errors[region] = error },
                {},
            )
        loader.load()
        advanceUntilIdle()
        assertEquals("offline", errors[HomeRegion.LATEST])
        assertNull(errors[HomeRegion.RESUME])
        fail = false
        loader.retry(HomeRegion.LATEST)
        advanceUntilIdle()
        assertNull(errors[HomeRegion.LATEST])
        assertEquals(1, calls.count { it == HomeRegion.RESUME })
        assertEquals(2, calls.count { it == HomeRegion.LATEST })
    }

    @Test
    fun cancellationDiscardsCancellationUnawareResponse() = runTest {
        val completed = mutableListOf<List<Media>>()
        val loader =
            HomeContentLoader(
                this,
                {
                    withContext(NonCancellable) { delay(500) }
                    listOf(Media("stale", "Stale", "Movie"))
                },
                { _, items, _ -> items?.let(completed::add) },
                {},
            )
        loader.load()
        runCurrent()
        loader.cancel()
        advanceUntilIdle()
        assertTrue(completed.isEmpty())
    }

    @Test
    fun unauthorizedUsesSessionHandlerInsteadOfRegionalError() = runTest {
        var expired = false
        val errors = mutableListOf<String>()
        val loader =
            HomeContentLoader(
                this,
                { throw ApiException(401, "expired") },
                { _, _, error -> error?.let(errors::add) },
                { expired = true },
            )
        loader.load()
        advanceUntilIdle()
        assertTrue(expired)
        assertTrue(errors.isEmpty())
    }
}
