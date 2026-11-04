package dev.joyfin.ui.features.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.data.PlaybackQuality
import dev.joyfin.playback.PlayerUiState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.theme.MotionTokens
import dev.joyfin.ui.theme.Palette

@Composable
internal fun PlayerSettingsContent(ui: PlayerUiState, onPanel: (PlayerPanel) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PlayerSettingsGroup {
            PlayerSettingRow("画质", if (ui.bitrate == PlaybackQuality.ORIGINAL) "原画" else "${ui.bitrate / 1_000_000f} Mbps",
                Modifier.focusTarget("player:settings-quality")) { onPanel(PlayerPanel.Quality) }
            SettingDivider()
            PlayerSettingRow("字幕", ui.localSubtitle?.label
                ?: ui.subtitles.firstOrNull { it.index == ui.subtitleIndex }?.title ?: "关闭",
                Modifier.focusTarget("player:settings-subtitle")) { onPanel(PlayerPanel.Subtitle) }
            SettingDivider()
            PlayerSettingRow("画面缩放", ui.videoScaleMode.label,
                Modifier.focusTarget("player:settings-video-scale")) { onPanel(PlayerPanel.VideoScale) }
            SettingDivider()
            PlayerSettingRow("音轨", ui.audio.firstOrNull { it.index == ui.audioIndex }?.title ?: "默认",
                Modifier.focusTarget("player:settings-audio")) { onPanel(PlayerPanel.Audio) }
            SettingDivider()
            PlayerSettingRow("音频输出", ui.audioOutputMode.label,
                Modifier.focusTarget("player:settings-audio-output")) { onPanel(PlayerPanel.AudioOutput) }
        }
        PlayerSettingsGroup {
            PlayerSettingRow("播放速度", "${ui.speed.toString().removeSuffix(".0")}×",
                Modifier.focusTarget("player:settings-speed")) { onPanel(PlayerPanel.Speed) }
            SettingDivider()
            PlayerSettingRow("章节", "${ui.navigation.chapters.size} 个章节",
                Modifier.focusTarget("player:settings-chapters")) { onPanel(PlayerPanel.Chapters) }
            SettingDivider()
            PlayerSettingRow("片头与片尾", if (ui.navigation.preferences.automatic) "自动跳过" else "手动跳过",
                Modifier.focusTarget("player:settings-skip")) { onPanel(PlayerPanel.Skip) }
        }
        PlayerSettingsGroup {
            PlayerSettingRow("高级设置", modifier = Modifier.focusTarget("player:settings-advanced")) {
                onPanel(PlayerPanel.Advanced)
            }
        }
    }
}

@Composable
internal fun PlayerAdvancedSettings(ui: PlayerUiState, onPanel: (PlayerPanel) -> Unit) {
    PlayerSettingsGroup {
        PlayerSettingRow("媒体版本", ui.versions.firstOrNull { it.id == ui.sourceId }?.title ?: "当前版本",
            Modifier.focusTarget("player:settings-versions")) { onPanel(PlayerPanel.Versions) }
        SettingDivider()
        PlayerSettingRow("播放方式", if (ui.compatible) "兼容转码" else "自动",
            Modifier.focusTarget("player:settings-playback-mode")) { onPanel(PlayerPanel.PlaybackMode) }
        SettingDivider()
        PlayerSettingRow("自定义控制栏", ui.toolbar.pinned.joinToString(" · ") { it.label },
            Modifier.focusTarget("player:settings-toolbar")) { onPanel(PlayerPanel.Toolbar) }
        SettingDivider()
        PlayerSettingRow("播放诊断", modifier = Modifier.focusTarget("player:settings-diagnostics")) {
            onPanel(PlayerPanel.Diagnostics)
        }
    }
}

@Composable
internal fun PlayerSettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()
        .background(Palette.Text.copy(alpha = .025f), RoundedCornerShape(12.dp))
        .border(.5.dp, Palette.Line, RoundedCornerShape(12.dp)).padding(3.dp), content = content)
}

@Composable
internal fun SettingDivider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(.5.dp).background(Palette.Line))
}

@Composable
internal fun PlayerSettingRow(
    title: String,
    value: String = "",
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    endIcon: Glyph? = Glyph.Next,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    FocusSurface(onClick, modifier.fillMaxWidth()
        .heightIn(min = if (LocalDeviceLayout.current.television) 44.dp else 48.dp),
        enabled = enabled, transparent = true, focusedScale = MotionTokens.FixedScale) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, Modifier.width(96.dp), color = if (enabled) Palette.Text else Palette.Muted,
                fontSize = 14.sp, lineHeight = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, Modifier.weight(1f), color = Palette.Muted, textAlign = TextAlign.End,
                fontSize = 12.sp, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (trailing != null) trailing()
            else if (endIcon != null) AppIcon(endIcon, Modifier.size(16.dp), color = Palette.Muted)
            else Spacer(Modifier.width(16.dp))
        }
    }
}

@Composable
internal fun PlayerSettingSwitch(title: String, checked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    PlayerSettingRow(title, modifier = modifier.semantics {
        role = Role.Switch
        toggleableState = ToggleableState(checked)
    }, trailing = {
        Box(Modifier.size(34.dp, 20.dp)
            .background(if (checked) Palette.Text else Palette.Elevated, CircleShape).padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.size(14.dp).background(if (checked) Palette.Background else Palette.Muted, CircleShape))
        }
    }, onClick = onClick)
}
