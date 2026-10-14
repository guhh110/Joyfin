package dev.joyfin.ui.features.subtitles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.data.Media
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.*

@Composable
internal fun SubtitleChoiceSheet(title: String, options: List<Pair<String, String>>, selected: String?, dismiss: () -> Unit, pick: (String) -> Unit) {
    val initial = options.firstOrNull { it.first == selected }?.first ?: options.firstOrNull()?.first
    RememberSelectionFocus(initial?.let { "subtitle-option:$it" } ?: "sheet:close") {
        Sheet(title, dismiss) {
            if (options.isEmpty()) Caption("没有可选项，请返回重试")
            options.forEach { (id, name) -> ChoiceRow(name, Modifier.focusTarget("subtitle-option:$id"), selected = id == selected) { pick(id) } }
        }
    }
}

@Composable
internal fun SubtitleEpisodeSheet(title: String, media: List<Media>, selected: String?, dismiss: () -> Unit, skip: (() -> Unit)?, pick: (Media) -> Unit) {
    val initialIndex = media.indexOfFirst { it.id == selected }.coerceAtLeast(0)
    val grid = rememberLazyGridState(initialFirstVisibleItemIndex = initialIndex)
    val initial = media.getOrNull(initialIndex)?.id
    RememberSelectionFocus(initial?.let { "subtitle-episode:$it" } ?: "sheet:close") {
        Sheet(title, dismiss) {
            LazyVerticalGrid(columns = GridCells.Fixed(if (LocalDeviceLayout.current.television) 6 else 4), state = grid,
                modifier = Modifier.fillMaxWidth().height(220.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(media, key = { it.id }) { item -> ActionButton("${item.episode}", { pick(item) }, Modifier.focusTarget("subtitle-episode:${item.id}"), selected = item.id == selected) }
            }
            skip?.let { ActionButton("暂不对应", it) }
        }
    }
}
