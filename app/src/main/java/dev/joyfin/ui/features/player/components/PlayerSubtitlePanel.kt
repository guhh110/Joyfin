package dev.joyfin.ui.features.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.data.SubtitleSize
import dev.joyfin.playback.PlayerActions
import dev.joyfin.playback.PlayerUiState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.theme.MotionTokens
import dev.joyfin.ui.theme.Palette

@Composable
internal fun PlayerSubtitleContent(
    ui: PlayerUiState,
    actions: PlayerActions,
    onPanel: (PlayerPanel) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SubtitleTool("搜索字幕", Glyph.Search, actions.searchSubtitles,
                Modifier.weight(1f).focusTarget("player:subtitle-search"),
                enabled = !ui.loading && ui.error == null && !ui.subtitleApplying)
            SubtitleTool("字幕样式", Glyph.Settings, { onPanel(PlayerPanel.SubtitleStyle) },
                Modifier.weight(1f).focusTarget("player:subtitle-style"))
        }
        SubtitleDivider()
        SubtitleOptionRow("关闭字幕", Modifier.focusTarget(PlayerPanel.Subtitle.choiceKey(-1)),
            isSelected = ui.subtitleIndex < 0 && ui.localSubtitle == null) {
            onDismiss()
            actions.selectSubtitle(-1)
        }
        val local = ui.localSubtitle
        if (local != null || ui.localSubtitles.isNotEmpty()) {
            Caption("本地字幕", Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp))
            local?.let {
                SubtitleOptionRow(it.label, Modifier.focusTarget("player:local-subtitle"),
                    detail = "偏移 ${it.offsetMs / 1000.0} 秒", isSelected = true, chevron = true) {
                    onPanel(PlayerPanel.SubtitleOffset)
                }
            }
            ui.localSubtitles.filter { it.id != local?.id }.forEach { file -> key(file.id) {
                SubtitleOptionRow(file.label, Modifier.focusTarget("player:local-file:${file.id}")) {
                    onDismiss()
                    actions.selectLocalFile(file.id)
                }
            } }
            if (ui.subtitles.isNotEmpty())
                Caption("媒体字幕", Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp))
        }
        ui.subtitles.forEach { track -> key(track.index) {
            SubtitleOptionRow(track.title, Modifier.focusTarget(PlayerPanel.Subtitle.choiceKey(track.index)),
                detail = track.detail, isSelected = track.index == ui.subtitleIndex && local == null) {
                onDismiss()
                actions.selectSubtitle(track.index)
            }
        } }
        ui.localSubtitleError?.let { Caption(it, Modifier.padding(horizontal = 12.dp)) }
    }
}

@Composable
internal fun PlayerSubtitleStyleContent(ui: PlayerUiState, actions: PlayerActions) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Caption("字幕字号", Modifier.padding(start = 12.dp, bottom = 6.dp))
        SubtitleSize.entries.forEach { size ->
            SubtitleOptionRow(size.label, Modifier.focusTarget(PlayerPanel.SubtitleStyle.choiceKey(size.name)),
                isSelected = size == ui.subtitleSize) { actions.setSubtitleSize(size) }
        }
        SubtitleDivider()
        FocusSurface({ actions.setSubtitleBackground(!ui.subtitleBackground) },
            Modifier.fillMaxWidth().heightIn(min = 64.dp).focusTarget("player:subtitle-background")
                .semantics {
                    role = Role.Switch
                    toggleableState = ToggleableState(ui.subtitleBackground)
                },
            transparent = true, focusedScale = MotionTokens.FixedScale) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("字幕背景", Modifier.weight(1f), color = Palette.Text, fontSize = 15.sp)
                Box(Modifier.size(38.dp, 22.dp)
                    .background(if (ui.subtitleBackground) Palette.Text else Palette.Elevated, CircleShape)
                    .padding(3.dp),
                    contentAlignment = if (ui.subtitleBackground) Alignment.CenterEnd else Alignment.CenterStart) {
                    Box(Modifier.size(16.dp).background(
                        if (ui.subtitleBackground) Palette.Background else Palette.Muted, CircleShape))
                }
            }
        }
    }
}

@Composable
private fun SubtitleTool(
    title: String,
    glyph: Glyph,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean = true,
) {
    FocusSurface(onClick, modifier.heightIn(min = 52.dp)
        .background(Palette.Text.copy(alpha = .04f), RoundedCornerShape(10.dp))
        .border(.5.dp, Palette.Line, RoundedCornerShape(10.dp)),
        enabled = enabled, transparent = true, focusedScale = MotionTokens.FixedScale) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppIcon(glyph, Modifier.size(18.dp), color = if (enabled) Palette.Text else Palette.Muted)
            Text(title, color = if (enabled) Palette.Text else Palette.Muted,
                fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SubtitleOptionRow(
    title: String,
    modifier: Modifier = Modifier,
    detail: String = "",
    isSelected: Boolean = false,
    chevron: Boolean = false,
    onClick: () -> Unit,
) {
    FocusSurface(onClick, modifier.fillMaxWidth().heightIn(min = 64.dp)
        .semantics { selected = isSelected },
        selected = isSelected, transparent = true, focusedScale = MotionTokens.FixedScale) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = Palette.Text, fontSize = 15.sp, lineHeight = 21.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (detail.isNotBlank()) Text(detail, color = Palette.Muted, fontSize = 12.sp,
                    lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (chevron) AppIcon(Glyph.Next, Modifier.size(18.dp), color = Palette.Muted)
            if (isSelected) AppIcon(Glyph.CheckCircle, Modifier.size(20.dp))
            else Box(Modifier.size(20.dp).border(1.dp, Palette.Muted.copy(alpha = .45f), CircleShape))
        }
    }
}

@Composable
private fun SubtitleDivider() {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp).height(.5.dp).background(Palette.Line))
}
