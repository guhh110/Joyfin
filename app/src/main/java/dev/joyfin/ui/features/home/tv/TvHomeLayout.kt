package dev.joyfin.ui.features.home.tv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
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
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.data.HomeLayoutPreferences
import dev.joyfin.ui.model.FirstViewportScrollSpec

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvHomeLayout(ui: HomeUiState, actions: BrowseActions, images: ArtworkSource, layout: HomeLayoutPreferences = HomeLayoutPreferences(),
    tracking: dev.joyfin.browse.TrackingUiState? = null) {
    val home = homeContent(ui)
    val result = rememberHomeResult(home)
    val carousel = rememberHomeCarousel(home, images, tracking)
    val featured = carousel.current
    val pinResume = layout.visibleKeys(home.libraries.map { it.id }).firstOrNull() == "resume"
    val scroll = rememberLazyListState()
    val platformScroll = LocalBringIntoViewSpec.current
    val firstViewportScroll = remember(platformScroll, scroll) {
        FirstViewportScrollSpec(platformScroll, viewportOffset = {
            scroll.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "hero" }?.offset?.toFloat()
        }) { true }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides firstViewportScroll) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewportHeight = maxHeight
            val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
            val resumeEmpty = home.resume.isEmpty() && !home.resumeLoad.loading && home.resumeLoad.error == null
            val heroHeight = if (resumeEmpty) viewportHeight * .82f else viewportHeight
            val resumeWidth = ((maxWidth - LocalDeviceLayout.current.gutter * 2 - MediaCardLayout.RailGap * 3) / 4 / scale)
                .coerceIn(140.dp, 280.dp)
            LazyColumn(
                Modifier.fillMaxSize().testTag("home:scroll"),
                state = scroll,
                contentPadding = PaddingValues(bottom = 36.dp),
            ) {
                item("hero", contentType = "hero") {
                    // Horizontal rails keep their own platform focus scrolling policy.
                    CompositionLocalProvider(LocalBringIntoViewSpec provides platformScroll) {
                        HeroBackdrop(
                            featured,
                            images,
                            0.dp,
                            Modifier.height(heroHeight).testTag("home:hero"),
                            background = { HomeCarouselBackdrop(carousel) },
                        ) {
                            Column(Modifier.fillMaxSize()) {
                                AppHeader(actions, active = "home")
                                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                                    val heroModifier = Modifier.align(
                                        if (result.visible || resumeEmpty) Alignment.CenterStart else Alignment.BottomStart)
                                    if ((!result.visible || tracking != null) && carousel.items.isNotEmpty()) HomeCarouselContent(
                                        carousel, actions, heroModifier,
                                        compact = maxHeight / LocalDensity.current.fontScale < 320.dp)
                                    else if (tracking != null) TrackingHeroPlaceholder(tracking, actions, heroModifier)
                                    else HomeHeroContent(
                                        home,
                                        actions,
                                        modifier = Modifier.align(
                                            if (result.visible || resumeEmpty) Alignment.CenterStart
                                            else Alignment.BottomStart
                                        ),
                                        result = result,
                                        compact = maxHeight / LocalDensity.current.fontScale < 240.dp,
                                    )
                                }
                                if (!result.visible && pinResume) HomeResumeRail(home, images, actions, resumeWidth)
                            }
                        }
                    }
                }
                homeRails(home, images, actions, includeResume = !pinResume, resultOnly = result.visible, layout = layout,
                    railContent = { content ->
                        CompositionLocalProvider(LocalBringIntoViewSpec provides platformScroll, content = content)
                    })
            }
        }
    }
}
