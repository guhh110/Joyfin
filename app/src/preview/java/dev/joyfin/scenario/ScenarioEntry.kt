package dev.joyfin.scenario

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import dev.joyfin.session.EntryState
import dev.joyfin.ui.features.entry.ConnectionRoute
import dev.joyfin.ui.features.entry.EntryActions
import dev.joyfin.ui.features.entry.SessionRestoreRoute
import dev.joyfin.ui.interaction.RememberScreenFocus

/** Uses production entry UI with local state only; never reads or writes saved accounts. */
@Composable
internal fun ScenarioEntry(initialPage: String, scenario: Scenario) {
    var page by rememberSaveable { mutableStateOf(initialPage) }
    var ui by remember { mutableStateOf(EntryState(
        server = if (page == "connect") "" else "https://cinema.example.com",
        serverName = "私人放映厅", userName = if (page == "restore") "演示账号" else "",
        busy = page == "restore" && scenario != Scenario.ERROR,
        error = if (scenario == Scenario.ERROR) "暂时无法连接服务器，请检查地址或重试" else null,
    )) }
    val actions = EntryActions(
        connect = { server -> ui = ui.copy(server = server, error = null); page = "login" },
        login = { name, _ -> ui = ui.copy(userName = name, error = "这是本地演示场景，不连接真实服务器") },
        changeServer = { ui = EntryState(); page = "connect" },
        cancel = { ui = ui.copy(busy = false, quickConnectCode = null) },
        retry = { ui = ui.copy(busy = false, error = "演示服务器暂不可用，请稍后重试") },
        quickLogin = { ui = ui.copy(quickConnect = true, quickConnectCode = "729318") },
    )
    key(page) {
        RememberScreenFocus(if (page == "restore") "auth:change-server" else "auth:input") {
            if (page == "restore") SessionRestoreRoute(ui, actions)
            else ConnectionRoute(ui, actions, login = page == "login")
        }
    }
}
