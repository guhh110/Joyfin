package dev.joyfin.ui.features.detail.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import dev.joyfin.data.Media
import dev.joyfin.data.seasonSummary
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.RememberSelectionFocus
import dev.joyfin.ui.interaction.LocalScreenFocus
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.device.LocalDeviceLayout
import kotlinx.coroutines.flow.first

@Composable
fun SeasonPicker(
    seasons: List<Media>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val selected = seasons.firstOrNull { it.id == selectedId } ?: seasons.firstOrNull()
    val initial = selected?.let { "season-choice:${it.id}" } ?: "sheet:close"
    RememberSelectionFocus(initial) {
        Sheet("选择季", onDismiss, scrollContent = false, modifier = Modifier.fillMaxHeight(.8f)) {
            val index = seasons.indexOfFirst { it.id == selected?.id }.coerceAtLeast(0)
            val scroll = rememberLazyListState(initialFirstVisibleItemIndex = index)
            val memory = LocalScreenFocus.current
            val window = LocalWindowInfo.current
            val tv = LocalDeviceLayout.current.television
            LaunchedEffect(initial) {
                if (seasons.isNotEmpty()) {
                    scroll.scrollToItem(index)
                    snapshotFlow { scroll.layoutInfo.visibleItemsInfo.any { it.index == index } }.first { it }
                }
                if (tv) {
                    snapshotFlow { window.isWindowFocused }.first { it }
                    memory?.rememberTarget?.invoke(initial)
                    withFrameNanos {}
                    memory?.requesters?.get(initial)?.requestFocus()
                }
            }
            if (seasons.isEmpty()) Caption("暂无季信息")
            LazyColumn(Modifier.fillMaxWidth().weight(1f), state = scroll,
                verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
              items(seasons, key = { it.id }) { season ->
                ChoiceRow(
                    season.name,
                    subtitle = season.seasonSummary(),
                    selected = season.id == selectedId,
                    modifier = Modifier.focusTarget("season-choice:${season.id}"),
                ) {
                    onSelect(season.id)
                }
              }
            }
        }
    }
}
