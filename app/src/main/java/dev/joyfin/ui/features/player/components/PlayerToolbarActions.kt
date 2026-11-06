package dev.joyfin.ui.features.player.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.joyfin.data.PlaybackQuality
import dev.joyfin.data.PlayerToolbarAction
import dev.joyfin.playback.PlayerActions
import dev.joyfin.playback.PlayerUiState
import dev.joyfin.ui.components.Glyph
import dev.joyfin.ui.components.timeLabel
import dev.joyfin.ui.interaction.focusTarget
import java.util.Locale

internal val PlayerToolbarAction.focusKey get() = "player:${name.lowercase(Locale.ROOT).replace('_', '-')}"
internal val PlayerToolbarAction.moreFocusKey get() = "player:more:$name"
internal val PlayerToolbarAction.toggleFocusKey get() = "player:toolbar:$name:toggle"

/** A nested menu restores its launching row instead of requesting focus during ordinary redraws. */
internal fun PlayerPanel.returnFocusFor(destination: PlayerPanel): String? = when (this) {
    PlayerPanel.More -> when (destination) {
        PlayerPanel.Toolbar -> "player:more-customize"
        PlayerPanel.Settings -> "player:more-settings"
        else -> PlayerToolbarAction.entries.firstOrNull { it.panel == destination }?.moreFocusKey
    }
    PlayerPanel.Settings -> when (destination) {
        PlayerPanel.Quality -> "player:settings-quality"
        PlayerPanel.Subtitle -> "player:settings-subtitle"
        PlayerPanel.VideoScale -> "player:settings-video-scale"
        PlayerPanel.Audio -> "player:settings-audio"
        PlayerPanel.AudioOutput -> "player:settings-audio-output"
        PlayerPanel.Speed -> "player:settings-speed"
        PlayerPanel.Chapters -> "player:settings-chapters"
        PlayerPanel.Skip -> "player:settings-skip"
        PlayerPanel.Advanced -> "player:settings-advanced"
        else -> null
    }
    PlayerPanel.Advanced -> when (destination) {
        PlayerPanel.Versions -> "player:settings-versions"
        PlayerPanel.PlaybackMode -> "player:settings-playback-mode"
        PlayerPanel.Toolbar -> "player:settings-toolbar"
        PlayerPanel.Diagnostics -> "player:settings-diagnostics"
        else -> null
    }
    PlayerPanel.Subtitle -> when (destination) {
        PlayerPanel.SubtitleStyle -> "player:subtitle-style"
        PlayerPanel.SubtitleOffset -> "player:local-subtitle"
        else -> null
    }
    PlayerPanel.Episodes -> if (destination == PlayerPanel.Seasons) "player:episode-season" else null
    else -> null
}

internal val PlayerToolbarAction.panel: PlayerPanel? get() = when (this) {
    PlayerToolbarAction.SUBTITLE -> PlayerPanel.Subtitle
    PlayerToolbarAction.SPEED -> PlayerPanel.Speed
    PlayerToolbarAction.EPISODES -> PlayerPanel.Episodes
    PlayerToolbarAction.QUALITY -> PlayerPanel.Quality
    PlayerToolbarAction.AUDIO -> PlayerPanel.Audio
    PlayerToolbarAction.CHAPTERS -> PlayerPanel.Chapters
    PlayerToolbarAction.VERSIONS -> PlayerPanel.Versions
    PlayerToolbarAction.VIDEO_SCALE -> PlayerPanel.VideoScale
    else -> null
}

internal val PlayerToolbarAction.glyph: Glyph get() = when (this) {
    PlayerToolbarAction.SUBTITLE -> Glyph.Subtitle
    PlayerToolbarAction.SPEED, PlayerToolbarAction.MARK_INTRO, PlayerToolbarAction.MARK_OUTRO -> Glyph.Clock
    PlayerToolbarAction.EPISODES, PlayerToolbarAction.CHAPTERS -> Glyph.List
    PlayerToolbarAction.QUALITY, PlayerToolbarAction.VIDEO_SCALE -> Glyph.Film
    PlayerToolbarAction.AUDIO -> Glyph.Audio
    PlayerToolbarAction.NEXT -> Glyph.Next
    PlayerToolbarAction.VERSIONS -> Glyph.Library
    PlayerToolbarAction.UNDO_MARK -> Glyph.Back
}

internal fun PlayerToolbarAction.enabled(ui: PlayerUiState): Boolean {
    val playbackAvailable = !ui.loading && ui.error == null && !ui.subtitleApplying &&
        !ui.subtitleSearchOpen && ui.nextSeconds == 0
    return when (this) {
        PlayerToolbarAction.EPISODES -> ui.episodes.isNotEmpty() || ui.seasons.isNotEmpty()
        PlayerToolbarAction.AUDIO -> ui.audio.isNotEmpty()
        PlayerToolbarAction.NEXT -> playbackAvailable && ui.nextItem != null
        PlayerToolbarAction.CHAPTERS -> playbackAvailable && ui.canSeek && ui.navigation.timelineAvailable &&
            ui.navigation.chapters.isNotEmpty()
        PlayerToolbarAction.VERSIONS -> ui.versions.isNotEmpty()
        PlayerToolbarAction.MARK_INTRO, PlayerToolbarAction.MARK_OUTRO -> playbackAvailable &&
            ui.navigation.canConfigure && ui.canSeek && ui.seekPreview?.active != true && ui.durationMs > 0
        PlayerToolbarAction.UNDO_MARK -> playbackAvailable && ui.navigation.canUndoMark
        else -> true
    }
}

internal fun PlayerToolbarAction.detail(ui: PlayerUiState): String = when (this) {
    PlayerToolbarAction.SUBTITLE -> ui.localSubtitle?.label
        ?: ui.subtitles.firstOrNull { it.index == ui.subtitleIndex }?.title ?: "字幕关闭 · 可搜索或选择字幕"
    PlayerToolbarAction.SPEED -> "${ui.speed.toString().removeSuffix(".0")}×"
    PlayerToolbarAction.EPISODES -> if (enabled(ui)) "切换集数或季" else "当前视频没有可选剧集"
    PlayerToolbarAction.QUALITY -> if (ui.bitrate == PlaybackQuality.ORIGINAL) "原画 · 不限码率"
        else "${ui.bitrate / 1_000_000f} Mbps 上限"
    PlayerToolbarAction.AUDIO -> ui.audio.firstOrNull { it.index == ui.audioIndex }?.title ?: "没有可选音轨"
    PlayerToolbarAction.NEXT -> ui.nextItem?.label ?: "暂无下一集"
    PlayerToolbarAction.CHAPTERS -> "${ui.navigation.chapters.size} 个章节"
    PlayerToolbarAction.VERSIONS -> ui.versions.firstOrNull { it.id == ui.sourceId }?.title ?: "当前媒体版本"
    PlayerToolbarAction.VIDEO_SCALE -> ui.videoScaleMode.label
    PlayerToolbarAction.MARK_INTRO -> "以当前播放位置为片头结束点 · ${timeLabel(ui.positionMs)}"
    PlayerToolbarAction.MARK_OUTRO -> "以当前播放位置为片尾开始点 · ${timeLabel(ui.positionMs)}"
    PlayerToolbarAction.UNDO_MARK -> if (ui.navigation.canUndoMark) "恢复上次标记前的设置" else "暂无可撤销的标记"
}

internal fun PlayerToolbarAction.perform(
    actions: PlayerActions,
    onPanel: (PlayerPanel) -> Unit,
    onDirectAction: () -> Unit,
) {
    val destination = panel
    if (destination != null) onPanel(destination)
    else {
        when (this) {
            PlayerToolbarAction.NEXT -> actions.nextNow()
            PlayerToolbarAction.MARK_INTRO -> actions.markIntroEnd()
            PlayerToolbarAction.MARK_OUTRO -> actions.markOutroStart()
            PlayerToolbarAction.UNDO_MARK -> actions.undoMark()
            else -> return
        }
        onDirectAction()
    }
}

@Composable
internal fun PlayerToolbarButton(
    action: PlayerToolbarAction,
    ui: PlayerUiState,
    actions: PlayerActions,
    onPanel: (PlayerPanel) -> Unit,
    onInteraction: () -> Unit,
) {
    val label = when (action) {
        PlayerToolbarAction.SPEED -> action.detail(ui)
        PlayerToolbarAction.QUALITY -> if (ui.bitrate == PlaybackQuality.ORIGINAL) "原画" else "画质"
        else -> action.label
    }
    val selected = when (action) {
        PlayerToolbarAction.MARK_INTRO -> ui.navigation.preferences.introEndMs != null
        PlayerToolbarAction.MARK_OUTRO -> ui.navigation.preferences.outroDurationMs != null
        else -> false
    }
    PlayerChromeButton(
        "${action.label}：${action.detail(ui)}",
        action.glyph,
        {
            onInteraction()
            action.perform(actions, onPanel, {})
        },
        Modifier.focusTarget(action.focusKey),
        text = label,
        enabled = action.enabled(ui),
        selected = selected,
    )
}
