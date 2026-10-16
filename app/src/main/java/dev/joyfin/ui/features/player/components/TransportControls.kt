package dev.joyfin.ui.features.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.playback.PlayerActions
import dev.joyfin.playback.PlayerUiState
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.theme.Palette

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
        if (!ui.playing && enabled) {
            val pauseShape = RoundedCornerShape(12.dp)
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = if (tv) 62.dp else 52.dp)
                    .clip(pauseShape)
                    .background(Color(0xFF0C0E14).copy(alpha = 0.85f))
                    .border(0.5.dp, Palette.Line, pauseShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(Palette.Text.copy(alpha = 0.7f))
                )
                Text(
                    "已暂停",
                    color = Palette.Text,
                    fontSize = if (tv) 12.sp else 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }
}
