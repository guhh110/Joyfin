package dev.joyfin.ui.features.collection.tv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
        LazyVerticalGrid(
            GridCells.Fixed(columns),
            Modifier.fillMaxSize().semantics { if (ui.busy) stateDescription = "正在加载" },
            state = rememberCollectionGridState(ui),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            mediaCollectionItems(ui, actions, images, header, columns * rows, skeletonVisible)
        }
    }
}
