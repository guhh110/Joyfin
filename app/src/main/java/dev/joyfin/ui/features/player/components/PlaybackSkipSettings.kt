package dev.joyfin.ui.features.player.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.joyfin.playback.PlayerActions
import dev.joyfin.playback.PlayerUiState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.focusTarget

/** Management lives in the main playback settings; points are marked directly on the video. */
@Composable
internal fun PlaybackSkipSettings(ui: PlayerUiState, actions: PlayerActions) {
    val nav = ui.navigation
    val prefs = nav.preferences
    SectionTitle("片头与片尾")
    if (nav.canConfigure) {
        ChoiceRow(
            "自动跳过", Modifier.focusTarget("player:skip-auto"),
            subtitle = if (prefs.automatic) "已开启 · 播放时自动跳过" else "已关闭 · 仅提示跳过",
            selected = prefs.automatic,
        ) { actions.setSkipPreferences(prefs.copy(automatic = !prefs.automatic)) }
        ChoiceRow(
            "片头结束", Modifier.focusTarget("player:skip-clear-intro"),
            subtitle = if (prefs.introDurationMs > 0)
                "${timeLabel(prefs.introDurationMs)} · 点击清除本地设置" else "未设置 · 使用服务器或章节标记",
            enabled = prefs.introDurationMs > 0,
        ) { actions.setSkipPreferences(prefs.copy(introSeconds = 0, introEndMs = null)) }
        ChoiceRow(
            "片尾时长", Modifier.focusTarget("player:skip-clear-outro"),
            subtitle = if (prefs.effectiveOutroDurationMs > 0)
                "距结尾 ${timeLabel(prefs.effectiveOutroDurationMs)} · 点击清除本地设置" else "未设置 · 使用服务器或章节标记",
            enabled = prefs.effectiveOutroDurationMs > 0,
        ) { actions.setSkipPreferences(prefs.copy(outroSeconds = 0, outroDurationMs = null)) }
        Caption("定位到目标画面后，点击控制栏的「片头结束」或「片尾开始」。手动标记优先使用；片尾按剩余时长保存，剧集按整剧沿用，电影单独保存。")
    } else Caption("当前视频使用服务器或章节标记。")
    if (!nav.timelineAvailable) Caption(if (nav.canConfigure)
        "当前版本不使用服务器时间轴，可在控制栏手动标记。" else "当前版本没有独立的服务器时间轴。")
    nav.error?.let { Caption(it) }
    if (nav.timelineAvailable)
        ActionButton("重新读取服务器标记", actions.retrySegments,
            Modifier.focusTarget("player:skip-retry"), loading = nav.loading, icon = Glyph.Refresh)
}
