package dev.joyfin.ui.features.collection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.Composable
import dev.joyfin.browse.CollectionUiState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.collection.mobile.MobileMediaCollectionLayout
import dev.joyfin.ui.features.collection.tv.TvMediaCollectionLayout
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.BrowseActions

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
