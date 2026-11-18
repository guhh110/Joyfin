package dev.joyfin.ui.features.home.tv

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.browse.HomeRegion
import dev.joyfin.data.Media
import dev.joyfin.ui.components.*
import dev.joyfin.ui.features.home.components.LocalResumeQuickAction
import dev.joyfin.ui.interaction.LocalScreenFocus
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.BrowseActions
import dev.joyfin.ui.theme.Palette
import dev.joyfin.ui.theme.rememberMotionSpec
import kotlinx.coroutines.launch

@Composable
internal fun TvHomeCollection(section: TvHomeSection, images: ArtworkSource, actions: BrowseActions,
    unit: Dp, selectedId: String?, onSelected: (String) -> Unit, active: Boolean) {
    var previousError by remember(section.key) { mutableStateOf(section.load.error) }
    val error = section.load.error ?: previousError.takeIf { section.load.loading }
    SideEffect { if (!section.load.loading || section.load.error != null) previousError = section.load.error }
    fun retry() {
        when (section.key) {
            "resume" -> actions.retryHome(HomeRegion.RESUME)
            "latest" -> actions.retryHome(HomeRegion.LATEST)
            else -> section.libraryId?.let(actions.retryLibrary) ?: actions.retryHome(HomeRegion.LIBRARIES)
        }
    }
    fun more() {
        when (section.key) {
            "latest" -> actions.allMedia()
            else -> section.libraryId?.let { actions.libraryWorks(it, section.title) }
                ?: actions.library(null, "媒体库")
        }
    }
    @Composable
    fun target(key: String) = Modifier.focusProperties { canFocus = active }.focusTarget(key, active)
    Text(section.title, Modifier.offset(x = unit * 96, y = unit * 198)
        .width(unit * 1600), color = Palette.Text, fontFamily = FontFamily.SansSerif,
        fontSize = (unit.value * 50).sp, lineHeight = (unit.value * 72).sp,
        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)

    if (section.items.isEmpty()) {
        Column(Modifier.offset(y = unit * 332).fillMaxWidth()
            .padding(horizontal = unit * 96), verticalArrangement = Arrangement.spacedBy(unit * 24)) {
            when {
                section.key == "layout" -> ActionButton("调整首页栏目", actions.settings,
                    target("home:layout"), primary = true)
                error != null -> {
                    Caption(browseErrorText(error), maxLines = 2)
                    ActionButton("重试", ::retry, target(section.retryKey), Glyph.Refresh,
                        primary = true, loading = section.load.loading)
                }
                section.load.loading -> TvHomeRailSkeleton(unit)
                else -> {
                    Caption("暂无内容")
                    if (section.hasMore) ActionButton("查看全部", ::more, target(section.moreKey))
                }
            }
        }
        return
    }

    var lastIndex by rememberSaveable(section.key) { mutableIntStateOf(0) }
    val selectedIndex = section.items.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 }
        ?: lastIndex.coerceAtMost(section.items.lastIndex)
    val selected = section.items[selectedIndex]
    SideEffect {
        lastIndex = selectedIndex
        if (selected.id != selectedId) onSelected(selected.id)
    }
    val row = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 1).coerceAtLeast(0))
    val focus = LocalScreenFocus.current
    val scope = rememberCoroutineScope()
    val quickActions = LocalResumeQuickAction.current
    val keys = section.items.map(section::mediaKey) +
        listOfNotNull(section.retryKey.takeIf { error != null }, section.moreKey.takeIf { section.hasMore })
    LaunchedEffect(selected.id, active) {
        if (active) row.animateScrollToItem((selectedIndex - 1).coerceAtLeast(0))
    }
    LazyRow(
        modifier = Modifier.offset(y = unit * 320).fillMaxWidth().height(unit * 514)
            .testTag("rail:${section.key}")
            .onPreviewKeyEvent { event ->
                if (event.key != Key.DirectionLeft && event.key != Key.DirectionRight) false
                else {
                    if (event.type == KeyEventType.KeyDown) {
                        val index = keys.indexOf(focus?.target?.invoke()).takeIf { it >= 0 } ?: selectedIndex
                        val next = (index + if (event.key == Key.DirectionLeft) -1 else 1).coerceIn(0, keys.lastIndex)
                        val key = keys[next]
                        focus?.rememberTarget?.invoke(key)
                        section.items.getOrNull(next)?.let { onSelected(it.id) }
                        focus?.requesters?.get(key)?.requestFocus()
                        if (next >= section.items.size) scope.launch {
                            row.animateScrollToItem((next - 1).coerceAtLeast(0))
                            if (focus?.target?.invoke() == key) focus.restore()
                        }
                    }
                    true
                }
            },
        state = row,
        contentPadding = PaddingValues(horizontal = unit * 96, vertical = unit * 12),
        horizontalArrangement = Arrangement.spacedBy(unit * 40),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(section.items, key = { _, item -> section.mediaKey(item) }) { index, item ->
            TvHomeFocusCard(item, images, unit, index == selectedIndex,
                modifier = target(section.mediaKey(item)).onFocusChanged {
                    if (it.isFocused) onSelected(item.id)
                },
                onLongClick = if (section.key == "resume") ({ quickActions(item) }) else null,
                onClick = { actions.open(item) })
        }
        if (error != null) item(section.retryKey) {
            TvHomeRailAction("重试", Glyph.Refresh, unit, target(section.retryKey), ::retry,
                loading = section.load.loading)
        }
        if (section.hasMore) item(section.moreKey) {
            TvHomeRailAction("查看全部", Glyph.Next, unit, target(section.moreKey), ::more)
        }
    }
    if (error != null) Caption(browseErrorText(error), Modifier.offset(x = unit * 96, y = unit * 862)
        .width(unit * 1550), maxLines = 2)
}

@Composable
private fun TvHomeFocusCard(item: Media, images: ArtworkSource, unit: Dp, expanded: Boolean,
    modifier: Modifier, onLongClick: (() -> Unit)?, onClick: () -> Unit) {
    val width by animateDpAsState(unit * if (expanded) 864 else 280,
        animationSpec = rememberMotionSpec(240), label = "homeCardWidth")
    val imageHeight by animateDpAsState(unit * if (expanded) 486 else 420,
        animationSpec = rememberMotionSpec(240), label = "homeCardHeight")
    val landscape by animateFloatAsState(if (expanded) 1f else 0f,
        animationSpec = rememberMotionSpec(180), label = "homeCardLandscape")
    val opacity by animateFloatAsState(if (expanded) 1f else .72f,
        animationSpec = rememberMotionSpec(140), label = "homeCardOpacity")
    val shape = RoundedCornerShape(unit * 12)
    Column(modifier.width(width).height(unit * 486).semantics { selected = expanded }
        .stableAction(true, onLongClick = onLongClick, onClick = onClick).alpha(opacity)) {
        Box(Modifier.fillMaxWidth().height(imageHeight)
            .then(if (expanded) Modifier.border(unit * 3, Palette.BorderFocus, shape) else Modifier)
            .clip(shape)) {
            if (item.type == "Episode") EpisodeArtwork(item, images, Modifier.fillMaxSize(),
                imageRatio = if (expanded) 16f / 9f else 2f / 3f)
            else {
                Artwork(images.poster(item), images, Modifier.fillMaxSize(), item.name)
                if (landscape > 0f) Artwork(images.backdrop(item), images,
                    Modifier.fillMaxSize().alpha(landscape), item.name)
            }
            if (expanded) {
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                    .3f to Color.Transparent, 1f to Color.Black.copy(alpha = .92f))))
                Text(item.seriesName.ifBlank { item.name }, Modifier.align(Alignment.BottomStart)
                    .padding(horizontal = unit * 32, vertical = unit * 38),
                    color = Palette.Text, fontFamily = FontFamily.SansSerif,
                    fontSize = (unit.value * 42).sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (item.resumeTicks > 0 && item.ticks > 0) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(unit * 5)
                    .background(Color.Black.copy(alpha = .55f))) {
                    Box(Modifier.fillMaxWidth((item.resumeTicks.toFloat() / item.ticks).coerceIn(.02f, 1f))
                        .fillMaxHeight().background(Palette.Text))
                }
            }
        }
        if (!expanded) Text(item.seriesName.ifBlank { item.name },
            Modifier.fillMaxWidth().padding(top = unit * 18), color = Palette.Text,
            fontFamily = FontFamily.SansSerif, fontSize = (unit.value * 27).sp,
            lineHeight = (unit.value * 39).sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TvHomeRailAction(text: String, glyph: Glyph, unit: Dp, modifier: Modifier,
    onClick: () -> Unit, loading: Boolean = false) {
    FocusSurface(onClick, modifier.width(unit * 280).height(unit * 420).semantics {
        if (loading) {
            stateDescription = "正在加载"
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        }
    },
        enabled = !loading, focusedScale = 1f) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(unit * 24)) {
            if (loading) LoadingSpinner() else AppIcon(glyph)
            Text(text, color = Palette.Text, fontFamily = FontFamily.SansSerif,
                fontSize = (unit.value * 27).sp, maxLines = 1)
        }
    }
}

@Composable
private fun TvHomeRailSkeleton(unit: Dp) {
    SkeletonRegion {
        Row(horizontalArrangement = Arrangement.spacedBy(unit * 40)) {
            SkeletonBlock(Modifier.size(unit * 864, unit * 486))
            repeat(3) { SkeletonBlock(Modifier.size(unit * 280, unit * 420)) }
        }
    }
}
