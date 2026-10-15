package dev.joyfin.ui.features.home.tv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.joyfin.browse.HomeUiState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.features.home.components.*
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.BrowseActions
import dev.joyfin.ui.model.FirstViewportScrollSpec
import dev.joyfin.ui.device.LocalDeviceLayout

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvHomeLayout(ui: HomeUiState, actions: BrowseActions, images: ArtworkSource) {
    val home = homeContent(ui)
    val result = rememberHomeResult(home)
    val featured = home.resume.firstOrNull() ?: home.latest.firstOrNull()
    val scroll = rememberLazyListState()
    val platformScroll = LocalBringIntoViewSpec.current
    val viewportScroll =
        remember(platformScroll, scroll) {
            FirstViewportScrollSpec(platformScroll) {
                scroll.firstVisibleItemIndex == 0 && scroll.firstVisibleItemScrollOffset == 0
            }
        }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val resumeWidth = ((maxWidth - LocalDeviceLayout.current.gutter * 2 - MediaCardLayout.RailGap * 3) / 4 / scale)
            .coerceIn(140.dp, 280.dp)
        CompositionLocalProvider(LocalBringIntoViewSpec provides viewportScroll) {
            LazyColumn(
                Modifier.fillMaxSize().testTag("home:scroll"),
                state = scroll,
                contentPadding = PaddingValues(bottom = 36.dp),
            ) {
                item("hero", contentType = "hero") {
                    // Horizontal rails keep platform navigation; only the outer vertical scroll
                    // holds fully visible first-screen controls in place.
                    CompositionLocalProvider(LocalBringIntoViewSpec provides platformScroll) {
                        HeroBackdrop(
                            featured,
                            images,
                            0.dp,
                            Modifier.height(viewportHeight).testTag("home:hero"),
                        ) {
                            Column(Modifier.fillMaxSize()) {
                                AppHeader(actions)
                                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                                    HomeHeroContent(
                                        home,
                                        actions,
                                        modifier = Modifier.align(if (result.visible) Alignment.CenterStart else Alignment.BottomStart),
                                        result = result,
                                        compact =
                                            maxHeight / LocalDensity.current.fontScale < 240.dp,
                                    )
                                }
                                if (!result.visible) HomeResumeRail(home, images, actions, resumeWidth)
                            }
                        }
                    }
                }
                homeRails(home, images, actions, includeResume = false, resultOnly = result.visible)
            }
        }
    }
}
