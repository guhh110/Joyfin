package dev.joyfin.ui.features.home.components

import androidx.compose.runtime.Composable
import dev.joyfin.data.*
import dev.joyfin.ui.components.*
import dev.joyfin.ui.model.*

@Composable
fun LibraryRail(state: LibraryRailState, images: ArtworkSource, actions: BrowseActions) {
    LoadingMediaRail(
        state.library.name,
        state.items,
        RegionLoad(state.loading, state.error, !state.loading && state.error == null),
        images,
        actions,
        onMore = { actions.library(state.library.id, state.library.name) },
        retry = { actions.retryLibrary(state.library.id) },
        focusPrefix = "library:${state.library.id}",
    )
}
