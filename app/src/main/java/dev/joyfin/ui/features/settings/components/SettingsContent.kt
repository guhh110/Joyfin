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
        val accountShape = RoundedCornerShape(14.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(androidx.compose.ui.graphics.Color(0xFF0F121A), accountShape)
                .border(0.5.dp, Palette.Line, accountShape)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(44.dp)
                    .background(Palette.Elevated, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(Glyph.Account, Modifier.size(22.dp), color = Palette.Text)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier.size(6.dp)
                            .background(androidx.compose.ui.graphics.Color(0xFF22C55E), androidx.compose.foundation.shape.CircleShape)
                    )
                    Text(
                        ui.userName.ifBlank { "已登录用户" },
                        color = Palette.Text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
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

        // 关于 Joyfin 旗舰独立微展台
        val aboutShape = RoundedCornerShape(16.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(androidx.compose.ui.graphics.Color(0xFF0B0D13), aboutShape)
                .border(0.5.dp, Palette.Line, aboutShape)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            dev.joyfin.ui.features.entry.components.JoyfinLogo(
                Modifier.size(42.dp),
                glow = true,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Joyfin",
                        color = Palette.Text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )
                    MediaPill("v${BuildConfig.VERSION_NAME}", outlined = true)
                }
                Text(
                    "无界影院 · 私享视听客户端",
                    color = Palette.Muted,
                    fontSize = 12.sp,
                )
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    QualityBadge("Android 9+ 通用")
                    QualityBadge("全架构支持")
                }
            }
        }
    }
    if (diagnostics) PlaybackDiagnosticsSheet { diagnostics = false }
}
