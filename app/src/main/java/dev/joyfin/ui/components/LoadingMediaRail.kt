package dev.joyfin.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.joyfin.data.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.model.*

@Composable
fun LoadingMediaRail(
    title: String,
    items: List<Media>,
    load: RegionLoad,
    images: ArtworkSource,
    actions: BrowseActions,
    wide: Boolean = false,
    onMore: (() -> Unit)? = null,
    retry: () -> Unit = actions.retry,
    focusPrefix: String = title,
    cardWidth: Dp? = null,
) {
    Column {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (items.isNotEmpty())
                MediaRail(title, items, images, actions.open, wide, onMore, focusPrefix, cardWidth = cardWidth)
            else {
                Box(Modifier.padding(horizontal = LocalDeviceLayout.current.gutter)) {
                    SectionHeading(
                        title,
                        if (onMore != null) "查看全部" else null,
                        onMore,
                        "$focusPrefix:more",
                    )
                }
                if (load.loading) RailSkeleton(wide, cardWidth = cardWidth)
                else if (load.error == null && load.loaded)
                    Box(Modifier.padding(horizontal = LocalDeviceLayout.current.gutter)) {
                        Caption("暂无内容")
                    }
            }
        }
        // This reserved line is also the gap before the next home rail; do not add another gap.
        RegionLoadFeedback(
            load.loading,
            load.error,
            retry,
            items.isNotEmpty(),
            Modifier.padding(horizontal = LocalDeviceLayout.current.gutter),
        )
    }
}
