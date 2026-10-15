package dev.joyfin.ui.features.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.joyfin.browse.CollectionUiState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.features.collection.MediaCollection
import dev.joyfin.ui.features.collection.directoryGrid
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.BrowseActions

@Composable
fun LibraryRoute(ui: CollectionUiState, actions: BrowseActions, images: ArtworkSource) {
    MediaCollection(ui, actions, images) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppHeader(actions, true, horizontalInset = 0.dp)
            FilmTitle(ui.title.ifBlank { "媒体库" })
            Caption(if (ui.busy && ui.media.isEmpty()) " "
                else if (ui.directoryGrid) "${ui.total} 个媒体库" else "${ui.total} 部作品")
        }
    }
}
