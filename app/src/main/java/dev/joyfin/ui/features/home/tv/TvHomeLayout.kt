package dev.joyfin.ui.features.home.tv

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import dev.joyfin.browse.HomeUiState
import dev.joyfin.browse.TrackingUiState
import dev.joyfin.data.HomeLayoutPreferences
import dev.joyfin.ui.components.*
import dev.joyfin.ui.features.home.components.*
import dev.joyfin.ui.interaction.LocalScreenFocus
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.BrowseActions
import dev.joyfin.ui.theme.Palette
import dev.joyfin.ui.theme.rememberMotionSpec
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvHomeLayout(ui: HomeUiState, actions: BrowseActions, images: ArtworkSource,
    layout: HomeLayoutPreferences = HomeLayoutPreferences(), tracking: TrackingUiState? = null) {
    val home = homeContent(ui)
    val result = rememberHomeResult(home)
    val carousel = rememberHomeCarousel(home, images, tracking)
    val sections = listOf(TvHomeSection("featured", "精选")) +
        if (result.visible) emptyList() else tvHomeSections(home, layout)
    val sectionKeys = sections.map { it.key }
    var activeKey by rememberSaveable { mutableStateOf("featured") }
    var selections by rememberSaveable { mutableStateOf(hashMapOf<String, String>()) }
    val pager = rememberPagerState(pageCount = { sections.size })
    val focus = LocalScreenFocus.current
    val scope = rememberCoroutineScope()
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val latestSections by rememberUpdatedState(sections)
    val pageAnimation = rememberMotionSpec<Float>(240)
    // Page changes are explicit. Default bring-into-view must not reveal half of another page.
    val pageScroll = remember { object : BringIntoViewSpec {
        override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = 0f
    } }

    fun entryTarget(section: TvHomeSection): String {
        if (section.key == "featured") return when {
            result.visible && (carousel.items.isEmpty() || tracking == null) -> "hero:refresh"
            else -> "hero:play"
        }
        if (section.key == "layout") return "home:layout"
        val selected = section.items.firstOrNull { it.id == selections[section.key] } ?: section.items.firstOrNull()
        return selected?.let(section::mediaKey)
            ?: section.retryKey.takeIf { section.load.error != null || focus?.requesters?.containsKey(it) == true }
            ?: section.moreKey.takeIf { section.hasMore && !section.load.loading }
            ?: "header:home"
    }
    val latestEntryTarget by rememberUpdatedState<(TvHomeSection) -> String>(::entryTarget)
    val currentWindowFocused by rememberUpdatedState(windowFocused)
    fun focusContent() {
        val section = latestSections.getOrNull(pager.settledPage) ?: return
        val remembered = focus?.target?.invoke().orEmpty()
        val belongs = if (section.key == "featured") remembered.startsWith("hero:")
            else remembered.startsWith("${section.focusPrefix}:") || remembered == section.retryKey ||
                remembered == section.moreKey || section.key == "layout" && remembered == "home:layout"
        val target = remembered.takeIf { belongs && focus?.requesters?.containsKey(it) == true }
            ?: latestEntryTarget(section)
        val requester = focus?.requesters?.get(target) ?: focus?.requesters?.get("header:home")
        requester?.requestFocus()
    }
    fun goToPage(index: Int) {
        if (pager.isScrollInProgress) return
        val target = index.coerceIn(0, sections.lastIndex)
        focus?.rememberTarget?.invoke(entryTarget(sections[target]))
        scope.launch {
            pager.animateScrollToPage(target, animationSpec = pageAnimation)
            withFrameNanos {}
            focusContent()
        }
    }

    LaunchedEffect(sectionKeys) {
        val index = sectionKeys.indexOf(activeKey).takeIf { it >= 0 }
            ?: pager.currentPage.coerceAtMost(sections.lastIndex)
        if (pager.currentPage != index) pager.scrollToPage(index)
        activeKey = sectionKeys[index]
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { index ->
            activeKey = latestSections.getOrNull(index)?.key ?: "featured"
            withFrameNanos {}
            if (currentWindowFocused && focus?.target?.invoke()?.startsWith("header:") == false) focusContent()
        }
    }
    // A refresh may remove a focused item or a successful retry button. Repair only a lost target.
    LaunchedEffect(sections, windowFocused, tracking, carousel.current?.id) {
        if (!windowFocused) return@LaunchedEffect
        withFrameNanos {}
        val target = focus?.target?.invoke() ?: return@LaunchedEffect
        if (!pager.isScrollInProgress && !focus.requesters.containsKey(target)) focusContent()
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Palette.Background)) {
        val unit = minOf(maxWidth / 1920, maxHeight / 1080)
        val stage = Modifier.size(unit * 1920, unit * 1080)
        CompositionLocalProvider(LocalBringIntoViewSpec provides pageScroll) {
            VerticalPager(pager, key = { sectionKeys[it] },
                modifier = Modifier.fillMaxSize().testTag("home:scroll").onPreviewKeyEvent { event ->
                    when (event.key) {
                        Key.DirectionDown, Key.PageDown -> {
                            if (event.type == KeyEventType.KeyDown) goToPage(pager.settledPage + 1)
                            true
                        }
                        Key.DirectionUp, Key.PageUp -> {
                            if (event.type == KeyEventType.KeyDown) {
                                if (pager.settledPage == 0) focus?.requesters?.get("header:home")?.requestFocus()
                                else goToPage(pager.settledPage - 1)
                            }
                            true
                        }
                        else -> false
                    }
                }) { index ->
                val section = sections[index]
                val active = index == pager.settledPage && !pager.isScrollInProgress
                val selected = section.items.firstOrNull { it.id == selections[section.key] } ?: section.items.firstOrNull()
                Box(Modifier.fillMaxSize().testTag("home:section:${section.key}")) {
                    if (section.key == "featured") TvHomeBackdrop(carousel.current, images) { HomeCarouselBackdrop(carousel) }
                    else TvHomeBackdrop(selected, images, collection = true)
                    Box(stage.align(Alignment.Center).then(
                        if (section.key == "featured") Modifier.testTag("home:hero") else Modifier)) {
                        if (section.key == "featured") TvHomeHero(home, result, carousel, tracking, actions,
                            unit, active, Modifier.offset(x = unit * 96,
                                y = unit * if (carousel.current != null && (!result.visible || tracking != null)) 488 else 360))
                        else TvHomeCollection(section, images, actions, unit, selections[section.key], { id ->
                            if (selections[section.key] != id)
                                selections = HashMap(selections).apply { put(section.key, id) }
                        }, active)
                        if (sections.size > 1) TvHomeNext(unit, index == sections.lastIndex,
                            Modifier.align(Alignment.BottomStart).padding(start = unit * 80, bottom = unit * 48)) {
                            goToPage(if (index == sections.lastIndex) 0 else index + 1)
                        }
                    }
                }
            }
        }
        Box(stage.align(Alignment.Center)) {
            TvHomeHeader(actions, unit, { goToPage(0) },
                Modifier.align(Alignment.TopCenter).padding(top = unit * 54).width(unit * 1728)
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.DirectionDown) {
                            if (event.type == KeyEventType.KeyDown) focusContent()
                            true
                        } else event.key == Key.DirectionUp
                    })
            if (sections.size > 1) TvHomePositions(pager.settledPage, sections.size, unit,
                Modifier.align(Alignment.CenterEnd).padding(end = unit * 60))
        }
    }
}

@Composable
private fun TvHomeNext(unit: Dp, last: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.size(unit * 60).focusProperties { canFocus = false }
        .semantics { contentDescription = if (last) "返回精选" else "下一栏目" }
        .stableAction(true, onClick = onClick), contentAlignment = Alignment.Center) {
        AppIcon(Glyph.Down, Modifier.size(unit * 27).graphicsLayer { rotationZ = if (last) 180f else 0f }, Palette.Muted)
    }
}

@Composable
private fun TvHomePositions(page: Int, count: Int, unit: Dp, modifier: Modifier) {
    val start = (page - 4).coerceIn(0, (count - 9).coerceAtLeast(0))
    val shown = minOf(count, 9)
    Canvas(modifier.size(unit * 8, unit * (shown * 32)).semantics {
        stateDescription = "第 ${page + 1} 屏，共 $count 屏"
    }) {
        repeat(shown) { offset ->
            val current = start + offset == page
            val height = (unit * if (current) 24 else 6).toPx()
            drawRoundRect(if (current) Palette.Text else Palette.Muted.copy(alpha = .45f),
                topLeft = Offset(0f, (unit * (offset * 32 + 16)).toPx() - height / 2),
                size = Size((unit * 4).toPx(), height), cornerRadius = CornerRadius((unit * 4).toPx()))
        }
    }
}
