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
import dev.joyfin.ui.interaction.LocalScreenFocus
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.BrowseActions
import dev.joyfin.ui.model.LocalRailBringIntoViewSpec
import dev.joyfin.ui.model.NoBringIntoViewSpec
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.data.HomeLayoutPreferences

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvHomeLayout(ui: HomeUiState, actions: BrowseActions, images: ArtworkSource, layout: HomeLayoutPreferences = HomeLayoutPreferences()) {
    val home = homeContent(ui)
    val result = rememberHomeResult(home)
    val featured = home.resume.firstOrNull() ?: home.latest.firstOrNull()
    val pinResume = layout.visibleKeys(home.libraries.map { it.id }).firstOrNull() == "resume"
    val scroll = rememberLazyListState()
    val platformScroll = LocalBringIntoViewSpec.current
    val screenFocus = LocalScreenFocus.current
    LaunchedEffect(scroll, screenFocus) {
        val memory = screenFocus ?: return@LaunchedEffect
        snapshotFlow { memory.target() }.collect { key ->
            if (key.isBlank()) return@collect
            val info = scroll.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                val itemKey = item.key as? String ?: return@firstOrNull false
                val prefix = when (itemKey) {
                    "hero" -> "hero:"
                    "resume" -> "继续观看:"
                    "latest" -> "最近加入:"
                    else -> "$itemKey:"
                }
                key.startsWith(prefix)
            }
            // Header and hero controls live in item 0; rails use their own item index.
            val index = info?.index ?: 0
            val fullyVisible = info != null && info.offset >= 0 &&
                info.offset + info.size <= scroll.layoutInfo.viewportEndOffset
            if (!fullyVisible) scroll.animateScrollToItem(index)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val resumeEmpty = home.resume.isEmpty() && !home.resumeLoad.loading && home.resumeLoad.error == null
        val heroHeight = if (resumeEmpty) viewportHeight * .82f else viewportHeight
        val resumeWidth = ((maxWidth - LocalDeviceLayout.current.gutter * 2 - MediaCardLayout.RailGap * 3) / 4 / scale)
            .coerceIn(140.dp, 280.dp)
        CompositionLocalProvider(
            LocalBringIntoViewSpec provides NoBringIntoViewSpec,
            LocalRailBringIntoViewSpec provides platformScroll,
        ) {
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
                }
                homeRails(home, images, actions, includeResume = !pinResume, resultOnly = result.visible, layout = layout)
            }
        }
    }
}
