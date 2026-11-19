package dev.joyfin.ui.features.home.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Text
import dev.joyfin.browse.HomeRegion
import dev.joyfin.browse.TrackingUiState
import dev.joyfin.browse.TvHomeDiscoveryUiState
import dev.joyfin.data.Home
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.LocalScreenFocus
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.BrowseActions
import dev.joyfin.ui.theme.Palette
import dev.joyfin.ui.theme.MotionTokens
import dev.joyfin.ui.theme.rememberMotionSpec

@Composable
private fun rememberFeaturedActionWidth(labels: List<String>, weight: FontWeight, namespace: String): Dp {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontSize = 15.sp, fontWeight = weight, letterSpacing = .2.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false))
    val textWidth = labels.maxOfOrNull {
        measurer.measure(it, style, maxLines = 1, softWrap = false).size.width
    } ?: 0
    val required = with(density) { textWidth.toDp() } + 70.dp
    var retained by rememberSaveable(namespace, density.density, density.fontScale) { mutableFloatStateOf(0f) }
    val width = maxOf(required.value, retained)
    SideEffect { retained = width }
    return width.dp
}

@Composable
internal fun TvHomeFeaturedHero(
    home: Home, tracking: TrackingUiState?, discovery: TvHomeDiscoveryUiState,
    state: TvHomeFeaturedState, actions: BrowseActions, images: ArtworkSource,
    unit: Dp, active: Boolean, nextPage: () -> Unit,
) {
    val current = state.current
    val focus = LocalScreenFocus.current
    val largeFont = LocalDensity.current.fontScale > 1.15f
    val playWidth = rememberFeaturedActionWidth(state.items.map { it.playLabel }, FontWeight.SemiBold, images.cacheNamespace)
    val detailWidth = rememberFeaturedActionWidth(listOf("详情"), FontWeight.Medium, images.cacheNamespace)
    fun request(key: String) { focus?.requesters?.get(key)?.requestFocus() }
    fun execute(item: TvHomeFeaturedItem) {
        state.onInput()
        item.playback?.let { actions.play(it, false, null) } ?: actions.open(item.details)
    }
    @Composable
    fun target(key: String) = Modifier.focusProperties { canFocus = active }.focusTarget(key, active)
    val retryActions = buildList<Pair<String, () -> Unit>> {
        if (home.resumeLoad.error != null) add("续看" to { actions.retryHome(HomeRegion.RESUME) })
        if (tracking?.error != null || tracking?.entries?.any { it.error != null } == true)
            add("追剧" to actions.refreshTracking)
        if (discovery.error != null) add("最近加入" to actions.retryTvHomeDiscovery)
    }
    val loading = home.resumeLoad.loading || tracking?.loading == true || discovery.loading ||
        !discovery.loaded && discovery.error == null
    val emptyLibrary = home.librariesLoad.loaded && home.libraries.isEmpty() &&
        home.resumeLoad.loaded && home.resume.isEmpty() && home.latestLoad.loaded && home.latest.isEmpty() &&
        discovery.loaded && discovery.items.isEmpty() && tracking?.loading != true && tracking?.error == null &&
        tracking?.entries.orEmpty().isEmpty()
    Column(Modifier.offset(x = unit * 104, y = unit * 230).width(unit * 960)
        .height(unit * 514).testTag("home:hero-content").onPreviewKeyEvent { event ->
            if (current != null) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionUp -> {
                    if (event.type == KeyEventType.KeyDown) request("header:home")
                    true
                }
                Key.DirectionDown -> {
                    if (event.type == KeyEventType.KeyDown) {
                        if (retryActions.isNotEmpty()) request("hero:retry:${retryActions.first().first}")
                        else nextPage()
                    }
                    true
                }
                else -> false
            }
        }) {
        if (current != null) {
            TvHomeFeaturedTitle(current, images, unit, largeFont)
            Text(current.metadata, color = Palette.Text.copy(alpha = .82f), fontSize = (unit.value * 24).sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(unit * 42).testTag("hero:metadata"))
            Text(current.media.overview, color = Palette.Muted, fontSize = (unit.value * 25).sp,
                lineHeight = (unit.value * 34).sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(unit * 800).height(unit * 42).testTag("hero:overview"))
            Spacer(Modifier.height(unit * 24))
            Row(Modifier.onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.DirectionUp -> {
                        if (event.type == KeyEventType.KeyDown) request("header:home")
                        true
                    }
                    Key.DirectionDown -> {
                        if (event.type == KeyEventType.KeyDown) request("hero:candidate:${current.key}")
                        true
                    }
                    Key.DirectionLeft, Key.DirectionRight -> {
                        if (event.type == KeyEventType.KeyDown)
                            request(if (event.key == Key.DirectionLeft) "hero:play"
                                else if (focus?.target?.invoke() == "hero:detail" && retryActions.isNotEmpty())
                                    "hero:retry:${retryActions.first().first}" else "hero:detail")
                        true
                    }
                    else -> false
                }
            }, horizontalArrangement = Arrangement.spacedBy(unit * 24)) {
                ActionButton(current.playLabel, { execute(current) }, target("hero:play").width(playWidth),
                    if (current.playback == null) Glyph.List else Glyph.Play, primary = true, maxLines = 1)
                ActionButton("详情", { state.onInput(); actions.open(current.details) },
                    target("hero:detail").width(detailWidth), Glyph.Info, maxLines = 1)
            }
        } else {
            Spacer(Modifier.height(unit * 45))
            if (loading) {
                SkeletonText(Modifier.width(unit * 850).height(unit * 220), lines = 3)
            } else {
                Text(when {
                    retryActions.isNotEmpty() -> "暂时无法加载精选"
                    emptyLibrary -> "媒体库还是空的"
                    else -> "暂无可展示作品"
                }, color = Palette.Text, fontSize = (unit.value * 60).sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(unit * 28))
                Caption(if (emptyLibrary) "添加电影或剧集后，回到这里刷新。"
                    else "可以浏览媒体库，或稍后回来看看。", maxLines = 2)
            }
            Spacer(Modifier.height(unit * 30))
            Row(horizontalArrangement = Arrangement.spacedBy(unit * 24)) {
                val failed = !loading && retryActions.isNotEmpty()
                ActionButton(if (failed) "重试" else "浏览媒体库", {
                    if (failed) retryActions.forEach { it.second() }
                    else actions.library(null, "媒体库")
                }, target("hero:play"), if (failed) Glyph.Refresh else null, primary = true, maxLines = 1)
                if (failed)
                    ActionButton("浏览媒体库", { actions.library(null, "媒体库") },
                        target("hero:detail"), maxLines = 1)
            }
        }
    }
    if (retryActions.isNotEmpty()) Row(Modifier.offset(x = unit * 96, y = unit * 728)
        .onPreviewKeyEvent { event ->
            if (event.key == Key.DirectionDown || event.key == Key.DirectionUp) {
                if (event.type == KeyEventType.KeyDown) {
                    if (event.key == Key.DirectionUp) request("hero:play")
                    else if (current != null) request("hero:candidate:${current.key}")
                    else nextPage()
                }
                true
            } else false
        }, horizontalArrangement = Arrangement.spacedBy(unit * 16), verticalAlignment = Alignment.CenterVertically) {
        Caption(if (current != null) "部分内容更新失败" else "加载失败，可分别重试", maxLines = 1)
        retryActions.forEach { (label, retry) ->
            ActionButton("重试$label", retry, target("hero:retry:$label"), quiet = true, maxLines = 1)
        }
    }
    if (state.items.isNotEmpty()) Row(Modifier.offset(x = unit * 96, y = unit * 802)
        .width(unit * 1728).height(unit * 212).testTag("hero:candidates")
        .onPreviewKeyEvent { event ->
            when (event.key) {
                Key.DirectionLeft, Key.DirectionRight -> {
                    if (event.type == KeyEventType.KeyDown) {
                        val index = (state.selectedIndex + if (event.key == Key.DirectionLeft) -1 else 1)
                            .coerceIn(0, state.items.lastIndex)
                        val item = state.items[index]
                        state.select(item.key)
                        request("hero:candidate:${item.key}")
                    }
                    true
                }
                Key.DirectionUp -> {
                    if (event.type == KeyEventType.KeyDown) request("hero:play")
                    true
                }
                Key.DirectionDown -> {
                    if (event.type == KeyEventType.KeyDown) nextPage()
                    true
                }
                else -> false
            }
        }, horizontalArrangement = Arrangement.spacedBy(unit * 24)) {
        state.items.forEach { item -> key(item.key) {
            val selected = item.key == current?.key
            var focused by remember { mutableStateOf(false) }
            val lift by animateFloatAsState(if (focused) 1f else 0f,
                animationSpec = rememberMotionSpec(MotionTokens.FocusDurationMs), label = "featured-focus")
            val tint = state.artwork?.tint?.takeIf { it.alpha > 0f } ?: Palette.Text
            val shape = RoundedCornerShape(unit * 10)
            Column(target("hero:candidate:${item.key}").width(unit * 268)
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) state.select(item.key) }
                .semantics { this.selected = selected; contentDescription = "${item.title}，${item.reason.label}" }
                .stableAction(true) { execute(item) }.zIndex(if (focused) 1f else 0f)
                .graphicsLayer {
                    scaleX = 1f + .025f * lift
                    scaleY = 1f + .025f * lift
                    translationY = -(unit * 6).toPx() * lift
                }) {
                Box(Modifier.fillMaxWidth().height(unit * 12), contentAlignment = Alignment.TopCenter) {
                    if (selected && !focused) Box(Modifier.width(unit * 32).height(unit * 3)
                        .background(Palette.Text, RoundedCornerShape(unit * 2))
                        .testTag("hero:selection:${item.key}"))
                }
                Box(Modifier.fillMaxWidth().height(unit * 151).testTag("hero:artwork:${item.key}")
                    .drawWithCache {
                        val spread = (unit * 24).toPx()
                        val glow = Brush.radialGradient(listOf(tint.copy(alpha = .24f * lift), Color.Transparent),
                            center = Offset(size.width / 2, size.height / 2), radius = size.width * .7f)
                        onDrawBehind {
                            drawRect(glow, topLeft = Offset(-spread, -spread),
                                size = Size(size.width + spread * 2, size.height + spread * 2))
                        }
                    }
                    .border(unit * 4, if (focused) Palette.BorderFocus else Color.Transparent, shape)
                    .padding(unit * 5).clip(shape)) {
                    val horizontal = item.media.backdropTag.isNotBlank()
                    Artwork(if (horizontal) images.thumbnail(item.media) else images.poster(item.media), images,
                        Modifier.fillMaxSize(), contentScale = if (horizontal) ContentScale.Crop else ContentScale.Fit)
                    item.progress?.let { progress ->
                        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progress).height(unit * 4)
                            .background(Palette.Text).semantics {
                                progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                            })
                    }
                }
                Text(item.title, Modifier.padding(top = unit * 10).fillMaxWidth(),
                    color = if (selected || focused) Palette.Text else Palette.Muted,
                    fontSize = (unit.value * 21).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } }
    }
}
