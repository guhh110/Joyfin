package dev.joyfin.ui.features.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.data.PlayerToolbarAction
import dev.joyfin.data.PlayerToolbarPreferences
import dev.joyfin.playback.PlayerActions
import dev.joyfin.playback.PlayerUiState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.theme.MotionTokens
import dev.joyfin.ui.theme.Palette

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
    ToolbarPreview(pinned)
    Caption("全部 ${PlayerToolbarPreferences.MAX_PINNED} 项都可固定，超出屏幕可横向浏览。调整立即保存，手机与 TV 分别记忆。")
    val ordered = (pinned + PlayerToolbarAction.entries).distinct()
    if (pinned.isEmpty()) {
        ToolbarSectionTitle("已固定", "0 项")
        Caption("还没有固定操作，从下方添加常用项。")
    }
    // One keyed collection keeps the launching control attached when pinning or reordering.
    ordered.forEachIndexed { position, action -> key(action) {
        val index = pinned.indexOf(action)
        if (position == 0 && index >= 0) ToolbarSectionTitle("已固定", "${pinned.size} 项 · 左右箭头排序")
        if (position == pinned.size) ToolbarSectionTitle("更多操作", "${ordered.size - pinned.size} 项")
        val shape = RoundedCornerShape(12.dp)
        Row(
            Modifier.fillMaxWidth()
                .background(Palette.Text.copy(alpha = if (index >= 0) .055f else .02f), shape)
                .border(.5.dp, Palette.Text.copy(alpha = if (index >= 0) .14f else .06f), shape)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FocusSurface(
                { actions.setToolbarPreferences(value.toggle(action)) },
                Modifier.weight(1f).heightIn(min = 64.dp).focusTarget(action.toggleFocusKey)
                    .semantics { contentDescription = "${action.label}，${if (index >= 0) "第 ${index + 1} 项，点击移入更多" else "点击固定到控制栏"}" },
                transparent = true, focusedScale = MotionTokens.FixedScale,
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(32.dp).background(Palette.Text.copy(alpha = .06f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center) {
                        AppIcon(action.glyph, Modifier.size(18.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(action.label, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Caption(if (index >= 0) "${index + 1} · 点击移除" else "点击固定", maxLines = 1)
                    }
                    if (index < 0) AppIcon(Glyph.Plus, Modifier.size(18.dp), color = Palette.Muted)
                }
            }
            if (index >= 0) {
                ToolbarOrderButton(action, before = true, enabled = index > 0) {
                    actions.setToolbarPreferences(value.move(action, -1))
                }
                ToolbarOrderButton(action, before = false, enabled = index < pinned.lastIndex) {
                    actions.setToolbarPreferences(value.move(action, 1))
                }
            }
        }
    } }
    if (pinned.size == ordered.size) {
        ToolbarSectionTitle("更多操作", "0 项")
        Caption("全部操作已固定，「更多」仍保留自定义和播放设置入口。")
    }
    ActionButton("恢复默认控制栏", { actions.setToolbarPreferences(PlayerToolbarPreferences()) },
        Modifier.fillMaxWidth().focusTarget("player:toolbar-reset"), icon = Glyph.Refresh,
        quiet = true, focusedScale = MotionTokens.FixedScale)
}

@Composable
private fun ToolbarSectionTitle(title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Caption(detail)
    }
}

@Composable
private fun ToolbarOrderButton(action: PlayerToolbarAction, before: Boolean, enabled: Boolean, onClick: () -> Unit) {
    FocusSurface(onClick,
        Modifier.size(width = 40.dp, height = 48.dp)
            .focusTarget("player:toolbar:${action.name}:${if (before) "before" else "after"}")
            .semantics { contentDescription = "${action.label}${if (before) "前移" else "后移"}" },
        enabled = enabled, transparent = true, focusedScale = MotionTokens.FixedScale,
    ) { AppIcon(if (before) Glyph.Back else Glyph.Next, Modifier.size(16.dp),
        color = Palette.Text.copy(alpha = if (enabled) .9f else .2f)) }
}

@Composable
private fun ToolbarPreview(pinned: List<PlayerToolbarAction>) {
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxWidth().background(Palette.Text.copy(alpha = .04f), shape)
        .border(.5.dp, Palette.Line, shape).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Caption("控制栏预览", color = Palette.Text)
            Caption("${pinned.size} / ${PlayerToolbarPreferences.MAX_PINNED}")
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            pinned.forEachIndexed { index, action -> key(action) {
                Column(Modifier.widthIn(min = 56.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppIcon(action.glyph, Modifier.size(20.dp))
                    Caption("${index + 1} ${action.label}", color = Palette.Text, maxLines = 1)
                }
            } }
            Column(Modifier.padding(start = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppIcon(Glyph.List, Modifier.size(20.dp), color = Palette.Muted)
                Caption("更多")
            }
        }
    }
}
