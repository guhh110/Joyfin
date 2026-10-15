package dev.joyfin.ui.features.detail

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import dev.joyfin.browse.DetailUiState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.detail.components.MediaInformation
import dev.joyfin.ui.features.detail.mobile.MobileMovieDetailLayout
import dev.joyfin.ui.features.detail.tv.TvMovieDetailLayout
import dev.joyfin.ui.interaction.RememberScreenFocus
import dev.joyfin.ui.model.*

@Composable
internal fun MovieDetailRoute(ui: DetailUiState, actions: BrowseActions, images: ArtworkSource) {
    RememberScreenFocus("movie:entry") {
        var information by remember { mutableStateOf(false) }
        if (LocalDeviceLayout.current.television)
            TvMovieDetailLayout(ui, actions, images) { information = true }
        else MobileMovieDetailLayout(ui, actions, images) { information = true }
        val media = ui.item
        if (information && media != null)
            MediaInformation(media, { information = false }) { source ->
                information = false
                actions.play(media, false, source)
            }
    }
}
