package dev.joyfin.ui.features.player.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import dev.joyfin.playback.PlayerActions
import dev.joyfin.playback.PlayerUiState
import dev.joyfin.data.VideoScaleMode
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.RememberScreenFocus
import dev.joyfin.ui.interaction.LocalScreenFocus
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.player.PlaybackDiagnosticsSheet
import dev.joyfin.ui.interaction.focusTarget
import kotlinx.coroutines.flow.first

enum class PlayerPanel(val title: String) {
    Settings("播放设置"),
    Quality("画质"),
    VideoScale("画面缩放"),
    Audio("音轨"),
    AudioOutput("音频输出"),
    Subtitle("字幕"),
    SubtitleOffset("字幕时间偏移"),
    Speed("播放速度"),
    Episodes("选集"),
    Seasons("选择季"),
    Versions("媒体版本"),
    Diagnostics("播放诊断"),
    Chapters("章节"),
    Skip("片头与片尾"),
}

@Composable
fun PlayerSettingsSheet(
    panel: PlayerPanel,
    ui: PlayerUiState,
    actions: PlayerActions,
    onPanel: (PlayerPanel) -> Unit,
    onDismiss: () -> Unit,
    initialFocus: String = panel.initialFocus(ui),
) {
    if (panel == PlayerPanel.Diagnostics) {
        PlaybackDiagnosticsSheet(onDismiss)
        return
    }
    key(panel) {
        RememberScreenFocus(initialFocus) {
            Sheet(panel.title, onDismiss, side = true) {
                val window = LocalWindowInfo.current
                val focus = LocalScreenFocus.current
                val tv = LocalDeviceLayout.current.television
                LaunchedEffect(Unit) {
                    if (tv) {
                        snapshotFlow { window.isWindowFocused }.first { it }
                        withFrameNanos {}
                        focus?.rememberTarget?.invoke(initialFocus)
                        focus?.requesters?.get(initialFocus)?.requestFocus()
                    }
                }
                when (panel) {
                    PlayerPanel.Settings -> {
                        ChoiceRow(
                            "画质",
                            subtitle =
                                if (ui.bitrate == dev.joyfin.data.PlaybackQuality.ORIGINAL)
                                    "原画 · 不限码率"
                                else "${ui.bitrate/1_000_000} Mbps 上限",
                        ) {
                            onPanel(PlayerPanel.Quality)
                        }
                        ChoiceRow(
                            "字幕",
                            subtitle =
                                if (ui.localSubtitle != null) ui.localSubtitle.label
                                else if (ui.subtitleIndex < 0) "关闭"
                                else
                                    ui.subtitles
                                        .firstOrNull { it.index == ui.subtitleIndex }
                                        ?.title
                                        .orEmpty(),
                        ) {
                            onPanel(PlayerPanel.Subtitle)
                        }
                        ChoiceRow("画面缩放", Modifier.focusTarget("player:settings-video-scale"),
                            subtitle = ui.videoScaleMode.label) { onPanel(PlayerPanel.VideoScale) }
                        ChoiceRow(
                            "音轨",
                            subtitle =
                                ui.audio.firstOrNull { it.index == ui.audioIndex }?.title ?: "默认",
                        ) {
                            onPanel(PlayerPanel.Audio)
                        }
                        ChoiceRow("播放速度", subtitle = "${ui.speed}×") { onPanel(PlayerPanel.Speed) }
                        ChoiceRow("音频输出", subtitle = ui.audioOutputMode.label) { onPanel(PlayerPanel.AudioOutput) }
                        ChoiceRow("媒体版本") { onPanel(PlayerPanel.Versions) }
                        ChoiceRow("章节", Modifier.focusTarget("player:settings-chapters"),
                            subtitle = "${ui.navigation.chapters.size} 个章节") { onPanel(PlayerPanel.Chapters) }
                        ChoiceRow("片头与片尾", Modifier.focusTarget("player:settings-skip"),
                            subtitle = if (ui.navigation.preferences.automatic) "自动跳过已开启" else "提示跳过，可按剧设置时长") { onPanel(PlayerPanel.Skip) }
                        ChoiceRow(
                            "播放方式",
                            subtitle = if (ui.compatible) "兼容转码 · 点击恢复自动" else "自动 · 点击使用兼容转码",
                        ) {
                            onDismiss()
                            actions.selectCompatibility(!ui.compatible)
                        }
                        ChoiceRow("播放诊断", Modifier.focusTarget("player:settings-diagnostics"),
                            subtitle = "实际播放格式、转码原因与错误记录") { onPanel(PlayerPanel.Diagnostics) }
                    }
                    PlayerPanel.Quality -> {
                        Caption("选择码率上限，实际画质由媒体与服务器决定。")
                        qualityChoices.forEach { (rate, name) ->
                            ChoiceRow(
                                name,
                                subtitle =
                                    if (rate == dev.joyfin.data.PlaybackQuality.ORIGINAL) "不限码率"
                                    else "${rate/1_000_000f} Mbps",
                                selected = ui.bitrate == rate,
                                modifier = Modifier.focusTarget(panel.choiceKey(rate)),
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
                                modifier = Modifier.focusTarget(panel.choiceKey(track.index)),
                            ) {
                                onDismiss()
                                actions.selectAudio(track.index)
                            }
                        }
                    }
                    PlayerPanel.VideoScale -> {
                        Caption("此设备记住选择，切换立即生效。")
                        VideoScaleMode.entries.forEach { mode ->
                            ChoiceRow(mode.label, Modifier.focusTarget(panel.choiceKey(mode.name)),
                                subtitle = mode.description, selected = mode == ui.videoScaleMode) {
                                actions.setVideoScaleMode(mode)
                                onDismiss()
                            }
                        }
                    }
                    PlayerPanel.AudioOutput -> dev.joyfin.data.AudioOutputMode.entries.forEach { mode ->
                        ChoiceRow(mode.label, Modifier.focusTarget(panel.choiceKey(mode.name)),
                            subtitle = if (mode == dev.joyfin.data.AudioOutputMode.AUTO)
                                "优先编码输出，不可用时自动解码或转换"
                                else "关闭编码直通，使用本机解码或服务器转换",
                            selected = mode == ui.audioOutputMode) {
                            onDismiss()
                            actions.setAudioOutputMode(mode)
                        }
                    }
                    PlayerPanel.SubtitleOffset -> dev.joyfin.ui.features.subtitles.SubtitleOffsetContent(ui, actions)
                    PlayerPanel.Subtitle -> {
                        ActionButton("搜索字幕", actions.searchSubtitles,
                            Modifier.focusTarget("player:subtitle-search"), icon = Glyph.Search,
                            enabled = !ui.loading && ui.error == null && !ui.subtitleApplying,
                            focusedScale = dev.joyfin.ui.theme.MotionTokens.FixedScale)
                        ui.localSubtitle?.let { local ->
                            ChoiceRow(local.label, Modifier.focusTarget("player:local-subtitle"),
                                subtitle = "本地字幕 · ${local.offsetMs / 1000.0} 秒偏移", selected = true) {
                                onPanel(PlayerPanel.SubtitleOffset)
                            }
                        }
                        ui.localSubtitles.filter { it.id != ui.localSubtitle?.id }.forEach { file ->
                            ChoiceRow(file.label, Modifier.focusTarget("player:local-file:${file.id}"), subtitle = "本地字幕") {
                                onDismiss()
                                actions.selectLocalFile(file.id)
                            }
                        }
                        ui.localSubtitleError?.let { Caption(it) }
                        ChoiceRow(
                            "关闭",
                            subtitle = "不显示字幕",
                            selected = ui.subtitleIndex < 0 && ui.localSubtitle == null,
                            modifier = Modifier.focusTarget(panel.choiceKey(-1)),
                        ) {
                            onDismiss()
                            actions.selectSubtitle(-1)
                        }
                        ui.subtitles.forEach { track ->
                            ChoiceRow(
                                track.title,
                                subtitle = track.detail,
                                selected = track.index == ui.subtitleIndex && ui.localSubtitle == null,
                                modifier = Modifier.focusTarget(panel.choiceKey(track.index)),
                            ) {
                                onDismiss()
                                actions.selectSubtitle(track.index)
                            }
                        }
                    }
                    PlayerPanel.Speed ->
                        speedChoices.forEach { speed ->
                            ChoiceRow(
                                "${speed}×",
                                selected = ui.speed == speed,
                                modifier = Modifier.focusTarget(panel.choiceKey(speed)),
                            ) {
                                onDismiss()
                                actions.speed(speed)
                            }
                        }
                    PlayerPanel.Episodes -> {
                        ActionButton(
                            ui.seasons.firstOrNull { it.id == ui.selectedSeasonId }?.name ?: "切换季",
                            { onPanel(PlayerPanel.Seasons) },
                            modifier = Modifier.focusTarget("player:episode-season"),
                            icon = Glyph.Down,
                        )
                        Caption(ui.nextPreparationError ?: ui.nextItem?.let { "下一集：${it.label}" }
                            ?: if (ui.nextPreparing) " " else "暂无下一集")
                        ActionButton("准备下一集", actions.retryNext,
                            Modifier.focusTarget("player:next-preparation"),
                            enabled = ui.nextPreparationError != null && !ui.nextPreparing,
                            loading = ui.nextPreparing, maxLines = 1)
                        if (ui.episodeError != null) {
                            Caption(ui.episodeError)
                            ActionButton(
                                "重试读取剧集",
                                { ui.selectedSeasonId?.let(actions.selectSeason) },
                            )
                        } else if (ui.episodes.isEmpty()) Caption("暂无可选剧集")
                        ui.episodes.forEach { episode ->
                            ChoiceRow(
                                episode.label,
                                subtitle =
                                    if (episode.id == ui.itemId) "正在播放"
                                    else if (episode.played) "已看" else "",
                                selected = episode.id == ui.itemId,
                                modifier = Modifier.focusTarget(panel.choiceKey(episode.id)),
                            ) {
                                onDismiss()
                                actions.selectEpisode(episode)
                            }
                        }
                    }
                    PlayerPanel.Seasons -> {
                        if (ui.seasons.isEmpty()) Caption("暂无季信息")
                        ui.seasons.forEach { season ->
                            ChoiceRow(
                                season.name,
                                selected = season.id == ui.selectedSeasonId,
                                modifier = Modifier.focusTarget(panel.choiceKey(season.id)),
                            ) {
                                actions.selectSeason(season.id)
                                onPanel(PlayerPanel.Episodes)
                            }
                        }
                    }
                    PlayerPanel.Versions -> {
                        if (ui.versions.isEmpty()) Caption("暂无其他媒体版本")
                        ui.versions.forEach { version ->
                            ChoiceRow(
                                version.title,
                                selected = version.id == ui.sourceId,
                                modifier = Modifier.focusTarget(panel.choiceKey(version.id)),
                            ) {
                                onDismiss()
                                actions.selectVersion(version.id)
                            }
                        }
                    }
                    PlayerPanel.Diagnostics -> Unit
                    PlayerPanel.Chapters -> {
                        if (!ui.navigation.timelineAvailable) Caption("当前媒体版本没有独立的章节时间轴")
                        else if (ui.navigation.chapters.isEmpty()) Caption("服务器没有提供此视频的章节")
                        val current = ui.navigation.chapters.lastOrNull { it.positionMs <= ui.positionMs }
                        ui.navigation.chapters.forEach { chapter ->
                            ChoiceRow(chapter.title, Modifier.focusTarget(panel.choiceKey(chapter.positionMs)),
                                subtitle = dev.joyfin.playback.diagnosticTime(chapter.positionMs), selected = current == chapter,
                                enabled = ui.canSeek && !ui.loading && !ui.subtitleApplying) {
                                onDismiss()
                                actions.selectChapter(chapter.positionMs)
                            }
                        }
                    }
                    PlayerPanel.Skip -> SkipSettings(ui, actions)
                }
            }
        }
    }
}
