package dev.joyfin.ui.features.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.joyfin.browse.HomeUiState
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.home.mobile.MobileHomeLayout
import dev.joyfin.ui.features.home.tv.TvHomeLayout
import dev.joyfin.ui.features.home.tv.tvHomeResumeItems
import dev.joyfin.ui.model.*
import dev.joyfin.data.HomeLayoutPreferences
import dev.joyfin.ui.features.home.components.HomeResumeActionsHost

@Composable
fun HomeRoute(ui: HomeUiState, actions: BrowseActions, images: ArtworkSource, layout: HomeLayoutPreferences = HomeLayoutPreferences(),
    tracking: dev.joyfin.browse.TrackingUiState? = null,
    discovery: dev.joyfin.browse.TvHomeDiscoveryUiState = dev.joyfin.browse.TvHomeDiscoveryUiState(),
    interactionBlocked: Boolean = false) {
    val television = LocalDeviceLayout.current.television
    val resume = ui.content?.resume.orEmpty()
    val displayedResume = remember(television, resume) { if (television) tvHomeResumeItems(resume) else resume }
    HomeResumeActionsHost(ui, actions, displayedResume) {
        if (television) TvHomeLayout(ui, actions, images, layout, tracking, discovery, interactionBlocked)
        else MobileHomeLayout(ui, actions, images, layout, tracking)
    }
}
