package dev.joyfin.ui.features.entry

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.joyfin.session.EntryState
import dev.joyfin.ui.features.entry.components.*
import dev.joyfin.ui.interaction.focusTarget

@Composable
fun SessionRestoreRoute(ui: EntryState, actions: EntryActions) {
    EntryScaffold("JOYFIN") {
        EntryStep("00", "恢复会话")
        EntryTitle(if (ui.error != null) "媒体服务器未连接" else "恢复影院会话")
        EntryText(
            if (ui.error != null) "未能访问已保存的媒体服务器，请检查网络或更换服务器。"
            else "正在连接已保存的 Jellyfin 服务器并恢复影院会话。"
        )
        ConnectedServerSummary(ui.serverName, ui.server, true, actions.changeServer)
        ui.error?.let { EntryError(it) }
        EntryButton(
            if (ui.busy) "正在连接..." else "重新连接",
            actions.retry,
            Modifier.focusTarget("auth:retry"),
            busy = ui.busy,
        )
    }
}
