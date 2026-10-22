package dev.joyfin.ui.features.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.data.HomeLayoutPreferences
import dev.joyfin.data.Media
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.RememberSelectionFocus
import dev.joyfin.ui.interaction.focusTarget

@Composable
internal fun HomeLayoutSheet(value: HomeLayoutPreferences, libraries: List<Media>, change: (HomeLayoutPreferences) -> Unit,
    dismiss: () -> Unit) {
    val ids = libraries.map { it.id }
    val keys = value.orderedKeys(ids)
    val initial = remember { "home-layout:${keys.first()}:visible" }
    RememberSelectionFocus(initial) {
        Sheet("首页栏目", dismiss, side = LocalDeviceLayout.current.television, preferredWidth = 600.dp) {
            Caption("调整后立即保存。主视觉保留；TV 的继续观看排首位时保留在首屏。")
            if (libraries.isEmpty()) Caption("媒体库列表尚未加载，回到首页加载后可调整各媒体库。")
            keys.forEachIndexed { index, id -> key(id) {
                val title = when (id) {
                    "resume" -> "继续观看"
                    "latest" -> "最近加入"
                    else -> libraries.firstOrNull { "library:${it.id}" == id }?.name.orEmpty()
                }
                ChoiceRow(title, Modifier.focusTarget("home-layout:$id:visible"),
                    subtitle = if (id in value.hidden) "已隐藏 · 点击显示" else "显示中 · 点击隐藏",
                    selected = id !in value.hidden) { change(value.toggle(id)) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton("上移", { change(value.move(id, -1, ids)) }, Modifier.focusTarget("home-layout:$id:up"), enabled = index > 0)
                    ActionButton("下移", { change(value.move(id, 1, ids)) }, Modifier.focusTarget("home-layout:$id:down"), enabled = index < keys.lastIndex)
                }
            } }
            ActionButton("恢复默认栏目", { change(HomeLayoutPreferences()) }, icon = Glyph.Refresh)
        }
    }
}
