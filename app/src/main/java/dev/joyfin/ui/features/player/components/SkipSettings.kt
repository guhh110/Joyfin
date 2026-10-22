package dev.joyfin.ui.features.player.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.data.SkipPreferences
import dev.joyfin.playback.PlayerActions
import dev.joyfin.playback.PlayerUiState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.focusTarget

@Composable
internal fun SkipSettings(ui: PlayerUiState, actions: PlayerActions) {
    val nav = ui.navigation
    val prefs = nav.preferences
    Caption("优先使用服务器明确标记。呼出控制栏后可选择跳过；自动跳过需要手动开启。")
    if (nav.undoPositionMs != null) ActionButton("撤销跳过", actions.undoSkip, enabled = ui.canSeek && !ui.loading)
    else nav.offer?.let {
        ActionButton("跳过${it.label}", actions.skipSegment, primary = true,
            enabled = ui.canSeek && !ui.loading && !ui.subtitleApplying)
    }
    if (!nav.timelineAvailable) Caption("当前版本没有独立的服务器时间轴；可使用本剧时长设置。")
    else if (!nav.loading && nav.error == null)
        Caption(if (nav.serverMarks == 0) "服务器未提供有效的片头片尾标记" else "服务器提供了 ${nav.serverMarks} 段片头片尾标记")
    if (nav.canConfigure) {
        ChoiceRow("自动跳过", Modifier.focusTarget("player:skip-auto"),
            subtitle = if (prefs.automatic) "已开启 · 播放时自动跳过" else "关闭 · 仅显示跳过按钮", selected = prefs.automatic) {
            actions.setSkipPreferences(prefs.copy(automatic = !prefs.automatic))
        }
        Caption("以下时长按剧保存，仅在缺少对应服务器标记时使用。片头从开头计算，片尾从结尾倒算。")
        SkipDuration("片头", prefs.introSeconds) { actions.setSkipPreferences(prefs.copy(introSeconds = it)) }
        SkipDuration("片尾", prefs.outroSeconds) { actions.setSkipPreferences(prefs.copy(outroSeconds = it)) }
        ActionButton("恢复本剧默认设置", { actions.setSkipPreferences(SkipPreferences()) })
    } else Caption("当前视频使用服务器标记；按剧时长设置仅适用于剧集。")
    nav.error?.let { Caption(it) }
    ActionButton("重新读取片段标记", actions.retrySegments, loading = nav.loading,
        enabled = nav.timelineAvailable, icon = Glyph.Refresh)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkipDuration(title: String, seconds: Int, change: (Int) -> Unit) {
    Caption("$title：${if (seconds == 0) "未设置" else "$seconds 秒"}")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(-30, -1, 1, 30).forEach { delta ->
            ActionButton("${if (delta > 0) "+" else ""}$delta 秒", { change((seconds + delta).coerceIn(0, 600)) },
                Modifier.focusTarget("skip-duration:$title:$delta"), enabled = if (delta < 0) seconds > 0 else seconds < 600)
        }
    }
}
