package dev.joyfin.ui.features.home.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.browse.HomeUiState
import dev.joyfin.ui.components.AppHeader
import dev.joyfin.ui.components.HeroBackdrop
import dev.joyfin.ui.features.home.components.*
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.BrowseActions

@Composable
internal fun MobileHomeLayout(ui: HomeUiState, actions: BrowseActions, images: ArtworkSource) {
    val home = homeContent(ui)
    val featured = home.resume.firstOrNull() ?: home.latest.firstOrNull()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val artworkHeight = (maxWidth * .75f).coerceIn(220.dp, 360.dp)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item("hero", contentType = "hero") {
                HeroBackdrop(featured, images, 0.dp, artworkHeight = artworkHeight) {
                    HomeHeroContent(home, actions,
                        Modifier.fillMaxWidth().statusBarsPadding().padding(top = artworkHeight * .58f))
                    Box(Modifier.align(Alignment.TopCenter)) { AppHeader(actions, statusBarInset = true) }
                }
            }
            homeRails(home, images, actions)
        }
    }
}
