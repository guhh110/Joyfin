package dev.joyfin.ui.features.player.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import dev.joyfin.playback.PlayerActions
import dev.joyfin.playback.PlayerUiState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.features.player.components.*
import dev.joyfin.ui.interaction.RememberScreenFocus
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.theme.Palette

@Composable
internal fun MobilePlayerLayout(
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
        22.dp,
        showTransport = ui.error == null && ui.nextSeconds == 0 && panel == null,
    )
    if (ui.error != null)
        RememberScreenFocus("player:retry") {
            Sheet("无法播放", actions.close) {
                Caption(ui.error, color = Palette.Error)
                ActionButton(
                    "重试",
                    actions.retry,
                    Modifier.focusTarget("player:retry"),
                    primary = true,
                )
                ActionButton("兼容转码", actions.compatible)
                ActionButton("返回", actions.close)
            }
        }
}
