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
import dev.joyfin.data.HomeLayoutPreferences

@Composable
internal fun MobileHomeLayout(ui: HomeUiState, actions: BrowseActions, images: ArtworkSource, layout: HomeLayoutPreferences = HomeLayoutPreferences(),
    tracking: dev.joyfin.browse.TrackingUiState? = null) {
    val home = homeContent(ui)
    val result = rememberHomeResult(home)
    val carousel = rememberHomeCarousel(home, images, tracking)
    val featured = carousel.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val artworkHeight = (maxWidth * .75f).coerceIn(220.dp, 360.dp)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item("hero", contentType = "hero") {
                HeroBackdrop(featured, images, 0.dp, artworkHeight = artworkHeight,
                    background = { HomeCarouselBackdrop(carousel) }) {
                    val heroModifier = Modifier.fillMaxWidth().statusBarsPadding()
                        .padding(top = if (result.visible) 96.dp else artworkHeight * .58f)
                    if ((!result.visible || tracking != null) && carousel.items.isNotEmpty()) HomeCarouselContent(carousel, actions, heroModifier)
                    else if (tracking != null) TrackingHeroPlaceholder(tracking, actions, heroModifier)
                    else HomeHeroContent(home, actions, heroModifier, result = result)
                    Box(Modifier.align(Alignment.TopCenter)) { AppHeader(actions, statusBarInset = true) }
                }
            }
            homeRails(home, images, actions, resultOnly = result.visible, layout = layout)
        }
    }
}
