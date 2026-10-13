package dev.joyfin.playback

import dev.joyfin.data.PlaybackReport
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackStopQueueTest {
    private fun stop(id: String) =
        PlaybackStop(PlaybackReport(playSessionId = id, itemId = "item"), true)

    @Test
    fun cleanupRetriesAndPersistsBeforeNetworkThenClearsOnSuccess() = runTest {
        var saved = emptyList<PlaybackStop>()
        var attempts = 0
        val queue =
            PlaybackStopQueue(this, emptyList(), { saved = it }) {
                assertEquals("play", saved.single().key)
                if (++attempts < 3) throw IOException("offline")
            }
        queue.enqueue(stop("play"))
        assertEquals("play", saved.single().key)
        queue.close()
        advanceUntilIdle()
        assertEquals(3, attempts)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun failedStopRemainsForNextInstanceWithoutInfiniteRetries() = runTest {
        var saved = emptyList<PlaybackStop>()
        var calls = 0
        val queue =
            PlaybackStopQueue(this, emptyList(), { saved = it }) {
                calls++
                throw IOException("offline")
            }
        queue.enqueue(stop("play"))
        queue.close()
        advanceUntilIdle()
        assertEquals(3, calls)
        assertEquals("play", saved.single().key)
        val retry = PlaybackStopQueue(this, saved, { saved = it }) { calls++ }
        retry.close()
        advanceUntilIdle()
        assertEquals(4, calls)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun separateSessionStopsAreNotConflatedAndCloseDrainsThem() = runTest {
        val delivered = mutableListOf<String>()
        val queue = PlaybackStopQueue(this, emptyList(), {}) { delivered += it.key }
        queue.enqueue(stop("first"))
        queue.enqueue(stop("second"))
        queue.close()
        advanceUntilIdle()
        assertEquals(listOf("first", "second"), delivered)
    }

    @Test
    fun cancellationDoesNotEraseUndeliveredStop() = runTest {
        var saved = emptyList<PlaybackStop>()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val queue = PlaybackStopQueue(scope, emptyList(), { saved = it }) { awaitCancellation() }
        queue.enqueue(stop("play"))
        runCurrent()
        scope.cancel()
        runCurrent()
        assertEquals("play", saved.single().key)
    }
}
