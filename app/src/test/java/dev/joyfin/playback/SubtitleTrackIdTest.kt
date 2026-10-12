package dev.joyfin.playback

import org.junit.Assert.*
import org.junit.Test

class SubtitleTrackIdTest {
    @Test
    fun mergedAndOriginalTrackIdsSelectSameSubtitle() {
        val id = SubtitleTrackId.external(7)
        assertFalse(id.contains(':'))
        assertTrue(SubtitleTrackId.matches(id, 7))
        assertTrue(SubtitleTrackId.matches("2:$id", 7))
        assertFalse(SubtitleTrackId.matches("2:$id", 8))
        assertFalse(SubtitleTrackId.matches(null, 7))
        assertFalse(SubtitleTrackId.matches("2:embedded-subtitle-7", 7))
    }
}
