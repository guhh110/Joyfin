package dev.joyfin.playback

import dev.joyfin.data.PlaybackReport
import dev.joyfin.data.Session
import org.junit.Assert.*
import org.junit.Test

class PlaybackStopPersistenceTest {
    private val owner = Session("https://jellyfin.example", "not-persisted", "user", "Viewer")

    private fun stop(id: String) = PlaybackStop(PlaybackReport(playSessionId = id), true)

    @Test
    fun oldPendingStopPayloadKeepsWireFieldsAndOwnership() {
        val raw =
            """{"user":"user","server":"https:\/\/jellyfin.example","requests":[{"body":{"ItemId":"episode","PlaySessionId":"old","PositionTicks":123},"encoding":true}]}"""
        val stop = PlaybackStopPersistence(owner, { raw }, {}).load().single()
        assertEquals("old", stop.key)
        assertEquals("episode", stop.body.itemId)
        assertEquals(123L, stop.body.positionTicks)
        assertTrue(stop.encoding)
    }

    @Test
    fun finishingOldPlayerDoesNotEraseNewPlayersPendingStop() {
        var raw: String? = null
        val old = PlaybackStopPersistence(owner, { raw }, { raw = it })
        old.load()
        old.save(listOf(stop("old")))
        val new = PlaybackStopPersistence(owner, { raw }, { raw = it })
        val restored = new.load()
        new.save(restored + stop("new"))
        old.save(emptyList())
        val result = PlaybackStopPersistence(owner, { raw }, { raw = it }).load()
        assertEquals(listOf("new"), result.map { it.key })
        assertFalse(raw!!.contains(owner.token))
    }

    @Test
    fun pendingStopsNeverReplayOnDifferentAccountOrServer() {
        var raw: String? = null
        val persistence = PlaybackStopPersistence(owner, { raw }, { raw = it })
        persistence.load()
        persistence.save(listOf(stop("old")))
        for (other in
            listOf(owner.copy(userId = "other"), owner.copy(server = "https://other.example"))) {
            assertTrue(PlaybackStopPersistence(other, { raw }, { raw = it }).load().isEmpty())
        }
    }

    @Test
    fun signingOutWhileCleanupRunsDoesNotRestoreOldAccountRecords() {
        var raw: String? = null
        var active = true
        val persistence = PlaybackStopPersistence(owner, { raw }, { raw = it }, { active })
        persistence.load()
        persistence.save(listOf(stop("old")))
        raw = null
        active = false
        persistence.save(listOf(stop("old")))
        assertNull(raw)
    }
}
