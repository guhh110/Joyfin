package dev.joyfin.ui.features.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.joyfin.browse.HomeRegion
import dev.joyfin.browse.HomeUiState
import dev.joyfin.data.*
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.BrowseActions

internal fun homeContent(ui: HomeUiState): Home {
    val home =
        ui.content
            ?: Home(
                emptyList(),
                emptyList(),
                emptyList(),
                resumeLoad = RegionLoad(loading = true),
                latestLoad = RegionLoad(loading = true),
                librariesLoad = RegionLoad(loading = true),
            )
    return home
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeHeroContent(
    home: Home,
    actions: BrowseActions,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val featured = home.resume.firstOrNull() ?: home.latest.firstOrNull()
    val tv = LocalDeviceLayout.current.television
    Column(
        modifier.widthIn(max = if (tv) 640.dp else 520.dp)
            .padding(horizontal = LocalDeviceLayout.current.gutter)
            .padding(bottom = if (tv) 16.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
    ) {
        if (featured == null && (home.resumeLoad.loading || home.latestLoad.loading)) {
            SkeletonText(Modifier.fillMaxWidth(), 3)
        } else {
            FilmTitle(
                featured?.seriesName?.ifBlank { featured.name } ?: featured?.name ?: "媒体库",
                large = tv && !compact,
                maxLines = if (tv) if (compact) 1 else 2 else 3,
            )
        }
        if (featured != null) {
            if (!compact && featured.type != "Episode" && featured.originalTitle.isNotBlank() &&
                featured.originalTitle != featured.name) Caption(featured.originalTitle, maxLines = 1)
            if (!compact) MediaMetadata(featured)
            if (tv && !compact && featured.overview.isNotBlank())
                Caption(featured.overview, maxLines = 2)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    if (featured.resumeTicks > 0) "继续观看" else if (featured.isFolder) "查看剧集" else "播放",
                    { if (featured.isFolder) actions.open(featured) else actions.play(featured, false, null) },
                    Modifier.focusTarget("hero:play"), Glyph.Play, primary = true, maxLines = 1,
                )
                ActionButton("查看详情", { actions.open(featured) },
                    Modifier.focusTarget("hero:detail"), maxLines = 1)
            }
        } else if (!home.resumeLoad.loading && !home.latestLoad.loading &&
            home.resumeLoad.error == null && home.latestLoad.error == null) {
            Caption("媒体库还没有内容")
            ActionButton("刷新", actions.retry, Modifier.focusTarget("hero:refresh"))
        }
    }
}

internal fun LazyListScope.homeRails(
    home: Home,
    images: ArtworkSource,
    actions: BrowseActions,
    includeResume: Boolean = true,
) {
    run {
        if (includeResume)
            item("resume", contentType = "rail") { HomeResumeRail(home, images, actions) }
        item("latest", contentType = "rail") {
            LoadingMediaRail(
                "最近加入",
                home.latest,
                home.latestLoad,
                images,
                actions,
                retry = { actions.retryHome(HomeRegion.LATEST) },
                onMore = { actions.library(null, "全部媒体") },
            )
        }
        if (
            (home.librariesLoad.loading && home.rails.isEmpty()) || home.librariesLoad.error != null
        ) {
            item("library-status") {
                Column {
                    RegionLoadFeedback(
                        home.librariesLoad.loading,
                        home.librariesLoad.error,
                        { actions.retryHome(HomeRegion.LIBRARIES) },
                        home.libraries.isNotEmpty(),
                        Modifier.padding(horizontal = LocalDeviceLayout.current.gutter),
                    )
                    if (home.librariesLoad.loading && home.rails.isEmpty()) RailSkeleton()
                }
            }
        }
        home.rails.forEach { rail ->
            item("library:${rail.library.id}", contentType = "library-rail") {
                LibraryRail(rail, images, actions)
            }
        }
    }
}

@Composable
internal fun HomeResumeRail(home: Home, images: ArtworkSource, actions: BrowseActions, cardWidth: Dp? = null) {
    LoadingMediaRail(
        "继续观看",
        home.resume,
        home.resumeLoad,
        images,
        actions,
        wide = true,
        cardWidth = cardWidth,
        retry = { actions.retryHome(HomeRegion.RESUME) },
    )
}
