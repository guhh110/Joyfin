package dev.joyfin.ui.features.entry

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.joyfin.session.EntryState
import dev.joyfin.ui.features.entry.components.*
import dev.joyfin.ui.interaction.focusTarget

@Composable
fun SessionRestoreRoute(ui: EntryState, actions: EntryActions) {
    EntryScaffold("JOYFIN") {
        EntryStep("WELCOME BACK")
        EntryTitle(if (ui.error != null) "暂时未能连接。" else "故事，继续。")
        EntryText(if (ui.error != null) "这次连接没有完成。\n你的登录信息仍然保留。" else "欢迎回到你的影院，继续你的故事。")
        ConnectedServerSummary(ui.serverName, ui.server, true, actions.changeServer)
        ui.error?.let { EntryError(it) }
        EntryButton("重新连接", actions.retry, Modifier.focusTarget("auth:retry"), busy = ui.busy)
    }
}
