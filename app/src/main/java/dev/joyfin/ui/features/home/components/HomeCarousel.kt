package dev.joyfin.ui.features.home.components

import androidx.compose.animation.*
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.joyfin.data.Media
import dev.joyfin.data.playLabel
import dev.joyfin.data.statusLabel
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.interaction.LocalScreenFocus
import dev.joyfin.ui.model.BrowseActions
import dev.joyfin.ui.theme.Palette
import dev.joyfin.ui.theme.rememberMotionSpec

@Composable
internal fun HomeCarouselBackdrop(state: HomeCarouselState) {
    val television = LocalDeviceLayout.current.television
    val fade = rememberMotionSpec<Float>(300)
    val slide = rememberMotionSpec<IntOffset>(300)
    if (television) {
        Crossfade(state.frame, Modifier.fillMaxSize(), animationSpec = fade, label = "heroBackground") { frame ->
            CarouselImage(frame)
        }
    } else {
        AnimatedContent(state.frame, Modifier.fillMaxSize(), transitionSpec = {
            (slideInHorizontally(slide) { it / 4 * state.direction } + fadeIn(fade)) togetherWith
                (slideOutHorizontally(slide) { -it / 4 * state.direction } + fadeOut(fade))
        }, label = "heroBackground") { frame -> CarouselImage(frame) }
    }
}

@Composable
private fun CarouselImage(frame: CarouselFrame?) {
    val tv = LocalDeviceLayout.current.television
    Box(Modifier.fillMaxSize()) {
        val request = frame?.request
        if (request != null) AsyncImage(request, contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            alignment = if (tv) Alignment.CenterEnd else Alignment.TopCenter)
    }
}

@Composable
internal fun HomeCarouselContent(state: HomeCarouselState, actions: BrowseActions, modifier: Modifier = Modifier, compact: Boolean = false) {
    val tv = LocalDeviceLayout.current.television
    val density = LocalDensity.current
    val focus = LocalScreenFocus.current
    val current = state.current ?: return
    val tracking = state.tracking
    val entry = tracking?.entries?.firstOrNull { it.series.id == current.id }
    val threshold = with(density) { 48.dp.toPx() }
    val gestures = if (tv) Modifier else Modifier.pointerInput(state) {
        var distance = 0f
        detectHorizontalDragGestures(
            onDragStart = { distance = 0f; state.dragging = true },
            onDragCancel = { state.dragging = false },
            onDragEnd = {
                state.dragging = false
                if (kotlin.math.abs(distance) >= threshold) state.step(if (distance < 0) 1 else -1)
            },
            onHorizontalDrag = { change, amount -> change.consume(); distance += amount },
        )
    }
    DisposableEffect(state) { onDispose { state.visible = false; state.focused = false; state.dragging = false } }
    Column(gestures.then(modifier).widthIn(max = if (tv) 640.dp else 520.dp)
        .onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            state.visible = bounds.width > 0 && bounds.height >= coordinates.size.height * .5f
        }
        .onFocusChanged { state.focused = it.hasFocus }
        .focusGroup()
        .onPreviewKeyEvent { event ->
            val delta = when {
                event.key == Key.DirectionLeft && focus?.target?.invoke() == "hero:play" -> -1
                event.key == Key.DirectionRight && focus?.target?.invoke() == "hero:detail" -> 1
                else -> 0
            }
            if (tv && state.items.size > 1 && delta != 0) {
                if (event.type == KeyEventType.KeyDown) state.step(delta)
                true
            } else false
        }
        .padding(horizontal = LocalDeviceLayout.current.gutter)
        .padding(bottom = if (compact) 4.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
        val titleSlot = if (compact) 40.sp else if (tv) 104.sp else 96.sp
        val headerHeight = with(density) { titleSlot.toDp() + if (compact) 0.dp else 82.sp.toDp() }
        Crossfade(current, Modifier.fillMaxWidth().height(headerHeight),
            animationSpec = rememberMotionSpec<Float>(250), label = "heroText") { item ->
            CarouselHeading(item, titleSlot, compact, tv)
        }
        if (entry != null) Caption(entry.statusLabel(), maxLines = 2)
        if (tracking?.empty == true) Caption("收藏剧集即可追剧")
        if (tracking?.error != null) {
            Caption("追剧刷新失败：${tracking.error}", maxLines = 2)
            ActionButton("重试追剧", actions.refreshTracking, Modifier.focusTarget("hero:retry"), quiet = true)
        } else if (entry?.error != null) {
            Caption("进度更新失败，当前显示上次进度", maxLines = 1)
            ActionButton("重试进度", { actions.retryTracking(entry.series.id) }, Modifier.focusTarget("hero:retry"), quiet = true)
        }
        Row(Modifier.widthIn(max = if (tv) 420.dp else 520.dp).fillMaxWidth().height(if (compact) 44.dp else 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton(entry?.playLabel() ?: if (current.resumeTicks > 0) "继续观看" else if (current.isFolder) "查看剧集" else "播放",
                { if (entry?.progress?.target != null) actions.play(entry.progress.target, false, null)
                    else if (current.isFolder) actions.open(current) else actions.play(current, false, null) },
                Modifier.weight(1f).focusTarget("hero:play"), Glyph.Play, primary = true, maxLines = 1)
            ActionButton(if (tracking?.empty == true) "我的追剧" else "查看详情",
                { if (tracking?.empty == true) actions.tracking() else actions.open(current) },
                Modifier.weight(1f).focusTarget("hero:detail"), maxLines = 1)
        }
    }
}

/** Tracking loading/errors must not fall through to the unrelated recent-media hero. */
@Composable
internal fun TrackingHeroPlaceholder(ui: dev.joyfin.browse.TrackingUiState, actions: BrowseActions,
    modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = LocalDeviceLayout.current.gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (ui.loading || !ui.loaded && ui.error == null) SkeletonText(Modifier.fillMaxWidth(), 3)
        else {
            FilmTitle(if (ui.error != null) "暂时无法加载追剧" else if (ui.empty) "收藏剧集即可追剧" else "我的追剧")
            Caption(ui.error ?: if (ui.empty) "收藏整部剧，即可在首页继续观看。" else "暂无可播放剧集，或部分剧集进度读取失败。")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton("我的追剧", actions.tracking, Modifier.focusTarget("hero:play"), primary = true)
            if (ui.error != null || ui.entries.any { it.error != null })
                ActionButton("重试", actions.refreshTracking, Modifier.focusTarget("hero:detail"))
            else ActionButton("浏览媒体库", { actions.library(null, "媒体库") }, Modifier.focusTarget("hero:detail"))
        }
    }
}

@Composable
private fun CarouselHeading(item: Media, titleSlot: androidx.compose.ui.unit.TextUnit, compact: Boolean, tv: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RailTextSlot(titleSlot) {
            Text(item.seriesName.ifBlank { item.name }, color = Palette.Text,
                fontSize = if (compact) 26.sp else if (tv) 40.sp else 32.sp,
                lineHeight = if (compact) 32.sp else if (tv) 48.sp else 40.sp,
                fontWeight = FontWeight.SemiBold, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
        }
        if (!compact) {
            RailTextSlot(22.sp) {
                Caption(listOfNotNull(item.year.takeIf { it > 0 }?.toString(), item.genres.firstOrNull()).joinToString(" · "), maxLines = 1)
            }
            RailTextSlot(44.sp) { if (item.overview.isNotBlank()) Caption(item.overview, maxLines = 2) }
        }
    }
}
