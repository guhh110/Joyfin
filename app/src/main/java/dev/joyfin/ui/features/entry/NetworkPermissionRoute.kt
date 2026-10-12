package dev.joyfin.ui.features.entry

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.joyfin.ui.features.entry.components.*
import dev.joyfin.ui.interaction.RememberScreenFocus
import dev.joyfin.ui.interaction.focusTarget

@Composable
fun NetworkPermissionRoute(onAllow: () -> Unit, denied: Boolean = false) {
    RememberScreenFocus("permission:allow") {
        EntryScaffold("JOYFIN") {
            EntryNetworkSymbol()
            EntryStep("LOCAL NETWORK")
            EntryTitle("让影院，\n近在身边。")
            EntryText("连接家中的 Jellyfin 服务器，\n需要访问本地网络。")
            EntryText("仅用于连接你的服务器\n允许后，你仍需要填写服务器地址。\n媒体内容与账号来自你的 Jellyfin。")
            if (denied) EntryError("尚未允许局域网连接，可重新授权；如果系统不再弹出请求，请在应用权限设置中开启。")
            EntryButton("允许局域网连接", onAllow, Modifier.focusTarget("permission:allow"))
            EntryText("接下来由系统请求授权", subdued = true)
        }
    }
}
