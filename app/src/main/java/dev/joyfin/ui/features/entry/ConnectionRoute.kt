package dev.joyfin.ui.features.entry

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import dev.joyfin.session.EntryState
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.entry.components.*

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
    EntryScaffold(if (login) "02 / 02" else "01 / 02", login) {
        if (login)
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
        else ServerConnectionForm(address, ui.busy, ui.error, ::submit, actions.cancel)
    }
}
