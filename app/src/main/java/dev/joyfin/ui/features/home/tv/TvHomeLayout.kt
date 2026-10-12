package dev.joyfin.ui.features.home.tv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.browse.HomeUiState
import dev.joyfin.data.*
import dev.joyfin.ui.components.*
import dev.joyfin.ui.features.home.components.*
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.BrowseActions

@Composable
internal fun TvHomeLayout(ui: HomeUiState, actions: BrowseActions, images: ArtworkSource) {
    val home = homeContent(ui)
    val featured = home.resume.firstOrNull() ?: home.latest.firstOrNull()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 36.dp)) {
        item("hero", contentType = "hero") {
            Box(Modifier.padding(bottom = 24.dp)) {
                HeroBackdrop(featured, images, 360.dp) {
                    HomeHeroContent(home, images, actions, 40.dp, 10.dp)
                }
            }
        }
        homeRails(home, images, actions)
    }
}
