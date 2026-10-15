package dev.joyfin.ui.features.player.components

import dev.joyfin.ui.theme.Palette
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.playback.PlayerActions
import dev.joyfin.playback.PlayerUiState
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.focusTarget

@Composable
fun TransportControls(ui: PlayerUiState, actions: PlayerActions, onInteraction: () -> Unit) {
    val tv = LocalDeviceLayout.current.television
    val enabled = !ui.loading && ui.error == null && ui.nextSeconds == 0
    Box(contentAlignment = Alignment.Center) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (tv) 28.dp else 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerSkipButton(
                false,
                enabled && ui.canSeek,
                {
                    onInteraction()
                    actions.preview.adjust(-10_000)
                },
                Modifier.focusTarget("player:rewind", tv || (enabled && ui.canSeek)),
            )
            PlayerToggleButton(
                ui.playing,
                enabled,
                {
                    onInteraction()
                    actions.toggle()
                },
                Modifier.focusTarget("player:toggle", tv || enabled),
                loading = ui.loading && ui.error == null,
            )
            PlayerSkipButton(
                true,
                enabled && ui.canSeek,
                {
                    onInteraction()
                    actions.preview.adjust(10_000)
                },
                Modifier.focusTarget("player:forward", tv || (enabled && ui.canSeek)),
            )
        }
        if (!ui.playing && enabled)
            Text(
                "已暂停",
                Modifier.align(Alignment.Center).offset(y = if (tv) 59.dp else 50.dp),
                color = Palette.Text.copy(alpha = .65f),
                fontSize = if (tv) 14.sp else 12.sp,
            )
    }
}
