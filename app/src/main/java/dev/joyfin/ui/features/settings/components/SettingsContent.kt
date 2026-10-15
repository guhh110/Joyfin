package dev.joyfin.ui.features.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.BuildConfig
import dev.joyfin.app.SettingsUiState
import dev.joyfin.session.EntryState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.interaction.LocalScreenFocus
import androidx.compose.ui.platform.LocalWindowInfo
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.player.PlaybackDiagnosticsSheet
import kotlinx.coroutines.flow.first
import dev.joyfin.ui.model.BrowseActions
import dev.joyfin.ui.theme.Palette

@Composable
internal fun ColumnScope.SettingsContent(
    ui: EntryState,
    actions: BrowseActions,
    autoNext: Boolean,
    request: SettingsUiState = SettingsUiState(),
) {
    var diagnostics by remember { mutableStateOf(false) }
    var returnFromDiagnostics by remember { mutableStateOf(false) }
    val pageFocus = LocalScreenFocus.current
    val window = LocalWindowInfo.current
    val tv = LocalDeviceLayout.current.television
    LaunchedEffect(diagnostics) {
        if (!diagnostics && returnFromDiagnostics && tv) {
            snapshotFlow { window.isWindowFocused }.first { it }
            withFrameNanos {}
            pageFocus?.rememberTarget?.invoke("settings:diagnostics")
            pageFocus?.requesters?.get("settings:diagnostics")?.requestFocus()
            returnFromDiagnostics = false
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IconButton("返回", Glyph.Back, actions.back, Modifier.focusTarget("settings:back"))
        FilmTitle("设置", Modifier.weight(1f), maxLines = 1)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Caption("当前账号")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Palette.Surface, RoundedCornerShape(10.dp))
                .border(1.dp, Palette.BorderSubtle, RoundedCornerShape(10.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(40.dp)
                    .background(Palette.Elevated, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(Glyph.Account, Modifier.size(20.dp), color = Palette.Text)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    ui.userName.ifBlank { "已登录用户" },
                    color = Palette.Text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                Caption(ui.server.ifBlank { "服务器已连接" }, color = Palette.Muted)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("播放偏好")
        ChoiceRow(
            "自动连播下一集",
            subtitle = if (autoNext) "播放结束后倒计时，可随时取消" else "每集结束后停止",
            selected = autoNext,
        ) {
            actions.setAutoNext(!autoNext)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceRow("最近一次播放诊断", Modifier.focusTarget("settings:diagnostics"),
            subtitle = "查看格式、播放阶段与错误，复制脱敏报告") {
            returnFromDiagnostics = true
            diagnostics = true
        }
        ActionButton("退出当前账号", actions.logout, loading = request.busy, icon = Glyph.Close)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MediaPill("Joyfin v${BuildConfig.VERSION_NAME}", outlined = true)
        }
    }
    if (diagnostics) PlaybackDiagnosticsSheet { diagnostics = false }
}
