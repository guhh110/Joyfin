package dev.joyfin.browse

import dev.joyfin.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeLibraryLoaderTest {
    private fun library(id: String) = Media(id, id, "CollectionFolder", collectionType = "movies")

    @Test
    fun maxThreeRequestsAndIndependentFailureRetry() = runTest {
        var inFlight = 0
        var maximum = 0
        var fail = true
        val states = mutableMapOf<String, LibraryRailState>()
        val loader =
            HomeLibraryLoader(
                this,
                { id ->
                    inFlight++
                    maximum = maxOf(maximum, inFlight)
                    try {
                        delay(100)
                        if (id == "2" && fail) error("offline")
                        listOf(Media(id, id, "Movie"))
                    } finally {
                        inFlight--
                    }
                },
                { states[it.library.id] = it },
                { fail("unexpected auth expiry") },
            )
        loader.load((1..8).map { library("$it") })
        advanceUntilIdle()
        assertEquals(3, maximum)
        assertEquals(8, states.size)
        assertEquals("offline", states["2"]!!.error)
        assertEquals(1, states["3"]!!.items.size)
        fail = false
        loader.retry(library("2"))
        advanceUntilIdle()
        assertNull(states["2"]!!.error)
        assertEquals(1, states["2"]!!.items.size)
    }

    @Test
    fun cancellationDoesNotPublishOldRows() = runTest {
        val states = mutableListOf<LibraryRailState>()
        val loader =
            HomeLibraryLoader(
                this,
                {
                    delay(100)
                    emptyList()
                },
                states::add,
                {},
            )
        loader.load(listOf(library("old")))
        runCurrent()
        loader.cancel()
        val count = states.size
        advanceUntilIdle()
        assertEquals(count, states.size)
    }

    @Test
    fun authFailureIsDistinctFromEmptyLibrary() = runTest {
        var expired = 0
        val states = mutableMapOf<String, LibraryRailState>()
        val loader =
            HomeLibraryLoader(
                this,
                { id -> if (id == "private") throw ApiException(401, "expired") else emptyList() },
                { states[it.library.id] = it },
                { expired++ },
            )
        loader.load(listOf(library("private"), library("empty")))
        advanceUntilIdle()
        assertEquals(1, expired)
        assertFalse(states["empty"]!!.loading)
        assertNull(states["empty"]!!.error)
    }

    @Test
    fun libraryTypesAndLatestGroupingPreserveOrdering() {
        assertTrue(library("电影").isVideoLibrary())
        assertTrue(library("混合").copy(collectionType = "mixed").isVideoLibrary())
        assertFalse(library("音乐").copy(collectionType = "music").isVideoLibrary())
        assertFalse(library("照片").copy(collectionType = "photos").isVideoLibrary())
        val list =
            listOf(
                Media("e2", "E2", "Episode", seriesId = "show", seriesName = "动漫"),
                Media("movie", "Movie", "Movie"),
                Media("e1", "E1", "Episode", seriesId = "show"),
                Media("song", "Song", "Audio"),
            )
        assertEquals(listOf("show", "movie"), groupLatestVideo(list).map { it.id })
        assertEquals("Series", groupLatestVideo(list).first().type)
    }
}
