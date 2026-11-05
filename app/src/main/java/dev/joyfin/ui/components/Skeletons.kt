package dev.joyfin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.loading.LoadingPolicy
import dev.joyfin.ui.theme.Palette
import kotlin.math.ceil
import kotlinx.coroutines.delay

private val LocalSkeletonVisibility = staticCompositionLocalOf<Boolean?> { null }

/** Optional preview renderer; production always uses the normal skeleton. */
internal val LocalCardSkeletonOverride = staticCompositionLocalOf<(@Composable (Modifier, Boolean, Boolean, Boolean) -> Unit)?> { null }

/** The timer is keyed to the request, rather than to individual placeholder cells. */
@Composable
fun rememberSkeletonVisible(loading: Boolean, requestKey: Any? = Unit): Boolean {
    var visible by remember(loading, requestKey) { mutableStateOf(false) }
    LaunchedEffect(loading, requestKey) {
        if (loading) {
            delay(LoadingPolicy.SkeletonDelayMillis)
            visible = true
        }
    }
    return visible
}

@Composable
fun SkeletonRegion(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val visible = rememberSkeletonVisible(true)
    CompositionLocalProvider(LocalSkeletonVisibility provides true) {
        Box(modifier.alpha(if (visible) 1f else 0f).semantics { stateDescription = "正在加载" }) {
            content()
        }
    }
}

/** Static blocks have no focus targets, shimmer or per-card animation. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier) {
    val visible = LocalSkeletonVisibility.current ?: rememberSkeletonVisible(true)
    Box(
        modifier
            .alpha(if (visible) 1f else 0f)
            .background(Palette.Surface, RoundedCornerShape(MediaCardLayout.Corner))
            .clearAndSetSemantics {}
    )
}

@Composable
fun SkeletonTextLine(fontSize: TextUnit, lineHeight: TextUnit, fraction: Float = .8f) {
    val density = LocalDensity.current
    val height = with(density) { lineHeight.toDp() }
    val blockHeight = with(density) { fontSize.toDp() }
    Box(Modifier.fillMaxWidth().height(height).clearAndSetSemantics {}) {
        SkeletonBlock(
            Modifier.fillMaxWidth(fraction).height(blockHeight).align(Alignment.CenterStart)
        )
    }
}

@Composable
fun SkeletonText(modifier: Modifier = Modifier, lines: Int = 3) {
    SkeletonRegion(modifier) {
        Column {
            repeat(lines) { SkeletonTextLine(14.sp, 23.sp, if (it == lines - 1) .6f else 1f) }
        }
    }
}

@Composable
fun MediaCardSkeleton(
    modifier: Modifier = Modifier,
    wide: Boolean = false,
    visible: Boolean = rememberSkeletonVisible(true),
    showSubtitle: Boolean = true,
) {
    val override = LocalCardSkeletonOverride.current
    if (override != null) {
        override(modifier, wide, visible, showSubtitle)
        return
    }
    CompositionLocalProvider(LocalSkeletonVisibility provides true) {
        Column(
            modifier
                .alpha(if (visible) 1f else 0f)
                .padding(vertical = dev.joyfin.ui.theme.MotionTokens.MediaCardVerticalReserve)
                .padding(MediaCardLayout.ContentInset * 2)
                .clearAndSetSemantics {}
        ) {
            SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(MediaCardLayout.ratio(wide)))
            Spacer(Modifier.height(MediaCardLayout.TextGap))
            RailTextSlot(MediaCardLayout.TitleSlot) { SkeletonTextLine(15.sp, 20.sp) }
            if (showSubtitle) RailTextSlot(MediaCardLayout.SubtitleSlot) { SkeletonTextLine(12.sp, 18.sp, .5f) }
        }
    }
}

@Composable
fun RailSkeleton(wide: Boolean = false, modifier: Modifier = Modifier, cardWidth: Dp? = null) {
    val tv = LocalDeviceLayout.current.television
    val width = cardWidth ?: MediaCardLayout.width(tv, wide)
    SkeletonRegion(modifier) {
        BoxWithConstraints {
            val count =
                ceil(
                        ((maxWidth - LocalDeviceLayout.current.gutter * 2 + MediaCardLayout.RailGap) /
                                (width + MediaCardLayout.RailGap))
                            .toDouble()
                    )
                    .toInt()
                    .coerceAtLeast(1) + 1
            LazyRow(
                Modifier.clearAndSetSemantics {},
                userScrollEnabled = false,
                contentPadding = PaddingValues(horizontal = LocalDeviceLayout.current.gutter),
                horizontalArrangement = Arrangement.spacedBy(MediaCardLayout.RailGap),
            ) {
                items(count) { MediaCardSkeleton(Modifier.width(width), wide, visible = true) }
            }
        }
    }
}

@Composable
fun PeopleRailSkeleton() {
    SkeletonRegion {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionTitle("演员与主创", Modifier.padding(horizontal = LocalDeviceLayout.current.gutter))
            BoxWithConstraints {
                val count =
                    ceil(
                            ((maxWidth - LocalDeviceLayout.current.gutter * 2 + 14.dp) / 118.dp)
                                .toDouble()
                        )
                        .toInt()
                        .coerceAtLeast(1) + 1
                LazyRow(
                    contentPadding = PaddingValues(horizontal = LocalDeviceLayout.current.gutter),
                    userScrollEnabled = false,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(count) {
                        Column(
                            Modifier.width(104.dp)
                                .padding(vertical = dev.joyfin.ui.theme.MotionTokens.PeopleCardVerticalReserve)
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SkeletonBlock(
                                Modifier.size(88.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                            )
                            RailTextSlot(MediaCardLayout.TitleSlot) { SkeletonTextLine(15.sp, 22.sp) }
                            RailTextSlot(MediaCardLayout.SubtitleSlot) { SkeletonTextLine(12.5.sp, 18.sp, .5f) }
                        }
                    }
                }
            }
        }
    }
}
