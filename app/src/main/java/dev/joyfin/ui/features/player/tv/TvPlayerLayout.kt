package dev.joyfin.ui.features.player.tv

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import dev.joyfin.playback.PlayerActions
import dev.joyfin.playback.PlayerUiState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.features.player.components.*
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.theme.Palette

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color

@Composable
internal fun TvPlayerLayout(
    ui: PlayerUiState,
    actions: PlayerActions,
    panel: PlayerPanel?,
    onPanel: (PlayerPanel) -> Unit,
    onInteraction: () -> Unit,
) {
    PlayerControlsFrame(
        ui,
        actions,
        onPanel,
        onInteraction,
        40.dp,
        showTransport = ui.nextSeconds == 0 && panel == null,
    ) {
        if (ui.error != null && panel == null)
            Column(
                Modifier.align(Alignment.TopCenter).padding(top = 48.dp)
                    .background(Color(0xE6141418), RoundedCornerShape(12.dp))
                    .border(1.dp, Palette.BorderSubtle, RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppIcon(Glyph.Info, Modifier.size(18.dp), color = Palette.Muted)
                    Caption(browseErrorText(ui.error), color = Palette.Text)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionButton(
                        "重试",
                        actions.retry,
                        Modifier.focusTarget("player:retry"),
                        icon = Glyph.Refresh,
                        primary = true,
                    )
                    ActionButton(
                        "兼容转码",
                        actions.compatible,
                        Modifier.focusTarget("player:compatible"),
                        quiet = false,
                    )
                    ActionButton("播放诊断", { onPanel(PlayerPanel.Diagnostics) },
                        Modifier.focusTarget("player:diagnostics"), icon = Glyph.Info)
                }
            }
    }
}
