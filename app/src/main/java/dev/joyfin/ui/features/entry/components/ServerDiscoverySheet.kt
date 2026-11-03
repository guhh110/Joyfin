package dev.joyfin.ui.features.entry.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.data.DiscoveredServer
import dev.joyfin.data.JellyfinApi
import dev.joyfin.session.DiscoveryPhase
import dev.joyfin.session.ServerDiscoveryState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.LocalScreenFocus
import dev.joyfin.ui.interaction.RememberScreenFocus
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.theme.Palette
import kotlinx.coroutines.flow.first

@Composable
internal fun ServerDiscoverySheet(
    state: ServerDiscoveryState,
    currentAddress: String,
    onScan: () -> Unit,
    onStop: () -> Unit,
    onSelect: (DiscoveredServer) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedAddress = remember(currentAddress) { runCatching { JellyfinApi.normalizeServer(currentAddress) }.getOrNull() }
    val initialFocus = remember {
        (state.servers.firstOrNull { it.address == selectedAddress } ?: state.servers.firstOrNull())
            ?.let { "discovery:server:${it.id}" } ?: "discovery:scan"
    }
    val tv = LocalDeviceLayout.current.television
    RememberScreenFocus(initialFocus) {
        Sheet("局域网服务器", onDismiss, side = tv, preferredWidth = 520.dp) {
            val focus = LocalScreenFocus.current
            val window = LocalWindowInfo.current
            LaunchedEffect(Unit) {
                if (tv) {
                    snapshotFlow { window.isWindowFocused }.first { it }
                    withFrameNanos {}
                    focus?.rememberTarget?.invoke(initialFocus)
                    focus?.restore()
                }
            }
            LaunchedEffect(state.phase) {
                if (tv && !state.searching) {
                    val target = focus?.target?.invoke()
                    if (target?.startsWith("discovery:server:") == true &&
                        state.servers.none { "discovery:server:${it.id}" == target }) {
                        snapshotFlow { window.isWindowFocused }.first { it }
                        withFrameNanos {}
                        focus?.rememberTarget?.invoke("discovery:scan")
                        focus?.restore()
                    }
                }
            }
            Caption("选择发现的地址后连接服务器，再登录账号。")
            EntryButton(
                if (state.searching) "停止查找" else if (state.phase == DiscoveryPhase.IDLE) "查找服务器" else "重新查找",
                { if (state.searching) onStop() else onScan() },
                Modifier.focusTarget("discovery:scan"), busy = state.searching, enabledWhileBusy = true,
            )
            state.error?.let { EntryError(it) }
            if (state.servers.isNotEmpty()) {
                Caption("已发现 ${state.servers.size} 台服务器")
                state.servers.forEach { server -> key(server.id) {
                    FocusSurface(
                        { onSelect(server) }, Modifier.fillMaxWidth().heightIn(min = 72.dp)
                            .focusTarget("discovery:server:${server.id}"),
                        selected = server.address == selectedAddress,
                        focusedScale = dev.joyfin.ui.theme.MotionTokens.FixedScale,
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AppIcon(Glyph.Library, Modifier.size(22.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(server.name, color = Palette.Text, fontSize = 15.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Caption(server.address, maxLines = 2)
                            }
                            if (server.address == selectedAddress) AppIcon(Glyph.CheckCircle, Modifier.size(20.dp))
                        }
                    }
                } }
            } else if (state.searching) {
                SkeletonRegion(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(2) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                SkeletonBlock(Modifier.size(22.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    SkeletonTextLine(15.sp, 20.sp, .5f)
                                    SkeletonTextLine(12.5.sp, 18.sp, .85f)
                                }
                            }
                        }
                    }
                }
            } else if (state.phase == DiscoveryPhase.COMPLETE) {
                Caption("未发现服务器。请确认设备与服务器位于同一局域网，且服务器已开启自动发现；也可以返回手动填写地址。")
            } else if (state.phase == DiscoveryPhase.STOPPED) {
                Caption("已停止查找。可以重新查找，或返回手动填写地址。")
            }
            Caption("通过 Wi-Fi 或网线连接到服务器所在的局域网；本次查找约需 6 秒。")
        }
    }
}
