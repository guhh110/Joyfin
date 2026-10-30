package dev.joyfin.ui.features.entry

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import dev.joyfin.session.EntryState
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.entry.components.*
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.interaction.LocalScreenFocus
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Screen assembly only. Requests, cancellation, authentication and persistence stay in the host.
 */
@Composable
fun ConnectionRoute(ui: EntryState, actions: EntryActions, login: Boolean = false) {
    val address = rememberTextFieldState(ui.server)
    val name = rememberTextFieldState(ui.userName)
    // Unlike address/name, this editor is not rememberSaveable: passwords never enter saved state.
    val password = remember { TextFieldState() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val television = LocalDeviceLayout.current.television
    val screenFocus = LocalScreenFocus.current
    var previousQuick by remember { mutableStateOf(ui.quickConnect) }
    LaunchedEffect(ui.quickConnect) {
        if (television && (ui.quickConnect || previousQuick)) {
            withFrameNanos {}
            screenFocus?.rememberTarget?.invoke("auth:quick")
            screenFocus?.restore()
        }
        previousQuick = ui.quickConnect
    }
    fun submit() {
        if (ui.busy) return
        keyboard?.hide()
        if (!television) focus.clearFocus()
        if (login) actions.login(name.text.toString(), password.text.toString())
        else actions.connect(address.text.toString())
    }
    LaunchedEffect(ui.error, ui.busy) {
        if (ui.error != null && !ui.busy) password.edit { replace(0, length, "") }
    }
    DisposableEffect(Unit) { onDispose { password.edit { replace(0, length, "") } } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val quickActive by rememberUpdatedState(ui.quickConnect)
    val cancel by rememberUpdatedState(actions.cancel)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && quickActive) cancel()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    BackHandler(enabled = login && ui.quickConnect, onBack = actions.cancel)
    EntryScaffold(
        if (login) "02 / 02" else "01 / 02",
        login,
        title = if (login && ui.quickConnect) "快速登录" else if (login) "账号登录" else "连接媒体库",
        subtitle = if (login) "登录你的 Jellyfin 账号，同步媒体收藏与观影记录。"
        else "输入你的 Jellyfin 服务器地址，开启专属私享视听。",
    ) {
        if (login && ui.quickConnect) QuickConnectForm(ui, actions)
        else if (login) {
            AccountLoginForm(
                name,
                password,
                ui.server,
                ui.serverName,
                ui.busy,
                ui.error,
                ui.sessionExpired,
                ::submit,
                actions.cancel,
                actions.changeServer,
            )
            if (television) EntryButton("使用快速登录", {
                keyboard?.hide()
                password.edit { replace(0, length, "") }
                actions.quickLogin()
            }, Modifier.focusTarget("auth:quick"), quiet = true, enabled = !ui.busy)
        } else ServerConnectionForm(address, ui.busy, ui.error, ::submit, actions.cancel)
    }
}
