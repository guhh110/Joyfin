package dev.joyfin.ui.features.player.components

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.playback.PlayerActions
import dev.joyfin.playback.PlayerUiState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.focusTarget

/** Detailed skip controls are separate from the main settings list. */
@Composable
internal fun PlaybackSkipSettings(ui: PlayerUiState, actions: PlayerActions) {
    val nav = ui.navigation
    val prefs = nav.preferences
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (nav.canConfigure) {
            PlayerSettingsGroup {
                PlayerSettingSwitch("自动跳过", prefs.automatic, Modifier.focusTarget("player:skip-auto")) {
                    actions.setSkipPreferences(prefs.copy(automatic = !prefs.automatic))
                }
                if (nav.canSharePreferences) {
                    SettingDivider()
                    PlayerSettingSwitch("按版本记忆", nav.separateVersions,
                        Modifier.focusTarget("player:skip-version-scope")) {
                        actions.separateSkipVersions(!nav.separateVersions)
                    }
                }
            }
            PlayerSettingsGroup {
                PlayerSettingRow("片头结束", if (prefs.introDurationMs > 0) timeLabel(prefs.introDurationMs) else "未设置",
                    Modifier.focusTarget("player:skip-clear-intro"), enabled = prefs.introDurationMs > 0,
                    endIcon = Glyph.Close) {
                    actions.setSkipPreferences(prefs.copy(introSeconds = 0, introEndMs = null))
                }
                SettingDivider()
                PlayerSettingRow("片尾时长", if (prefs.effectiveOutroDurationMs > 0)
                    timeLabel(prefs.effectiveOutroDurationMs) else "未设置",
                    Modifier.focusTarget("player:skip-clear-outro"), enabled = prefs.effectiveOutroDurationMs > 0,
                    endIcon = Glyph.Close) {
                    actions.setSkipPreferences(prefs.copy(outroSeconds = 0, outroDurationMs = null))
                }
                SettingDivider()
                PlayerSettingRow("撤销上次标记", modifier = Modifier.focusTarget("player:settings-undo-mark"),
                    enabled = nav.canUndoMark && !ui.loading && !ui.subtitleApplying, endIcon = Glyph.Back,
                    onClick = actions.undoMark)
            }
            if (nav.preferenceScope.isNotBlank()) Caption(nav.preferenceScope, maxLines = 2)
            nav.legacyPreferences?.let { legacy ->
                val intro = if (legacy.introDurationMs > 0) timeLabel(legacy.introDurationMs) else "未设置"
                val outro = if (legacy.effectiveOutroDurationMs > 0) timeLabel(legacy.effectiveOutroDurationMs) else "未设置"
                Caption("旧设置：片头 $intro · 片尾 $outro")
                PlayerSettingsGroup {
                    PlayerSettingRow("沿用旧设置", modifier = Modifier.focusTarget("player:skip-use-legacy"),
                        endIcon = Glyph.Clock, onClick = actions.useLegacySkipPreferences)
                }
            }
        } else Caption("使用服务器或章节标记")
        if (!nav.timelineAvailable) Caption("当前版本没有独立的服务器时间轴")
        nav.error?.let { Caption(it) }
        if (nav.timelineAvailable) {
            PlayerSettingsGroup {
                PlayerSettingRow("重新读取标记", if (nav.loading) "读取中" else "",
                    Modifier.focusTarget("player:skip-retry"), enabled = !nav.loading,
                    endIcon = Glyph.Refresh, onClick = actions.retrySegments)
            }
        }
    }
}
