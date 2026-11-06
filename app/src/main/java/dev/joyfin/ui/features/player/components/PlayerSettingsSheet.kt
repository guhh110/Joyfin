package dev.joyfin.ui.features.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.joyfin.artwork.ArtworkCacheIdentity
import dev.joyfin.playback.PlayerActions
import dev.joyfin.playback.PlayerUiState
import dev.joyfin.data.VideoScaleMode
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.RememberScreenFocus
import dev.joyfin.ui.interaction.LocalScreenFocus
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.player.PlaybackDiagnosticsSheet
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.theme.Palette
import kotlinx.coroutines.flow.first

enum class PlayerPanel(val title: String) {
    More("更多操作"),
    Toolbar("自定义控制栏"),
    Settings("播放设置"),
    Advanced("高级设置"),
    Skip("片头与片尾"),
    PlaybackMode("播放方式"),
    Quality("画质"),
    VideoScale("画面缩放"),
    Audio("音轨"),
    AudioOutput("音频输出"),
    Subtitle("字幕"),
    SubtitleStyle("字幕样式"),
    SubtitleOffset("字幕时间偏移"),
    Speed("播放速度"),
    Episodes("选集"),
    Seasons("选择季"),
    Versions("媒体版本"),
    Diagnostics("播放诊断"),
    Chapters("章节"),
}

@Composable
fun PlayerSettingsSheet(
    panel: PlayerPanel,
    ui: PlayerUiState,
    actions: PlayerActions,
    onPanel: (PlayerPanel) -> Unit,
    onDismiss: () -> Unit,
    initialFocus: String = panel.initialFocus(ui),
    imageAuthorization: String = "",
    imageCacheNamespace: String = "",
) {
    if (panel == PlayerPanel.Diagnostics) {
        PlaybackDiagnosticsSheet(onDismiss)
        return
    }
    if (panel == PlayerPanel.Episodes || panel == PlayerPanel.Seasons) {
        PlayerEpisodeSheet(panel, ui, actions, onPanel, onDismiss, initialFocus)
        return
    }
    key(panel) {
        RememberScreenFocus(initialFocus) {
            Sheet(panel.title, onDismiss, side = true, preferredWidth = 420.dp) {
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
                    PlayerPanel.More -> PlayerMoreContent(ui, actions, onPanel, onDismiss)
                    PlayerPanel.Toolbar -> PlayerToolbarSettings(ui, actions)
                    PlayerPanel.Settings -> PlayerSettingsContent(ui, onPanel)
                    PlayerPanel.Advanced -> PlayerAdvancedSettings(ui, onPanel)
                    PlayerPanel.Skip -> PlaybackSkipSettings(ui, actions)
                    PlayerPanel.PlaybackMode -> listOf(false to "自动", true to "兼容转码").forEach { (compatible, label) ->
                        ChoiceRow(label, Modifier.focusTarget(panel.choiceKey(compatible)), selected = ui.compatible == compatible) {
                            onDismiss()
                            if (ui.compatible != compatible) actions.selectCompatibility(compatible)
                        }
                    }
                    PlayerPanel.Quality -> {
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
                                leading = { PlayerPanelIcon(Glyph.Audio) },
                                modifier = Modifier.focusTarget(panel.choiceKey(track.index)),
                            ) {
                                onDismiss()
                                actions.selectAudio(track.index)
                            }
                        }
                    }
                    PlayerPanel.VideoScale -> {
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
                    PlayerPanel.SubtitleStyle -> PlayerSubtitleStyleContent(ui, actions)
                    PlayerPanel.Subtitle -> PlayerSubtitleContent(ui, actions, onPanel, onDismiss)
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
                    PlayerPanel.Episodes, PlayerPanel.Seasons -> Unit
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
                            ChoiceRow(
                                chapter.title,
                                Modifier.focusTarget(panel.choiceKey(chapter.positionMs)),
                                subtitle = dev.joyfin.playback.diagnosticTime(chapter.positionMs),
                                selected = current == chapter,
                                enabled = ui.canSeek && !ui.loading && !ui.subtitleApplying,
                                leading = {
                                    ChapterThumb(chapter.imageUrl, imageAuthorization, imageCacheNamespace)
                                },
                            ) {
                                onDismiss()
                                actions.selectChapter(chapter.positionMs)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterThumb(url: String?, authorization: String, cacheNamespace: String) {
    val context = LocalContext.current
    Box(
        Modifier
            .size(width = 112.dp, height = 63.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Palette.Surface),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            AppIcon(Glyph.Film, Modifier.size(20.dp), color = Palette.Muted)
        } else {
            val key = ArtworkCacheIdentity.key(cacheNamespace, url)
            val request = ImageRequest.Builder(context)
                .data(url)
                .addHeader("Authorization", authorization)
                .memoryCacheKey(key)
                .diskCacheKey(key)
                .crossfade(false)
                .build()
            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
