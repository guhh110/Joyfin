package dev.joyfin.ui.features.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import dev.joyfin.ui.theme.Palette

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

/** A completed empty/error result is a page state, not three repeated empty rails. */
internal val Home.noContentResult: Boolean
    get() = resume.isEmpty() && latest.isEmpty() && libraries.isEmpty() &&
        listOf(resumeLoad, latestLoad, librariesLoad).let { loads ->
            loads.none { it.loading } && (loads.all { it.loaded } || loads.any { it.error != null })
        }

internal data class HomeResultPresentation(val visible: Boolean, val error: String?)

/** Keep the result and its retry button in place until a retry actually yields content. */
@Composable
internal fun rememberHomeResult(home: Home): HomeResultPresentation {
    var shown by remember { mutableStateOf(false) }
    var previousError by remember { mutableStateOf<String?>(null) }
    val loads = listOf(home.resumeLoad, home.latestLoad, home.librariesLoad)
    val visible = home.resume.isEmpty() && home.latest.isEmpty() && home.libraries.isEmpty() &&
        (home.noContentResult || shown)
    val error = loads.firstNotNullOfOrNull { it.error } ?: previousError.takeIf { loads.any { it.loading } }
    SideEffect { shown = visible; previousError = error }
    return HomeResultPresentation(visible, error)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeHeroContent(
    home: Home,
    actions: BrowseActions,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    result: HomeResultPresentation = HomeResultPresentation(home.noContentResult, null),
) {
    val featured = home.resume.firstOrNull() ?: home.latest.firstOrNull()
    val tv = LocalDeviceLayout.current.television
    Column(
        modifier.widthIn(max = if (tv) 640.dp else 520.dp)
            .padding(horizontal = LocalDeviceLayout.current.gutter)
            .padding(bottom = if (tv) 16.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
    ) {
        if (featured == null && !result.visible && (home.resumeLoad.loading || home.latestLoad.loading)) {
            SkeletonText(Modifier.fillMaxWidth(), 3)
        } else if (featured != null) {
            FilmTitle(
                featured.seriesName.ifBlank { featured.name },
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
        } else if (result.visible) {
            val error = result.error
            val loading = listOf(home.resumeLoad, home.latestLoad, home.librariesLoad).any { it.loading }
            if (error != null) {
                Box(
                    Modifier.size(44.dp).background(Palette.Elevated, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(Glyph.Info, Modifier.size(22.dp), color = Palette.Text)
                }
            }
            FilmTitle(
                when {
                    result.error != null -> "暂时无法加载媒体库"
                    else -> "媒体库还是空的"
                },
                large = tv && !compact,
                maxLines = if (tv) if (compact) 1 else 2 else 3,
            )
            Caption(error?.let(::browseErrorText) ?: "在服务器中添加影片或剧集后，回到这里刷新。")
            ActionButton(if (error != null) "重新加载" else "刷新媒体库", {
                HomeRegion.entries.forEach(actions.retryHome)
            }, Modifier.focusTarget("hero:refresh"), Glyph.Refresh, primary = true, loading = loading)
        }
    }
}

internal fun LazyListScope.homeRails(
    home: Home,
    images: ArtworkSource,
    actions: BrowseActions,
    includeResume: Boolean = true,
    resultOnly: Boolean = home.noContentResult,
    layout: HomeLayoutPreferences = HomeLayoutPreferences(),
) {
    if (resultOnly) return
    val keys = layout.visibleKeys(home.libraries.map { it.id })
    var libraryStatusAdded = false
    fun libraryStatus() {
        if (libraryStatusAdded) return
        libraryStatusAdded = true
        if ((home.librariesLoad.loading && home.rails.isEmpty()) || home.librariesLoad.error != null) {
            item("library-status") {
                Column {
                    RegionLoadFeedback(home.librariesLoad.loading, home.librariesLoad.error,
                        { actions.retryHome(HomeRegion.LIBRARIES) }, home.libraries.isNotEmpty(),
                        Modifier.padding(horizontal = LocalDeviceLayout.current.gutter))
                    if (home.librariesLoad.loading && home.rails.isEmpty()) RailSkeleton()
                }
            }
        }
    }
    keys.forEach { key -> when (key) {
        "resume" -> if (includeResume) item("resume", contentType = "rail") { HomeResumeRail(home, images, actions) }
        "latest" -> item("latest", contentType = "rail") {
            LoadingMediaRail(
                "最近加入",
                home.latest,
                home.latestLoad,
                images,
                actions,
                retry = { actions.retryHome(HomeRegion.LATEST) },
                onMore = actions.allMedia,
            )
        }
        else -> {
            libraryStatus()
            home.rails.firstOrNull { "library:${it.library.id}" == key }?.let { rail ->
                item(key, contentType = "library-rail") { LibraryRail(rail, images, actions) }
            }
        }
    } }
    if (home.libraries.isEmpty()) libraryStatus()
    if (keys.isEmpty()) item("custom-empty") {
        Column(Modifier.padding(horizontal = LocalDeviceLayout.current.gutter, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Caption("首页栏目已隐藏")
            ActionButton("调整首页栏目", actions.settings)
        }
    }
}

@Composable
internal fun HomeResumeRail(home: Home, images: ArtworkSource, actions: BrowseActions, cardWidth: Dp? = null) {
    if (home.resume.isEmpty() && !home.resumeLoad.loading && home.resumeLoad.error == null) return
    var actionItem by remember { mutableStateOf<Media?>(null) }
    LoadingMediaRail(
        "继续观看",
        home.resume,
        home.resumeLoad,
        images,
        actions,
        wide = true,
        cardWidth = cardWidth,
        retry = { actions.retryHome(HomeRegion.RESUME) },
        onLongClick = { actionItem = it },
    )
    actionItem?.let { item ->
        ResumeQuickActionSheet(item, actions) { actionItem = null }
    }
}
