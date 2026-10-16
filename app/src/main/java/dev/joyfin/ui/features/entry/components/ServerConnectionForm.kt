package dev.joyfin.ui.features.entry.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.focusTarget

@Composable
internal fun ColumnScope.ServerConnectionForm(
    address: TextFieldState,
    busy: Boolean,
    error: String?,
    onConnect: () -> Unit,
    onCancel: () -> Unit,
) {
    val tv = LocalDeviceLayout.current.television
    EntryStep("01", "连接服务器")
    EntryTitle("连接媒体库")
    EntryText("输入你的 Jellyfin 服务器地址，开启专属私享视听。")
    EntryTextField(
        "服务器地址",
        address,
        "https://media.example.com",
        Modifier.focusTarget("auth:input", tv || !busy),
        enabled = !busy,
        uri = true,
        error = error,
        showErrorText = false,
        onSubmit = onConnect,
    )
    EntryText("支持局域网 IP（如 192.168.1.8:8096）或公网域名反向代理", subdued = true)
    EntryRequestFeedback(busy, error)
    EntryButton(
        if (busy) "取消连接" else "连接服务器",
        { if (busy) onCancel() else onConnect() },
        Modifier.focusTarget("auth:submit"),
        busy = busy,
        enabledWhileBusy = true,
    )
    EntryText("端到端直连你的私有 Jellyfin 服务器，凭据仅安全保存在本地", subdued = true)
}
