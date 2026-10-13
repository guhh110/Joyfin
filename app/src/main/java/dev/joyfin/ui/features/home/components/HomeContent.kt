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

@Composable
internal fun HomeHeroContent(
    home: Home,
    images: ArtworkSource,
    actions: BrowseActions,
    topPadding: Dp,
    spacing: Dp,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    compact: Boolean = false,
) {
    val featured = home.resume.firstOrNull() ?: home.latest.firstOrNull()
    Column(modifier) {
        if (showHeader) AppHeader(actions)
        Column(
            Modifier.padding(horizontal = LocalDeviceLayout.current.gutter)
                .padding(top = topPadding, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            if (!compact)
                Caption(
                    if (featured?.resumeTicks?.let { it > 0 } == true) "继续你的故事"
                    else "YOUR PERSONAL CINEMA"
                )
            FilmTitle(
                featured?.seriesName?.ifBlank { featured.name } ?: featured?.name ?: "你的私人影院",
                large = !compact,
                maxLines = if (compact || !showHeader) 1 else 2,
            )
            if (featured != null) {
                MediaMetadata(featured)
                if (!compact)
                    androidx.tv.material3.Text(
                        featured.overview,
                        modifier = Modifier.widthIn(max = 540.dp),
                        color = dev.joyfin.ui.theme.Palette.Muted,
                        maxLines = 2,
                    )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton(
                        if (featured.resumeTicks > 0) "继续观看"
                        else if (featured.isFolder) "探索剧集" else "播放",
                        {
                            if (featured.isFolder) actions.open(featured)
                            else actions.play(featured, false, null)
                        },
                        Modifier.focusTarget("hero:play"),
                        Glyph.Play,
                        primary = true,
                    )
                    ActionButton(
                        "详情",
                        { actions.open(featured) },
                        Modifier.focusTarget("hero:detail"),
                        Glyph.Next,
                        quiet = true,
                    )
                }
            } else if (home.resumeLoad.loading || home.latestLoad.loading) {
                SkeletonText(Modifier.widthIn(max = 360.dp), 3)
            } else if (home.resumeLoad.error == null && home.latestLoad.error == null)
                EmptyContent("媒体库还没有内容", "添加媒体后，你的故事会在这里出现。", "刷新", actions.retry)
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
internal fun HomeResumeRail(home: Home, images: ArtworkSource, actions: BrowseActions) {
    LoadingMediaRail(
        "继续观看",
        home.resume,
        home.resumeLoad,
        images,
        actions,
        wide = true,
        retry = { actions.retryHome(HomeRegion.RESUME) },
    )
}
