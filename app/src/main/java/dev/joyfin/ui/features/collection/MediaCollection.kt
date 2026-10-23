package dev.joyfin.ui.features.collection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import dev.joyfin.browse.CollectionUiState
import dev.joyfin.browse.CollectionKind
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.collection.mobile.MobileMediaCollectionLayout
import dev.joyfin.ui.features.collection.tv.TvMediaCollectionLayout
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.BrowseActions

/** Root navigation is a directory view; nested views require explicit directory types. */
internal val CollectionUiState.directoryGrid: Boolean
    get() = kind == CollectionKind.LIBRARY && query.isBlank() &&
        (!worksOnly && parent == null || media.isNotEmpty() && media.all { it.libraryDirectory })

/** Reset only when the query changes in place; returning from a detail keeps saved scroll. */
@Composable
internal fun rememberCollectionGridState(ui: CollectionUiState): LazyGridState {
    val state = rememberLazyGridState()
    val query = listOf(ui.parent, ui.query, ui.person?.id, ui.libraryOptions, ui.worksOnly)
    var previous by remember { mutableStateOf(query) }
    LaunchedEffect(query) {
        if (query != previous) { previous = query; state.scrollToItem(0) }
    }
    return state
}

/** Library, search and filmography share collection content and device-specific grid layouts. */
@Composable
fun MediaCollection(
    ui: CollectionUiState,
    actions: BrowseActions,
    images: ArtworkSource,
    header: @Composable () -> Unit,
) {
    if (LocalDeviceLayout.current.television) TvMediaCollectionLayout(ui, actions, images, header)
    else MobileMediaCollectionLayout(ui, actions, images, header)
}
