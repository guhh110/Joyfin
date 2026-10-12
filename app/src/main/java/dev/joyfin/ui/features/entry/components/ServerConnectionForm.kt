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
    EntryStep("01 / CONNECT")
    EntryTitle("连接你的影院。")
    EntryText("从熟悉的服务器开始，\n让收藏的故事来到身边。")
    EntryTextField(
        "Jellyfin 服务器地址",
        address,
        "https://media.example.com",
        Modifier.focusTarget("auth:input", tv || !busy),
        enabled = !busy,
        uri = true,
        error = error,
        showErrorText = false,
        onSubmit = onConnect,
    )
    EntryText("也可以填写局域网地址，例如 192.168.1.8:8096", subdued = true)
    EntryRequestFeedback(busy, error)
    EntryButton(
        if (busy) "取消连接" else "连接服务器",
        { if (busy) onCancel() else onConnect() },
        Modifier.focusTarget("auth:submit"),
        busy = busy,
        enabledWhileBusy = true,
    )
    EntryText("支持 HTTP / HTTPS 与反向代理子路径\n使用你现有的 Jellyfin 服务器", subdued = true)
}
