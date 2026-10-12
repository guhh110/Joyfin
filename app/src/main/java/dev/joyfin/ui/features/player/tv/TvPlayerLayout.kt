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
        if (ui.error != null)
            Column(
                Modifier.align(Alignment.TopCenter).padding(top = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.height(24.dp)) { Caption(ui.error, color = Palette.Error) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionButton(
                        "重试",
                        actions.retry,
                        Modifier.focusTarget("player:retry"),
                        quiet = true,
                    )
                    ActionButton(
                        "兼容转码",
                        actions.compatible,
                        Modifier.focusTarget("player:compatible"),
                        quiet = true,
                    )
                }
            }
    }
}
