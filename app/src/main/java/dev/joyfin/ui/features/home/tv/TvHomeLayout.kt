package dev.joyfin.ui.features.home.tv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
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

@Composable
internal fun TvHomeLayout(ui: HomeUiState, actions: BrowseActions, images: ArtworkSource, layout: HomeLayoutPreferences = HomeLayoutPreferences()) {
    val home = homeContent(ui)
    val result = rememberHomeResult(home)
    val featured = home.resume.firstOrNull() ?: home.latest.firstOrNull()
    val pinResume = layout.visibleKeys(home.libraries.map { it.id }).firstOrNull() == "resume"
    val scroll = rememberLazyListState()
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
                HeroBackdrop(
                    featured,
                    images,
                    0.dp,
                    Modifier.height(heroHeight).testTag("home:hero"),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        AppHeader(actions, active = "home")
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                            HomeHeroContent(
                                home,
                                actions,
                                modifier = Modifier.align(
                                    if (result.visible || resumeEmpty) Alignment.CenterStart
                                    else Alignment.BottomStart
                                ),
                                result = result,
                                compact =
                                    maxHeight / LocalDensity.current.fontScale < 240.dp,
                            )
                        }
                        if (!result.visible && pinResume) HomeResumeRail(home, images, actions, resumeWidth)
                    }
                }
            }
            homeRails(home, images, actions, includeResume = !pinResume, resultOnly = result.visible, layout = layout)
        }
    }
}
