package dev.joyfin.ui.features.home.tv

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.unit.dp
import dev.joyfin.browse.HomeUiState
import dev.joyfin.browse.TrackingUiState
import dev.joyfin.browse.TvHomeDiscoveryUiState
import dev.joyfin.data.HomeLayoutPreferences
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
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
    layout: HomeLayoutPreferences = HomeLayoutPreferences(), tracking: TrackingUiState? = null,
    discovery: TvHomeDiscoveryUiState = TvHomeDiscoveryUiState(), interactionBlocked: Boolean = false) {
    val home = homeContent(ui)
    val result = rememberHomeResult(home)
    val sections = listOf(TvHomeSection("featured", "精选")) +
        if (result.visible) emptyList() else tvHomeSections(home, layout)
    val sectionKeys = sections.map { it.key }
    var activeKey by rememberSaveable { mutableStateOf("featured") }
    var selections by rememberSaveable { mutableStateOf(hashMapOf<String, String>()) }
    var resumeIndex by rememberSaveable { mutableIntStateOf(0) }
    val latestSections by rememberUpdatedState(sections)
    val pager = rememberPagerState(pageCount = { latestSections.size })
    val featured = rememberTvHomeFeaturedState(home.resume, tracking, discovery.items, images,
        pager.settledPage == 0 && !pager.isScrollInProgress, interactionBlocked)
    val focus = LocalScreenFocus.current
    val scope = rememberCoroutineScope()
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val pageAnimation = rememberMotionSpec<Float>(240)
    // Page changes are explicit. Default bring-into-view must not reveal half of another page.
    val pageScroll = remember { object : BringIntoViewSpec {
        override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = 0f
    } }

    fun selectedItem(section: TvHomeSection) =
        section.items.firstOrNull { section.selectionKey(it) == selections[section.key] }
            ?: section.items.getOrNull((if (section.key == "resume") resumeIndex else 0)
                .coerceAtMost(section.items.lastIndex))

    fun entryTarget(section: TvHomeSection): String {
        if (section.key == "featured") return "hero:play"
        if (section.key == "layout") return "home:layout"
        val selected = selectedItem(section)
        return selected?.let(section::mediaKey)
            ?: section.retryKey.takeIf { section.load.error != null || focus?.requesters?.containsKey(it) == true }
            ?: section.moreKey.takeIf { section.hasMore }
            ?: "header:home"
    }
    val latestEntryTarget by rememberUpdatedState<(TvHomeSection) -> String>(::entryTarget)
    val currentWindowFocused by rememberUpdatedState(windowFocused)
    var initialFocusRequested by rememberSaveable(images.cacheNamespace) { mutableStateOf(false) }
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
        val target = index.coerceIn(0, latestSections.lastIndex)
        focus?.rememberTarget?.invoke(latestEntryTarget(latestSections[target]))
        scope.launch {
            pager.animateScrollToPage(target, animationSpec = pageAnimation)
            withFrameNanos {}
            focusContent()
        }
    }

    LaunchedEffect(windowFocused, featured.current?.key, featured.interacted, discovery.loaded,
        discovery.error, interactionBlocked) {
        if (!initialFocusRequested && windowFocused && !interactionBlocked &&
            (featured.current != null || discovery.loaded || discovery.error != null)) {
            if (!featured.interacted && pager.settledPage == 0) {
                withFrameNanos {}
                focus?.requesters?.get("hero:play")?.requestFocus()
            }
            initialFocusRequested = true
        }
    }

    val featuredFocusBeforeUpdate = focus?.target?.invoke()?.takeIf { it.startsWith("hero:") }
    LaunchedEffect(featured.current?.key) {
        if (featuredFocusBeforeUpdate != null && pager.settledPage == 0 && windowFocused && !interactionBlocked) {
            withFrameNanos {}
            val fallback = if (featuredFocusBeforeUpdate.startsWith("hero:candidate:") && featured.current != null)
                "hero:candidate:${featured.current?.key}" else "hero:play"
            val requester = focus.requesters[featuredFocusBeforeUpdate]
                ?: focus.requesters[fallback]
            requester?.requestFocus()
        }
    }

    val removedSectionHadFocus = activeKey !in sectionKeys &&
        focus?.target?.invoke()?.startsWith("header:") == false
    LaunchedEffect(sectionKeys) {
        val index = sectionKeys.indexOf(activeKey).takeIf { it >= 0 }
            ?: pager.currentPage.coerceAtMost(sections.lastIndex)
        if (removedSectionHadFocus) focus.rememberTarget(latestEntryTarget(latestSections[index]))
        if (pager.currentPage != index) pager.scrollToPage(index)
        activeKey = sectionKeys[index]
        if (removedSectionHadFocus && currentWindowFocused) {
            withFrameNanos {}
            if (!focus.target().startsWith("header:")) focusContent()
        }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { index ->
            activeKey = latestSections.getOrNull(index)?.key ?: "featured"
            withFrameNanos {}
            if (currentWindowFocused && focus?.target?.invoke()?.startsWith("header:") == false) focusContent()
        }
    }
    // A refresh may remove a focused item or a successful retry button. Repair only a lost target.
    LaunchedEffect(sections, windowFocused, featured.current?.key, ui.resumeAction, discovery, tracking) {
        if (!windowFocused) return@LaunchedEffect
        withFrameNanos {}
        val target = focus?.target?.invoke() ?: return@LaunchedEffect
        if (!pager.isScrollInProgress && !focus.requesters.containsKey(target)) focusContent()
    }

    BackHandler(enabled = pager.settledPage == 0 && windowFocused && !interactionBlocked &&
        focus?.target?.invoke()?.startsWith("hero:") == true) {
        featured.onInput()
        val target = if (focus?.target?.invoke()?.startsWith("hero:candidate:") == true) "hero:play" else "header:home"
        focus?.requesters?.get(target)?.requestFocus()
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Palette.Background).onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown) featured.onInput()
        false
    }) {
        val unit = minOf(maxWidth / 1920, maxHeight / 1080)
        val stage = Modifier.size(unit * 1920, unit * 1080)
        CompositionLocalProvider(LocalBringIntoViewSpec provides pageScroll) {
            VerticalPager(pager, key = { latestSections[it].key },
                modifier = Modifier.fillMaxSize().testTag("home:scroll").onPreviewKeyEvent { event ->
                    if (pager.settledPage == 0 && event.key in listOf(Key.DirectionDown, Key.DirectionUp))
                        return@onPreviewKeyEvent false
                    val section = latestSections[pager.settledPage]
                    val target = focus?.target?.invoke()
                    when (event.key) {
                        Key.DirectionDown, Key.PageDown -> {
                            if (event.type == KeyEventType.KeyDown) {
                                val entry = latestEntryTarget(section)
                                if (event.key == Key.DirectionDown && target == section.moreKey && entry != section.moreKey)
                                    focus.requesters[entry]?.requestFocus()
                                else goToPage(pager.settledPage + 1)
                            }
                            true
                        }
                        Key.DirectionUp, Key.PageUp -> {
                            if (event.type == KeyEventType.KeyDown) {
                                if (event.key == Key.DirectionUp && section.hasMore && target != section.moreKey)
                                    focus?.requesters?.get(section.moreKey)?.requestFocus()
                                else if (pager.settledPage == 0) focus?.requesters?.get("header:home")?.requestFocus()
                                else goToPage(pager.settledPage - 1)
                            }
                            true
                        }
                        else -> false
                    }
                }) { index ->
                val section = latestSections[index]
                val active = index == pager.settledPage && !pager.isScrollInProgress
                val selected = selectedItem(section)
                Box(Modifier.fillMaxSize().testTag("home:section:${section.key}")) {
                    if (section.key == "featured") TvHomeFeaturedBackdrop(featured, unit)
                    else TvHomeBackdrop(selected, images, collection = true)
                    Box(stage.align(Alignment.Center).then(
                        if (section.key == "featured") Modifier.testTag("home:hero") else Modifier)) {
                        if (section.key == "featured") TvHomeFeaturedHero(home, tracking, discovery, featured,
                            actions, images, unit, active, { goToPage(1) })
                        else TvHomeCollection(section, images, actions, unit, selections[section.key], { key ->
                            if (selections[section.key] != key)
                                selections = HashMap(selections).apply { put(section.key, key) }
                            if (section.key == "resume")
                                resumeIndex = section.items.indexOfFirst { section.selectionKey(it) == key }.coerceAtLeast(0)
                        }, active)
                        if (sections.size > 1 && section.key != "featured") TvHomeNext(unit, index == sections.lastIndex,
                            Modifier.align(Alignment.BottomStart).padding(start = unit * 80, bottom = unit * 48)) {
                            goToPage(if (index == sections.lastIndex) 0 else index + 1)
                        }
                    }
                }
            }
        }
        Box(stage.align(Alignment.Center)) {
            if (sections.size > 1) TvHomePositions(pager.settledPage, sections.size, unit,
                Modifier.align(Alignment.CenterEnd).padding(end = unit * 60))
        }
        TvHomeHeader(actions, { goToPage(0) },
            Modifier.align(Alignment.TopCenter)
                .padding(horizontal = LocalDeviceLayout.current.gutter, vertical = 6.dp)
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.DirectionDown) {
                        if (event.type == KeyEventType.KeyDown) focusContent()
                        true
                    } else event.key == Key.DirectionUp
                })
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
    val selectedColor = Palette.Text
    val inactiveColor = Palette.Muted.copy(alpha = .45f)
    Canvas(modifier.size(unit * 8, unit * (shown * 32)).semantics {
        stateDescription = "第 ${page + 1} 屏，共 $count 屏"
    }) {
        repeat(shown) { offset ->
            val current = start + offset == page
            val height = (unit * if (current) 24 else 6).toPx()
            drawRoundRect(if (current) selectedColor else inactiveColor,
                topLeft = Offset(0f, (unit * (offset * 32 + 16)).toPx() - height / 2),
                size = Size((unit * 4).toPx(), height), cornerRadius = CornerRadius((unit * 4).toPx()))
        }
    }
}
