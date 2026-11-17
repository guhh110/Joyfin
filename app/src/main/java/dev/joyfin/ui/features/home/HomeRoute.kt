package dev.joyfin.ui.features.home

import androidx.compose.runtime.Composable
import dev.joyfin.browse.HomeUiState
import dev.joyfin.ui.features.home.components.CineramaHome
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.home.mobile.MobileHomeLayout
import dev.joyfin.ui.model.*
import dev.joyfin.data.HomeLayoutPreferences
import dev.joyfin.ui.features.home.components.HomeResumeActionsHost

@Composable
fun HomeRoute(ui: HomeUiState, actions: BrowseActions, images: ArtworkSource, layout: HomeLayoutPreferences = HomeLayoutPreferences(),
    tracking: dev.joyfin.browse.TrackingUiState? = null) {
    val television = LocalDeviceLayout.current.television
    HomeResumeActionsHost(ui, actions, restoreFocusKey = if (television) "tunnel:screen" else null) {
        if (television) CineramaHome(ui, actions, images, layout, tracking)
        else MobileHomeLayout(ui, actions, images, layout, tracking)
    }
}
