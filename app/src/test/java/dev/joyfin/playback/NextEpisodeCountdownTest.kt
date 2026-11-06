package dev.joyfin.playback

import org.junit.Assert.*
import org.junit.Test

class NextEpisodeCountdownTest {
    @Test fun countdownUsesMediaPositionAndPlaybackSpeedInsteadOfWallClock() {
        assertEquals(0, NextEpisodeCountdown.seconds(54_999, 60_000, 1f))
        assertEquals(5, NextEpisodeCountdown.seconds(55_000, 60_000, 1f))
        repeat(10) { assertEquals(5, NextEpisodeCountdown.seconds(55_000, 60_000, 1f)) }
        assertEquals(3, NextEpisodeCountdown.seconds(55_000, 60_000, 2f))
        assertEquals(5, NextEpisodeCountdown.seconds(50_000, 60_000, 2f))
        assertEquals(4, NextEpisodeCountdown.seconds(58_000, 60_000, .5f))
        assertEquals(0, NextEpisodeCountdown.seconds(30_000, 60_000, 1f))
    }

    @Test fun countdownNeverAdvancesOnItsOwnAndIgnoresInvalidTimelines() {
        assertEquals(1, NextEpisodeCountdown.seconds(59_999, 60_000, 1f))
        assertEquals(0, NextEpisodeCountdown.seconds(60_000, 60_000, 1f))
        assertEquals(0, NextEpisodeCountdown.seconds(0, 0, 1f))
        assertEquals(0, NextEpisodeCountdown.seconds(-1, 60_000, 1f))
        assertEquals(0, NextEpisodeCountdown.seconds(9000, 10_000, 1f))
        assertEquals(0, NextEpisodeCountdown.seconds(55_000, 60_000, Float.NaN))
    }
}
