package dev.joyfin.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.joyfin.data.Media
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.LocalRailBringIntoViewSpec
import dev.joyfin.ui.tv.scroll.tvFocusTarget

@OptIn(ExperimentalFoundationApi::class)
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
    outerIndex: Int = 0,
    itemFocus: @Composable (Media) -> Modifier = { Modifier.focusTarget("$focusPrefix:${it.id}") },
) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showHeading)
            Box(Modifier.padding(horizontal = LocalDeviceLayout.current.gutter).tvFocusTarget(outerIndex)) {
                SectionHeading(
                    title,
                    if (onMore != null) "查看全部" else null,
                    onMore,
                    "$focusPrefix:more",
                )
            }
        val railSpec = LocalRailBringIntoViewSpec.current
        val rail: @Composable () -> Unit = {
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
                        focusModifier = itemFocus(item).tvFocusTarget(outerIndex),
                        wide = wide,
                        cardWidth = cardWidth,
                        focusKey = "$focusPrefix:${item.id}",
                        preferSeriesTitle = preferSeriesTitle,
                    )
                }
            }
        }
        if (railSpec != null)
            CompositionLocalProvider(LocalBringIntoViewSpec provides railSpec) { rail() }
        else rail()
    }
}
