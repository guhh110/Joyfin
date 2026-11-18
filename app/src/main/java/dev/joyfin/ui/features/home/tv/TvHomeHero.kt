package dev.joyfin.ui.features.home.tv

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.browse.TrackingUiState
import dev.joyfin.browse.HomeRegion
import dev.joyfin.data.Home
import dev.joyfin.data.Media
import dev.joyfin.data.playLabel
import dev.joyfin.ui.components.*
import dev.joyfin.ui.features.home.components.*
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.BrowseActions
import dev.joyfin.ui.theme.Palette
import dev.joyfin.ui.theme.rememberMotionSpec

@Composable
internal fun TvHomeBackdrop(item: Media?, images: ArtworkSource, collection: Boolean = false,
    content: (@Composable () -> Unit)? = null) {
    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        if (content != null) content()
        else Crossfade(item, animationSpec = rememberMotionSpec(180), label = "homeSectionBackdrop") {
            if (it != null) Artwork(images.backdrop(it), images, Modifier.fillMaxSize(),
                alignment = Alignment.CenterEnd)
        }
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
            0f to Palette.Background.copy(alpha = if (collection) .96f else .90f),
            .43f to Palette.Background.copy(alpha = if (collection) .84f else .44f),
            1f to Palette.Background.copy(alpha = if (collection) .71f else .02f),
        )))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to Palette.Background.copy(alpha = .43f),
            .28f to Color.Transparent,
            .55f to Color.Transparent,
            1f to Palette.Background,
        )))
    }
}

@Composable
internal fun TvHomeHero(home: Home, result: HomeResultPresentation, carousel: HomeCarouselState,
    tracking: TrackingUiState?, actions: BrowseActions, unit: Dp, active: Boolean, modifier: Modifier = Modifier) {
    val item = carousel.current
    val largeFont = LocalDensity.current.fontScale > 1.15f
    val entry = tracking?.entries?.firstOrNull { it.series.id == item?.id }
    val rawError = entry?.error ?: tracking?.error
    val retryLoading = entry?.loading == true || tracking?.loading == true
    var previousError by remember(item?.id) { mutableStateOf<String?>(null) }
    val error = rawError ?: previousError.takeIf { retryLoading }
    SideEffect { if (!retryLoading || rawError != null) previousError = rawError }
    DisposableEffect(carousel, active) {
        carousel.visible = active
        onDispose { carousel.visible = false; carousel.focused = false }
    }
    Column(modifier.widthIn(max = unit * 1200).testTag("home:hero-content")
        .onFocusChanged { carousel.focused = it.hasFocus }
        .focusGroup()
        .onPreviewKeyEvent { event ->
            if (item != null && error == null && (event.key == Key.DirectionLeft || event.key == Key.DirectionRight)) {
                if (event.type == KeyEventType.KeyDown) carousel.step(if (event.key == Key.DirectionLeft) -1 else 1)
                true
            } else false
        }, verticalArrangement = Arrangement.spacedBy(unit * 22)) {
        when {
            item != null && (!result.visible || tracking != null) -> {
                Text(item.seriesName.ifBlank { item.name }, color = Palette.Text,
                    fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
                    fontSize = (unit.value * 100).sp, lineHeight = (unit.value * 124).sp,
                    maxLines = if (largeFont) 1 else 2, overflow = TextOverflow.Ellipsis)
                val target = entry?.progress?.target ?: item
                val metadata = listOfNotNull(item.year.takeIf { it > 0 }?.toString(),
                    if (entry?.progress?.target != null) "S${target.season} · E${target.episode}"
                    else item.genres.firstOrNull()).joinToString(" · ")
                if (metadata.isNotBlank()) Text(metadata, color = Palette.Muted,
                    fontFamily = FontFamily.SansSerif, fontSize = (unit.value * 26).sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(unit * 4))
                Row(horizontalArrangement = Arrangement.spacedBy(unit * 24)) {
                    ActionButton(entry?.playLabel() ?: if (item.resumeTicks > 0) "继续观看"
                        else if (item.isFolder) "查看剧集" else "播放", {
                        if (target.isFolder) actions.open(target) else actions.play(target, false, null)
                    }, Modifier.focusProperties { canFocus = active }.focusTarget("hero:play", active),
                        Glyph.Play, primary = true, maxLines = 1)
                    if (error != null) ActionButton("重试", {
                        if (entry?.error != null) actions.retryTracking(entry.series.id) else actions.refreshTracking()
                    }, Modifier.focusProperties { canFocus = active }.focusTarget("hero:retry", active),
                        Glyph.Refresh, loading = retryLoading)
                }
                if (error != null) Caption(if (entry?.error != null) "进度更新失败，当前显示上次进度"
                    else "追剧刷新失败", maxLines = 1)
            }
            result.visible -> {
                FilmTitle(if (result.error != null) "暂时无法加载媒体库" else "媒体库还是空的", large = true)
                Caption(result.error?.let(::browseErrorText) ?: "在服务器中添加影片或剧集后，回到这里刷新。", maxLines = 2)
                ActionButton(if (result.error != null) "重新加载" else "刷新媒体库", {
                    HomeRegion.entries.forEach(actions.retryHome)
                }, Modifier.focusProperties { canFocus = active }.focusTarget("hero:refresh", active),
                    Glyph.Refresh, primary = true,
                    loading = listOf(home.resumeLoad, home.latestLoad, home.librariesLoad).any { it.loading })
            }
            tracking != null -> TrackingHeroPlaceholder(tracking, actions, active = active)
            else -> SkeletonText(Modifier.width(unit * 680), lines = 3)
        }
    }
}
