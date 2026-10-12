package dev.joyfin.ui.features.player.components

import androidx.compose.runtime.Composable
import dev.joyfin.playback.PlayerActions
import dev.joyfin.playback.PlayerUiState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.RememberScreenFocus

enum class PlayerPanel(val title: String) {
    Settings("播放设置"),
    Quality("画质"),
    Audio("音轨"),
    Subtitle("字幕"),
    Speed("播放速度"),
    Episodes("选集"),
    Seasons("选择季"),
    Versions("媒体版本"),
}

@Composable
fun PlayerSettingsSheet(
    panel: PlayerPanel,
    ui: PlayerUiState,
    actions: PlayerActions,
    onPanel: (PlayerPanel) -> Unit,
    onDismiss: () -> Unit,
) {
    RememberScreenFocus("sheet:close") {
        Sheet(panel.title, onDismiss, side = true) {
            when (panel) {
                PlayerPanel.Settings -> {
                    ChoiceRow("画质", subtitle = "${ui.bitrate/1_000_000} Mbps 上限") {
                        onPanel(PlayerPanel.Quality)
                    }
                    ChoiceRow(
                        "字幕",
                        subtitle =
                            if (ui.subtitleIndex < 0) "关闭"
                            else
                                ui.subtitles
                                    .firstOrNull { it.index == ui.subtitleIndex }
                                    ?.title
                                    .orEmpty(),
                    ) {
                        onPanel(PlayerPanel.Subtitle)
                    }
                    ChoiceRow(
                        "音轨",
                        subtitle = ui.audio.firstOrNull { it.index == ui.audioIndex }?.title ?: "默认",
                    ) {
                        onPanel(PlayerPanel.Audio)
                    }
                    ChoiceRow("播放速度", subtitle = "${ui.speed}×") { onPanel(PlayerPanel.Speed) }
                    ChoiceRow("媒体版本") { onPanel(PlayerPanel.Versions) }
                    ChoiceRow("兼容播放", subtitle = "请求服务器转换当前媒体") {
                        onDismiss()
                        actions.compatible()
                    }
                }
                PlayerPanel.Quality -> {
                    Caption("选择码率上限，实际画质由媒体与服务器决定。")
                    listOf(
                            80_000_000 to "原画优先",
                            20_000_000 to "高清优先",
                            8_000_000 to "更稳定",
                            4_000_000 to "节省流量",
                            1_500_000 to "低带宽",
                        )
                        .forEach { (rate, name) ->
                            ChoiceRow(
                                name,
                                subtitle = "${rate/1_000_000f} Mbps",
                                selected = ui.bitrate == rate,
                            ) {
                                onDismiss()
                                actions.selectBitrate(rate)
                            }
                        }
                }
                PlayerPanel.Audio -> {
                    if (ui.audio.isEmpty()) Caption("当前媒体没有可选音轨")
                    ui.audio.forEach { track ->
                        ChoiceRow(
                            track.title,
                            subtitle = track.detail,
                            selected = track.index == ui.audioIndex,
                        ) {
                            onDismiss()
                            actions.selectAudio(track.index)
                        }
                    }
                }
                PlayerPanel.Subtitle -> {
                    ChoiceRow("关闭", subtitle = "不显示字幕", selected = ui.subtitleIndex < 0) {
                        onDismiss()
                        actions.selectSubtitle(-1)
                    }
                    ui.subtitles.forEach { track ->
                        ChoiceRow(
                            track.title,
                            subtitle = track.detail,
                            selected = track.index == ui.subtitleIndex,
                        ) {
                            onDismiss()
                            actions.selectSubtitle(track.index)
                        }
                    }
                }
                PlayerPanel.Speed ->
                    listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                        ChoiceRow("${speed}×", selected = ui.speed == speed) {
                            onDismiss()
                            actions.speed(speed)
                        }
                    }
                PlayerPanel.Episodes -> {
                    ActionButton(
                        ui.seasons.firstOrNull { it.id == ui.selectedSeasonId }?.name ?: "切换季",
                        { onPanel(PlayerPanel.Seasons) },
                        icon = Glyph.Down,
                    )
                    if (ui.episodeError != null) {
                        Caption(ui.episodeError)
                        ActionButton("重试读取剧集", { ui.selectedSeasonId?.let(actions.selectSeason) })
                    } else if (ui.episodes.isEmpty()) Caption("暂无可选剧集")
                    ui.episodes.forEach { episode ->
                        ChoiceRow(
                            episode.label,
                            subtitle =
                                if (episode.id == ui.itemId) "正在播放"
                                else if (episode.played) "已看" else "",
                            selected = episode.id == ui.itemId,
                        ) {
                            onDismiss()
                            actions.selectEpisode(episode)
                        }
                    }
                }
                PlayerPanel.Seasons -> {
                    if (ui.seasons.isEmpty()) Caption("暂无季信息")
                    ui.seasons.forEach { season ->
                        ChoiceRow(season.name, selected = season.id == ui.selectedSeasonId) {
                            actions.selectSeason(season.id)
                            onPanel(PlayerPanel.Episodes)
                        }
                    }
                }
                PlayerPanel.Versions -> {
                    if (ui.versions.isEmpty()) Caption("暂无其他媒体版本")
                    ui.versions.forEach { version ->
                        ChoiceRow(version.title, selected = version.id == ui.sourceId) {
                            onDismiss()
                            actions.selectVersion(version.id)
                        }
                    }
                }
            }
        }
    }
}
