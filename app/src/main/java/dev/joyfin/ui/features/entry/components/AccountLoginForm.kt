package dev.joyfin.ui.features.entry.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.focusTarget

@Composable
internal fun ColumnScope.AccountLoginForm(
    name: TextFieldState,
    password: TextFieldState,
    server: String,
    serverName: String,
    busy: Boolean,
    error: String?,
    expired: Boolean,
    onLogin: () -> Unit,
    onCancel: () -> Unit,
    onChangeServer: () -> Unit,
) {
    var passwordKeyboardRequest by remember { mutableIntStateOf(0) }
    val passwordFocus = remember { FocusRequester() }
    val nameFocus = remember { FocusRequester() }
    val serverFocus = remember { FocusRequester() }
    val eyeFocus = remember { FocusRequester() }
    val submitFocus = remember { FocusRequester() }
    var nameError by remember { mutableStateOf<String?>(null) }
    fun submit() {
        if (busy) return
        if (name.text.isBlank()) {
            nameError = "请填写用户名。"
            nameFocus.requestFocus()
        } else {
            nameError = null
            onLogin()
        }
    }
    LaunchedEffect(name.text.toString()) { if (name.text.isNotBlank()) nameError = null }
    val tv = LocalDeviceLayout.current.television
    EntryStep("02 / SIGN IN")
    EntryTitle("欢迎回来。")
    EntryText("登录账号，继续未完的故事。")
    ConnectedServerSummary(
        serverName,
        server,
        !busy,
        onChangeServer,
        Modifier.focusRequester(serverFocus).focusProperties { if (tv) down = nameFocus },
    )
    if (expired)
        EntryText("登录已过期，请重新登录。", Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    EntryTextField(
        "用户名",
        name,
        "你的 Jellyfin 用户名",
        Modifier.focusRequester(nameFocus)
            .focusProperties {
                if (tv) {
                    up = serverFocus
                    down = passwordFocus
                }
            }
            .focusTarget("auth:input", tv || !busy),
        enabled = !busy,
        error = nameError,
        showErrorText = false,
        next = true,
        onSubmit = {
            if (!busy) {
                passwordFocus.requestFocus()
                passwordKeyboardRequest++
            }
        },
    )
    EntryTextField(
        "密码",
        password,
        "输入密码",
        Modifier.focusRequester(passwordFocus)
            .focusProperties {
                if (tv) {
                    up = nameFocus
                    down = eyeFocus
                    right = eyeFocus
                }
            }
            .focusTarget("auth:password", tv || !busy),
        enabled = !busy,
        secret = true,
        keyboardRequest = passwordKeyboardRequest,
        visibilityModifier =
            Modifier.focusRequester(eyeFocus).focusProperties {
                if (tv) {
                    up = passwordFocus
                    left = passwordFocus
                    down = submitFocus
                }
            },
        error = error,
        showErrorText = false,
        onSubmit = ::submit,
    )
    EntryRequestFeedback(busy, nameError ?: error)
    EntryButton(
        if (busy) "取消登录" else "进入影院",
        { if (busy) onCancel() else submit() },
        Modifier.focusRequester(submitFocus)
            .focusProperties {
                if (tv) {
                    up = eyeFocus
                }
            }
            .focusTarget("auth:submit"),
        busy = busy,
        enabledWhileBusy = true,
    )
    EntryText("账号由你的 Jellyfin 服务器管理", subdued = true)
}
