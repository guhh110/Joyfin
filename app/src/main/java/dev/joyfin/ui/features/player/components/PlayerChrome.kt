package dev.joyfin.ui.features.player.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
) {
    val tv = LocalDeviceLayout.current.television
    val subtitleLabel = ui.localSubtitle?.label
        ?: ui.subtitles.firstOrNull { it.index == ui.subtitleIndex }?.title ?: "字幕关闭"
    val audioLabel = ui.audio.firstOrNull { it.index == ui.audioIndex }?.title ?: "音轨"
    Column(modifier.fillMaxWidth()) {
        SeekBar(
            ui.positionMs,
            ui.durationMs,
            ui.bufferedMs,
            ui.seekPreview,
            actions.preview,
            ui.canSeek && !ui.loading && ui.error == null,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LazyRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    ActionButton(
                        "${ui.speed}×",
                        { onPanel(PlayerPanel.Speed) },
                        modifier = Modifier.focusTarget("player:speed"),
                        icon = Glyph.Clock,
                        quiet = true,
                    )
                }
                item {
                    ActionButton(
                        if (ui.bitrate == dev.joyfin.data.PlaybackQuality.ORIGINAL) "原画"
                        else "${ui.bitrate/1_000_000f} Mbps",
                        { onPanel(PlayerPanel.Quality) },
                        modifier = Modifier.focusTarget("player:quality"),
                        quiet = true,
                    )
                }
                item {
                    ActionButton(
                        subtitleLabel,
                        { onPanel(PlayerPanel.Subtitle) },
                        modifier = Modifier.widthIn(max = if (tv) 220.dp else 180.dp)
                            .focusTarget("player:subtitle")
                            .semantics { contentDescription = "字幕：$subtitleLabel" },
                        enabled = !tv || ui.subtitles.isNotEmpty() || ui.localSubtitle != null || ui.localSubtitles.isNotEmpty(),
                        icon = Glyph.Subtitle,
                        quiet = true,
                        maxLines = 1,
                    )
                }
                item {
                    ActionButton(
                        audioLabel,
                        { onPanel(PlayerPanel.Audio) },
                        modifier = Modifier.widthIn(max = if (tv) 220.dp else 180.dp)
                            .focusTarget("player:audio")
                            .semantics { contentDescription = "音轨：$audioLabel" },
                        enabled = !tv || ui.audio.isNotEmpty(),
                        icon = Glyph.Audio,
                        quiet = true,
                        maxLines = 1,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (tv || ui.nextItem != null)
                    ActionButton(
                        "下一集",
                        actions.nextNow,
                        modifier = Modifier.focusTarget("player:next"),
                        enabled = ui.nextItem != null && !ui.loading && ui.error == null &&
                            ui.nextSeconds == 0 && !ui.subtitleApplying && !ui.subtitleSearchOpen,
                        icon = Glyph.Next,
                        quiet = true,
                        maxLines = 1,
                    )
                if (tv || ui.episodes.isNotEmpty() || ui.seasons.isNotEmpty())
                    ActionButton(
                        "选集",
                        { onPanel(PlayerPanel.Episodes) },
                        modifier = Modifier.focusTarget("player:episodes"),
                        enabled = ui.episodes.isNotEmpty() || ui.seasons.isNotEmpty(),
                        icon = Glyph.List,
                        quiet = true,
                        maxLines = 1,
                    )
            }
        }
    }
}
