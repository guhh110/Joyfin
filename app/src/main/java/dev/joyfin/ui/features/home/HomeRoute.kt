package dev.joyfin.ui.features.home

import androidx.compose.runtime.Composable
import dev.joyfin.browse.HomeUiState
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.home.mobile.MobileHomeLayout
import dev.joyfin.ui.features.home.tv.TvHomeLayout
import dev.joyfin.ui.model.*

@Composable
fun HomeRoute(ui: HomeUiState, actions: BrowseActions, images: ArtworkSource) {
    if (LocalDeviceLayout.current.television) TvHomeLayout(ui, actions, images)
    else MobileHomeLayout(ui, actions, images)
}
