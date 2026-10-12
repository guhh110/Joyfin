package dev.joyfin.ui.features.detail

import androidx.compose.runtime.Composable
import dev.joyfin.browse.DetailUiState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.model.*

/** Movie and series layouts have separate composition roots; navigation remains shared. */
@Composable
fun DetailRoute(ui: DetailUiState, actions: BrowseActions, images: ArtworkSource) {
    if (ui.item?.type == "Series" || ui.entryType in setOf("Series", "Season", "Episode"))
        SeriesDetailRoute(ui, actions, images)
    else MovieDetailRoute(ui, actions, images)
}
