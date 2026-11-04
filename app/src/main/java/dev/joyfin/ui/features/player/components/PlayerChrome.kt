package dev.joyfin.ui.features.player.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
        IconButton("退出播放器", Glyph.Back, actions.close, Modifier.focusTarget("player:back"))
        Column(Modifier.weight(1f)) {
            SectionTitle(ui.title)
            Caption(ui.subtitle)
            ui.playbackNotice?.let { Caption(it, maxLines = 1) }
        }
        val methodText = when (ui.method) {
            "DirectPlay" -> "直接播放"
            "DirectStream" -> "直接串流"
            "Transcode" -> "转码播放"
            else -> ""
        }
        if (methodText.isNotBlank()) {
            QualityBadge(methodText)
        }
        PlayerClock()
        if (!LocalDeviceLayout.current.television) {
            PlayerBattery()
        }
        IconButton(
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
    Column(modifier.fillMaxWidth()) {
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
            Modifier.fillMaxWidth()
                .background(Palette.Background.copy(alpha = .55f), RoundedCornerShape(12.dp))
                .border(.5.dp, Palette.Text.copy(alpha = .1f), RoundedCornerShape(12.dp))
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()).focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ui.toolbar.pinned.forEach { action ->
                    key(action) {
                        PlayerToolbarButton(action, ui, actions, onPanel, onInteraction)
                    }
                }
            }
            Box(Modifier.width(.5.dp).height(24.dp).background(Palette.Text.copy(alpha = .15f)))
            ActionButton("更多", {
                onInteraction()
                onPanel(PlayerPanel.More)
            },
                Modifier.focusTarget("player:more"), icon = Glyph.List, quiet = true, maxLines = 1,
                focusedScale = dev.joyfin.ui.theme.MotionTokens.FixedScale)
        }
    }
}
