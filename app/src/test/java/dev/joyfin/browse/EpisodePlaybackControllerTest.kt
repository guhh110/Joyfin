package dev.joyfin.browse

import dev.joyfin.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EpisodePlaybackControllerTest {
    private fun episode(id: String) = Media(id, id, "Episode", resumeTicks = 42)

    @Test
    fun supplementFailureDoesNotPreventIndependentPlaybackAndFreshProgressIsUsed() = runTest {
        var state: EpisodePlaybackState = EpisodePlaybackState.Idle
        val controller =
            EpisodePlaybackController(
                this,
                { episode(it).copy(resumeTicks = 100) },
                { state = it },
                {},
            )
        controller.play(episode("e1"), false, null, "row:e1")
        advanceUntilIdle()
        val ready = state as EpisodePlaybackState.Ready
        assertEquals("e1", ready.intent.episodeId)
        assertEquals(100L, ready.media.resumeTicks)
        assertEquals(42L, ready.intent.target.resumeTicks)
        assertFalse(ready.intent.fromStart)
    }

    @Test
    fun duplicatePressDoesNotStartAnotherRequest() = runTest {
        var calls = 0
        var state: EpisodePlaybackState = EpisodePlaybackState.Idle
        val controller =
            EpisodePlaybackController(
                this,
                {
                    calls++
                    delay(500)
                    episode(it)
                },
                { state = it },
                {},
            )
        controller.play(episode("e1"), false, null, "row:e1")
        controller.play(episode("e2"), false, null, "row:e2")
        advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals("e1", (state as EpisodePlaybackState.Ready).media.id)
    }

    @Test
    fun cancellationRejectsLateResponseAndNewIntentOwnsResult() = runTest {
        var state: EpisodePlaybackState = EpisodePlaybackState.Idle
        val controller =
            EpisodePlaybackController(
                this,
                { id ->
                    if (id == "e1") withContext(NonCancellable) { delay(500) }
                    episode(id)
                },
                { state = it },
                {},
            )
        controller.play(episode("e1"), false, null, "row:e1")
        runCurrent()
        controller.cancel()
        controller.play(episode("e2"), true, null, "row:e2")
        advanceUntilIdle()
        val ready = state as EpisodePlaybackState.Ready
        assertEquals("e2", ready.media.id)
        assertTrue(ready.intent.fromStart)
    }

    @Test
    fun retryKeepsOriginalIdVersionAndStartPolicy() = runTest {
        val seen = mutableListOf<String>()
        var state: EpisodePlaybackState = EpisodePlaybackState.Idle
        val controller =
            EpisodePlaybackController(
                this,
                {
                    seen += it
                    if (seen.size == 1) error("offline")
                    episode(it).copy(sources = listOf(JSONObject().put("Id", "version")))
                },
                { state = it },
                {},
            )
        controller.play(episode("e1"), true, "version", "start:e1")
        advanceUntilIdle()
        assertTrue(state is EpisodePlaybackState.Failed)
        controller.retry()
        advanceUntilIdle()
        val ready = state as EpisodePlaybackState.Ready
        assertEquals(listOf("e1", "e1"), seen)
        assertEquals("version", ready.intent.sourceId)
        assertEquals("start:e1", ready.intent.trigger)
        assertTrue(ready.intent.fromStart)
    }

    @Test
    fun deletedVersionDoesNotLaunchDefaultVersion() = runTest {
        var state: EpisodePlaybackState = EpisodePlaybackState.Idle
        val controller = EpisodePlaybackController(this, { episode(it) }, { state = it }, {})
        controller.play(episode("e1"), false, "deleted", "version:e1")
        advanceUntilIdle()
        assertTrue(state is EpisodePlaybackState.Failed)
        assertTrue((state as EpisodePlaybackState.Failed).message.contains("版本"))
    }

    @Test
    fun readyIsConsumedExactlyOnceAndCanceledReadyIsNotLaunchable() = runTest {
        var state: EpisodePlaybackState = EpisodePlaybackState.Idle
        val controller = EpisodePlaybackController(this, { episode(it) }, { state = it }, {})
        controller.play(episode("e1"), false, null, "row:e1")
        advanceUntilIdle()
        val sequence = (state as EpisodePlaybackState.Ready).intent.sequence
        controller.consumed(sequence + 1)
        assertTrue(state is EpisodePlaybackState.Ready)
        controller.consumed(sequence)
        assertEquals(EpisodePlaybackState.Idle, state)
        controller.consumed(sequence)
        assertEquals(EpisodePlaybackState.Idle, state)
    }

    @Test
    fun authFailureExpiresAndNeverPublishesReady() = runTest {
        var state: EpisodePlaybackState = EpisodePlaybackState.Idle
        var expired = false
        val controller =
            EpisodePlaybackController(
                this,
                { throw ApiException(401, "expired") },
                { state = it },
                { expired = true },
            )
        controller.play(episode("e1"), false, null, "row:e1")
        advanceUntilIdle()
        assertTrue(expired)
        assertFalse(state is EpisodePlaybackState.Ready)
    }
}
