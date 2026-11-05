package dev.joyfin.ui.features.collection.tv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.joyfin.browse.CollectionUiState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.features.collection.mediaCollectionItems
import dev.joyfin.ui.features.collection.directoryGrid
import dev.joyfin.ui.features.collection.rememberCollectionGridState
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.BrowseActions
import kotlin.math.ceil
import kotlin.math.floor

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun TvMediaCollectionLayout(
    ui: CollectionUiState,
    actions: BrowseActions,
    images: ArtworkSource,
    header: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns =
            floor(((maxWidth - 96.dp + 14.dp) / (150.dp + 14.dp)).toDouble())
                .toInt()
                .coerceAtLeast(1)
        val cardWidth = (maxWidth - 96.dp - 14.dp * (columns - 1)) / columns
        val directories = ui.directoryGrid
        val cardHeight = (cardWidth - 12.dp) / MediaCardLayout.ratio(directories) +
            (if (directories) 53.dp else 81.dp)
        val rows =
            ceil((maxHeight / cardHeight).toDouble())
                .toInt()
                .coerceAtLeast(2)
        val skeletonVisible =
            rememberSkeletonVisible(ui.busy, listOf(ui.parent, ui.query, ui.person?.name, ui.libraryOptions, ui.worksOnly))
        val platformScroll = LocalBringIntoViewSpec.current
        val gridState = rememberCollectionGridState(ui)
        val focusInset = with(LocalDensity.current) { 12.dp.toPx() }
        val gridScroll = remember(platformScroll, gridState, focusInset) {
            object : BringIntoViewSpec {
                override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                    val visible = gridState.layoutInfo.visibleItemsInfo
                    val center = offset + size / 2
                    val item = visible.firstOrNull {
                        it.key != "header" && center >= it.offset.y && center <= it.offset.y + it.size.height
                    } ?: return platformScroll.calculateScrollDistance(offset, size, containerSize)
                    // Use the whole row, including the card's focus reserve. Pixel rounding and
                    // focus scale must not produce a different vertical target for each column.
                    val rowOffset = item.offset.y - focusInset
                    val rowHeight = visible.filter { it.row == item.row }.maxOf { it.size.height } + focusInset * 2
                    return if (rowOffset >= 0 && rowOffset + rowHeight <= containerSize) 0f
                    else platformScroll.calculateScrollDistance(rowOffset, rowHeight, containerSize)
                }
            }
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides gridScroll) {
            LazyVerticalGrid(
                GridCells.Fixed(columns),
                Modifier.fillMaxSize().semantics { if (ui.busy) stateDescription = "正在加载" },
                state = gridState,
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                mediaCollectionItems(ui, actions, images, header, columns * rows, skeletonVisible)
            }
        }
    }
}
