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
        Caption("保存范围：${nav.preferenceScope}", maxLines = 2)
        if (nav.canSharePreferences) ChoiceRow(
            "按版本分别记忆", Modifier.focusTarget("player:skip-version-scope"),
            subtitle = if (nav.separateVersions) "已开启 · 同季同名版本沿用，不同版本分别设置"
                else "已关闭 · 本季全部集数共享片头片尾设置",
            selected = nav.separateVersions,
        ) { actions.separateSkipVersions(!nav.separateVersions) }
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
        ActionButton("撤销上次标记", actions.undoMark, Modifier.focusTarget("player:settings-undo-mark"),
            icon = Glyph.Back, enabled = nav.canUndoMark && !ui.loading && !ui.subtitleApplying)
        nav.legacyPreferences?.let { legacy ->
            val intro = if (legacy.introDurationMs > 0) timeLabel(legacy.introDurationMs) else "未设置"
            val outro = if (legacy.effectiveOutroDurationMs > 0) timeLabel(legacy.effectiveOutroDurationMs) else "未设置"
            Caption("旧设置仍保留：片头 $intro，片尾 $outro，自动跳过${if (legacy.automatic) "开启" else "关闭"}。")
            ActionButton("沿用旧设置到当前范围", actions.useLegacySkipPreferences,
                Modifier.focusTarget("player:skip-use-legacy"), icon = Glyph.Clock)
        }
        Caption("定位到目标画面后，点击控制栏的「片头结束」或「片尾开始」。剧集默认按季共享，片尾按剩余时长计算；电影按版本保存。只有不同时长的版本需要单独设置时，再开启按版本记忆。")
        if (nav.separateVersions) Caption("版本按服务器提供的名称区分；名称含每集文件名或无法识别时，请关闭按版本记忆以跨集共享。")
    } else Caption("当前视频使用服务器或章节标记。")
    if (!nav.timelineAvailable) Caption(if (nav.canConfigure)
        "当前版本不使用服务器时间轴，可在控制栏手动标记。" else "当前版本没有独立的服务器时间轴。")
    nav.error?.let { Caption(it) }
    if (nav.timelineAvailable)
        ActionButton("重新读取服务器标记", actions.retrySegments,
            Modifier.focusTarget("player:skip-retry"), loading = nav.loading, icon = Glyph.Refresh)
}
