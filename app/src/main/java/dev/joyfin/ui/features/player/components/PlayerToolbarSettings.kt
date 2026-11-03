package dev.joyfin.ui.features.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.data.PlayerToolbarAction
import dev.joyfin.data.PlayerToolbarPreferences
import dev.joyfin.playback.PlayerActions
import dev.joyfin.playback.PlayerUiState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.theme.MotionTokens

@Composable
internal fun PlayerMoreContent(
    ui: PlayerUiState,
    actions: PlayerActions,
    onPanel: (PlayerPanel) -> Unit,
    onDismiss: () -> Unit,
) {
    ChoiceRow("自定义控制栏", Modifier.focusTarget("player:more-customize"),
        subtitle = "选择常用操作并调整顺序", chevron = true,
        leading = { AppIcon(Glyph.List, Modifier.size(20.dp)) }) { onPanel(PlayerPanel.Toolbar) }
    ui.toolbar.moreActions().forEach { action -> key(action) {
        ChoiceRow(
            action.label, Modifier.focusTarget(action.moreFocusKey), subtitle = action.detail(ui),
            enabled = action.enabled(ui), chevron = action.panel != null,
            leading = { AppIcon(action.glyph, Modifier.size(20.dp)) },
        ) { action.perform(actions, onPanel, onDismiss) }
    } }
    ChoiceRow("全部播放设置", Modifier.focusTarget("player:more-settings"), chevron = true,
        leading = { AppIcon(Glyph.Settings, Modifier.size(20.dp)) }) { onPanel(PlayerPanel.Settings) }
}

@Composable
internal fun PlayerToolbarSettings(ui: PlayerUiState, actions: PlayerActions) {
    val value = ui.toolbar
    val pinned = value.pinned
    Caption("最多固定 ${PlayerToolbarPreferences.MAX_PINNED} 项，其余操作收进「更多」。调整立即保存，手机与 TV 分别记忆。")
    Caption("已固定 ${pinned.size} / ${PlayerToolbarPreferences.MAX_PINNED}")
    (pinned + PlayerToolbarAction.entries).distinct().forEach { action -> key(action) {
        val index = pinned.indexOf(action)
        ChoiceRow(
            if (index >= 0) "${index + 1} · ${action.label}" else action.label,
            Modifier.focusTarget(action.toggleFocusKey),
            subtitle = if (index >= 0) "已固定 · 点击收进更多" else "在更多中 · 点击固定",
            selected = index >= 0,
            enabled = index >= 0 || pinned.size < PlayerToolbarPreferences.MAX_PINNED,
            leading = { AppIcon(action.glyph, Modifier.size(20.dp)) },
        ) { actions.setToolbarPreferences(value.toggle(action)) }
        if (index >= 0) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton("前移", { actions.setToolbarPreferences(value.move(action, -1)) },
                Modifier.focusTarget("player:toolbar:${action.name}:before"), enabled = index > 0,
                focusedScale = MotionTokens.FixedScale)
            ActionButton("后移", { actions.setToolbarPreferences(value.move(action, 1)) },
                Modifier.focusTarget("player:toolbar:${action.name}:after"), enabled = index < pinned.lastIndex,
                focusedScale = MotionTokens.FixedScale)
        }
    } }
    ActionButton("恢复默认控制栏", { actions.setToolbarPreferences(PlayerToolbarPreferences()) },
        Modifier.focusTarget("player:toolbar-reset"), icon = Glyph.Refresh, focusedScale = MotionTokens.FixedScale)
}
