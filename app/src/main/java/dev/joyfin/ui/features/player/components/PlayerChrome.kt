package dev.joyfin.ui.features.player.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.playback.PlayerActions
import dev.joyfin.playback.PlayerUiState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.focusTarget

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
        }
        Caption(
            when (ui.method) {
                "DirectPlay" -> "直接播放"
                "DirectStream" -> "直接串流"
                "Transcode" -> "转码播放"
                else -> ""
            }
        )
        IconButton(
            "播放设置",
            Glyph.Settings,
            { onPanel(PlayerPanel.Settings) },
            Modifier.focusTarget("player:settings"),
        )
    }
}

@Composable
fun PlayerBottomBar(
    ui: PlayerUiState,
    actions: PlayerActions,
    onPanel: (PlayerPanel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tv = LocalDeviceLayout.current.television
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
            androidx.compose.foundation.lazy.LazyRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    ActionButton(
                        "${ui.speed}×",
                        { onPanel(PlayerPanel.Speed) },
                        modifier = Modifier.focusTarget("player:speed"),
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
                        ui.localSubtitle?.label ?: ui.subtitles.firstOrNull { it.index == ui.subtitleIndex }?.title ?: "字幕关闭",
                        { onPanel(PlayerPanel.Subtitle) },
                        modifier = Modifier.focusTarget("player:subtitle"),
                        enabled = !tv || ui.subtitles.isNotEmpty() || ui.localSubtitle != null || ui.localSubtitles.isNotEmpty(),
                        icon = Glyph.Subtitle,
                        quiet = true,
                    )
                }
                item {
                    ActionButton(
                        ui.audio.firstOrNull { it.index == ui.audioIndex }?.title ?: "音轨",
                        { onPanel(PlayerPanel.Audio) },
                        modifier = Modifier.focusTarget("player:audio"),
                        enabled = !tv || ui.audio.isNotEmpty(),
                        icon = Glyph.Audio,
                        quiet = true,
                    )
                }
            }
            if (tv || ui.episodes.isNotEmpty() || ui.seasons.isNotEmpty())
                ActionButton(
                    "选集",
                    { onPanel(PlayerPanel.Episodes) },
                    modifier = Modifier.focusTarget("player:episodes"),
                    enabled = ui.episodes.isNotEmpty() || ui.seasons.isNotEmpty(),
                    icon = Glyph.List,
                    quiet = true,
                )
        }
    }
}
