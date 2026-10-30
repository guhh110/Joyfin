package dev.joyfin.ui.features.entry.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.session.EntryState
import dev.joyfin.ui.features.entry.EntryActions
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.theme.Palette

@Composable
internal fun ColumnScope.QuickConnectForm(ui: EntryState, actions: EntryActions) {
    EntryText(ui.serverName.ifBlank { "Jellyfin" })
    EntryText("在手机或电脑上登录同一台 Jellyfin 服务器，进入个人设置 → 快速连接（Quick Connect），输入下方授权码。")
    Text(
        ui.quickConnectCode?.chunked(3)?.joinToString("  ") ?: "— — — — — —",
        color = Palette.Text,
        fontSize = 36.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 3.sp,
    )
    EntryText("授权后电视会自动登录。授权码仅本次有效，最多等待 5 分钟。", subdued = true)
    EntryRequestFeedback(ui.busy, ui.error)
    EntryButton(
        if (ui.busy) "取消快速登录" else "重新获取授权码",
        { if (ui.busy) actions.cancel() else actions.quickLogin() },
        Modifier.focusTarget("auth:quick"),
        busy = ui.busy,
        enabledWhileBusy = true,
    )
    if (!ui.busy) EntryButton("使用账号密码登录", actions.cancel, Modifier.focusTarget("auth:password-login"), quiet = true)
}
