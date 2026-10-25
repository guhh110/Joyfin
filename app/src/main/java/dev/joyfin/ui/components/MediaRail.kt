package dev.joyfin.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.joyfin.data.Media
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.model.ArtworkSource

@Composable
fun MediaRail(
    title: String,
    items: List<Media>,
    images: ArtworkSource,
    onOpen: (Media) -> Unit,
    wide: Boolean = false,
    onMore: (() -> Unit)? = null,
    focusPrefix: String = title,
    state: LazyListState = rememberLazyListState(),
    showHeading: Boolean = true,
    cardWidth: Dp? = null,
    preferSeriesTitle: Boolean = true,
    itemFocus: @Composable (Media) -> Modifier = { Modifier.focusTarget("$focusPrefix:${it.id}") },
) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showHeading)
            Box(Modifier.padding(horizontal = LocalDeviceLayout.current.gutter)) {
                SectionHeading(
                    title,
                    if (onMore != null) "查看全部" else null,
                    onMore,
                    "$focusPrefix:more",
                )
            }
        LazyRow(
            state = state,
            modifier = Modifier.testTag("rail:$focusPrefix"),
            contentPadding = PaddingValues(horizontal = LocalDeviceLayout.current.gutter),
            horizontalArrangement = Arrangement.spacedBy(MediaCardLayout.RailGap),
        ) {
            items(items, key = { it.id }, contentType = { "media-card" }) { item ->
                MediaCard(
                    item,
                    images,
                    { onOpen(item) },
                    focusModifier = itemFocus(item),
                    wide = wide,
                    cardWidth = cardWidth,
                    focusKey = "$focusPrefix:${item.id}",
                    preferSeriesTitle = preferSeriesTitle,
                )
            }
        }
    }
}
