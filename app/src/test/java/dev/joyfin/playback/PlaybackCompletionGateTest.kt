package dev.joyfin.playback

import org.junit.Assert.*
import org.junit.Test

class PlaybackCompletionGateTest {
    @Test
    fun rebuildIgnoresEndedUntilCurrentSourceIsReady() {
        val gate = PlaybackCompletionGate()
        val old = gate.begin()
        gate.ready(old)
        val current = gate.begin()
        assertEquals(PlaybackCompletionGate.Result.Ignore, gate.ended(old, 60000, 60000, true))
        assertEquals(PlaybackCompletionGate.Result.Ignore, gate.ended(current, 60000, 60000, true))
        gate.ready(old)
        assertEquals(PlaybackCompletionGate.Result.Ignore, gate.ended(current, 60000, 60000, true))
        gate.ready(current)
        assertEquals(
            PlaybackCompletionGate.Result.Completed,
            gate.ended(current, 60000, 60000, true),
        )
    }

    @Test
    fun earlyStreamEndNeverStartsNextEpisode() {
        val gate = PlaybackCompletionGate()
        val token = gate.begin()
        gate.ready(token)
        assertEquals(PlaybackCompletionGate.Result.Premature, gate.ended(token, 20000, 60000, true))
        assertEquals(PlaybackCompletionGate.Result.Ignore, gate.ended(token, 60000, 60000, true))
    }

    @Test
    fun genuineEndIsConsumedExactlyOnce() {
        val gate = PlaybackCompletionGate()
        val token = gate.begin()
        gate.ready(token)
        assertEquals(PlaybackCompletionGate.Result.Completed, gate.ended(token, 59990, 60000, true))
        assertEquals(PlaybackCompletionGate.Result.Ignore, gate.ended(token, 60000, 60000, true))
    }

    @Test
    fun subtitleOnlyEmptyAndUnknownTimelinesCannotAdvance() {
        val gate = PlaybackCompletionGate()
        val token = gate.begin()
        gate.ready(token)
        assertEquals(PlaybackCompletionGate.Result.Ignore, gate.ended(null, 60000, 60000, true))
        assertEquals(PlaybackCompletionGate.Result.Ignore, gate.ended(token, 60000, 60000, false))
        assertEquals(PlaybackCompletionGate.Result.Ignore, gate.ended(token, 60000, 0, true))
        assertEquals(PlaybackCompletionGate.Result.Completed, gate.ended(token, 60000, 60000, true))
    }
}
