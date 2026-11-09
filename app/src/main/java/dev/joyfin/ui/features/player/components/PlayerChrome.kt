package dev.joyfin.ui.features.player.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import dev.joyfin.playback.PlayerActions
import dev.joyfin.playback.PlayerUiState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.theme.Palette
import kotlinx.coroutines.delay

@Composable
fun PlayerHeader(
    ui: PlayerUiState,
    actions: PlayerActions,
    onPanel: (PlayerPanel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PlayerChromeButton("退出播放器", Glyph.Back, actions.close, Modifier.focusTarget("player:back"))
        Text(listOf(ui.title, ui.subtitle).filter(String::isNotBlank).joinToString(" · "),
                Modifier.weight(1f), color = Palette.Text,
                fontSize = if (LocalDeviceLayout.current.television) 24.sp else 20.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .6f), blurRadius = 8f)))
        PlayerClock()
        if (!LocalDeviceLayout.current.television) {
            PlayerBattery()
        }
        PlayerChromeButton(
            "播放设置",
            Glyph.Settings,
            { onPanel(PlayerPanel.Settings) },
            Modifier.focusTarget("player:settings"),
        )
    }
}

@Composable
fun PlayerClock(modifier: Modifier = Modifier) {
    var timeText by remember { mutableStateOf(currentClockText()) }
    LaunchedEffect(Unit) {
        while (true) {
            timeText = currentClockText()
            delay(10_000)
        }
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        AppIcon(Glyph.Clock, Modifier.size(14.dp), color = Palette.Text.copy(alpha = 0.7f))
        Caption(
            timeText,
            color = Palette.Text.copy(alpha = 0.85f),
        )
    }
}

private fun currentClockText(): String {
    val now = java.time.LocalTime.now()
    return String.format(java.util.Locale.ROOT, "%02d:%02d", now.hour, now.minute)
}

@Composable
fun PlayerBottomBar(
    ui: PlayerUiState,
    actions: PlayerActions,
    onPanel: (PlayerPanel) -> Unit,
    modifier: Modifier = Modifier,
    onInteraction: () -> Unit = {},
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ui.playbackNotice?.let { Caption(it, maxLines = 2) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val method = playerMethodLabel(ui.method)
            if (method.isNotBlank()) {
                Caption(method, color = Palette.Text.copy(alpha = .75f))
                Caption("·", color = Palette.Text.copy(alpha = .4f))
            }
            Caption(if (ui.bitrate == dev.joyfin.data.PlaybackQuality.ORIGINAL) "原画" else "${ui.bitrate / 1_000_000f} Mbps",
                color = Palette.Text.copy(alpha = .75f))
            Spacer(Modifier.weight(1f))
            if (ui.durationMs > 0) {
                Caption("剩余 ${timeLabel((ui.durationMs - ui.positionMs).coerceAtLeast(0))}", maxLines = 1)
            }
        }
        SeekBar(
            ui.positionMs,
            ui.durationMs,
            ui.bufferedMs,
            ui.seekPreview,
            actions.preview,
            ui.canSeek && !ui.loading && ui.error == null,
            skipRanges = ui.navigation.ranges,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()).focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ui.toolbar.pinned.forEach { action ->
                    key(action) {
                        PlayerToolbarButton(action, ui, actions, onPanel, onInteraction)
                    }
                }
            }
            PlayerChromeButton("更多", Glyph.List, {
                onInteraction()
                onPanel(PlayerPanel.More)
            },
                Modifier.focusTarget("player:more"), text = "更多")
        }
    }
}

internal fun playerMethodLabel(method: String): String = when (method) {
    "DirectPlay" -> "直接播放"
    "DirectStream" -> "直接串流"
    "Transcode" -> "转码播放"
    else -> ""
}
