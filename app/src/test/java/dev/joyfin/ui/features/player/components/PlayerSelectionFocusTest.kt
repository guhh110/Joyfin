package dev.joyfin.ui.features.player.components

import dev.joyfin.data.SubtitleSize
import dev.joyfin.playback.PlayerUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerSelectionFocusTest {
    @Test
    fun subtitleStyleFocusesOnCurrentSize() {
        val standardState = PlayerUiState(subtitleSize = SubtitleSize.STANDARD)
        assertEquals("player-choice:SubtitleStyle:STANDARD", PlayerPanel.SubtitleStyle.initialFocus(standardState))

        val extraLargeState = PlayerUiState(subtitleSize = SubtitleSize.EXTRA_LARGE)
        assertEquals("player-choice:SubtitleStyle:EXTRA_LARGE", PlayerPanel.SubtitleStyle.initialFocus(extraLargeState))
    }
}
