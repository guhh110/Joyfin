package dev.joyfin.playback

import org.junit.Assert.*
import org.junit.Test

class PlaybackTimingTest {
    @Test fun shortPlaybackThenTenSecondRebufferHasSeparateStartupAndRebufferRecords() {
        val tracker = PlaybackTimingTracker(100)
        tracker.attempt(1, 100, 0, PlaybackLoadReason.INITIAL)
        tracker.state(1, 200, TimingPlayerState.BUFFERING, 0, true)
        tracker.state(1, 1100, TimingPlayerState.READY, 0, true)
        tracker.firstFrame(1, 1200)
        tracker.state(1, 3200, TimingPlayerState.BUFFERING, 2000, true)
        tracker.state(1, 13200, TimingPlayerState.READY, 2000, true)
        val snapshot = tracker.snapshot(14000)
        assertEquals(1100L, snapshot.firstFrameMs)
        assertEquals(listOf(PlaybackBufferKind.STARTUP, PlaybackBufferKind.REBUFFER), snapshot.buffers.map { it.kind })
        assertEquals(listOf(900L, 10000L), snapshot.buffers.map { it.durationMs })
        assertEquals(2000L, snapshot.buffers.last().positionMs)
        assertTrue(snapshot.buffers.all { it.finished && !it.canceled })
        assertEquals(1, snapshot.attemptCount)
    }

    @Test fun seekCallbacksMayFollowBufferingAndBufferedSeekDoesNotMislabelTheNextStall() {
        val tracker = PlaybackTimingTracker(0)
        tracker.attempt(1, 0, 0, PlaybackLoadReason.INITIAL)
        tracker.state(1, 100, TimingPlayerState.READY, 0, true)
        tracker.state(1, 500, TimingPlayerState.BUFFERING, 30000, true)
        tracker.operation(1, PlaybackBufferKind.SEEK)
        tracker.state(1, 900, TimingPlayerState.READY, 30000, true)
        tracker.operation(1, PlaybackBufferKind.SEEK)
        tracker.state(1, 950, TimingPlayerState.READY, 31000, true)
        tracker.state(1, 1000, TimingPlayerState.BUFFERING, 31050, true)
        assertEquals(listOf(PlaybackBufferKind.SEEK, PlaybackBufferKind.REBUFFER), tracker.snapshot(1100).buffers.map { it.kind })
    }

    @Test fun reloadInterruptsBufferingAndRejectsOldGenerationCallbacks() {
        val tracker = PlaybackTimingTracker(0)
        tracker.attempt(1, 0, 0, PlaybackLoadReason.INITIAL)
        tracker.firstFrame(1, 100)
        tracker.firstFrame(1, 200)
        tracker.state(1, 300, TimingPlayerState.BUFFERING, 200, true)
        tracker.attempt(2, 400, 200, PlaybackLoadReason.DEVICE)
        tracker.state(1, 500, TimingPlayerState.READY, 200, true)
        tracker.firstFrame(1, 600)
        tracker.firstFrame(2, 1400)
        val snapshot = tracker.snapshot(2000)
        assertEquals(100L, snapshot.firstFrameMs)
        assertEquals(listOf(100L, 1000L), snapshot.attempts.map { it.firstFrameMs })
        assertEquals(PlaybackLoadReason.DEVICE, snapshot.attempts.last().reason)
        assertEquals(100L, snapshot.buffers.single().durationMs)
        assertTrue(snapshot.buffers.single().canceled)
    }

    @Test fun pausedLoadingAndSubtitleChangesAreNotReportedAsUnexpectedRebuffering() {
        val tracker = PlaybackTimingTracker(0)
        tracker.attempt(1, 0, 0, PlaybackLoadReason.INITIAL)
        tracker.state(1, 10, TimingPlayerState.READY, 0, false)
        tracker.state(1, 20, TimingPlayerState.BUFFERING, 0, false)
        tracker.state(1, 30, TimingPlayerState.READY, 0, false)
        tracker.operation(1, PlaybackBufferKind.SUBTITLE)
        tracker.state(1, 40, TimingPlayerState.BUFFERING, 0, false)
        tracker.stop(50)
        tracker.state(1, 60, TimingPlayerState.READY, 0, false)
        val snapshot = tracker.snapshot(100)
        assertEquals(listOf(PlaybackBufferKind.PAUSED, PlaybackBufferKind.SUBTITLE), snapshot.buffers.map { it.kind })
        assertTrue(snapshot.buffers.last().canceled)
        assertEquals(10L, snapshot.buffers.last().durationMs)
    }

    @Test fun repeatedBufferEventsDoNotRestartTheTimerAndHistoryIsBounded() {
        val tracker = PlaybackTimingTracker(0)
        repeat(40) { i ->
            val owner = i.toLong()
            val now = i * 100L
            tracker.attempt(owner, now, 0, PlaybackLoadReason.RETRY)
            tracker.state(owner, now + 10, TimingPlayerState.BUFFERING, 0, true)
            tracker.state(owner, now + 20, TimingPlayerState.BUFFERING, 0, true)
            tracker.state(owner, now + 50, TimingPlayerState.READY, 0, true)
        }
        val snapshot = tracker.snapshot(5000)
        assertEquals(40, snapshot.attemptCount)
        assertEquals(10, snapshot.attempts.size)
        assertEquals(20, snapshot.buffers.size)
        assertTrue(snapshot.buffers.all { it.durationMs == 40L })
    }
}
