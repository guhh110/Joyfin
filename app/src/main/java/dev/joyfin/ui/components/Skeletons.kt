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
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.loading.LoadingPolicy
import dev.joyfin.ui.theme.Palette
import kotlin.math.ceil
import kotlinx.coroutines.delay

private val LocalSkeletonVisibility = staticCompositionLocalOf<Boolean?> { null }

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
            .background(Palette.Surface, RoundedCornerShape(7.dp))
            .clearAndSetSemantics {}
    )
}

@Composable
fun SkeletonTextLine(fontSize: TextUnit, lineHeight: TextUnit, fraction: Float = .8f) {
    Box(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
        Text("\u00a0", fontSize = fontSize, lineHeight = lineHeight, minLines = 1, maxLines = 1)
        SkeletonBlock(
            Modifier.fillMaxWidth(fraction).height(fontSize.value.dp).align(Alignment.CenterStart)
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
) {
    CompositionLocalProvider(LocalSkeletonVisibility provides true) {
        Column(modifier.alpha(if (visible) 1f else 0f).padding(6.dp).clearAndSetSemantics {}) {
            SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(if (wide) 16f / 9f else 2f / 3f))
            Spacer(Modifier.height(10.dp))
            SkeletonTextLine(14.sp, 20.sp)
            SkeletonTextLine(12.sp, 19.sp, .5f)
        }
    }
}

@Composable
fun RailSkeleton(wide: Boolean = false, modifier: Modifier = Modifier) {
    val tv = LocalDeviceLayout.current.television
    val width = if (wide) if (tv) 260.dp else 232.dp else if (tv) 156.dp else 132.dp
    SkeletonRegion(modifier) {
        BoxWithConstraints {
            val count =
                ceil(
                        ((maxWidth - LocalDeviceLayout.current.gutter * 2 + 10.dp) /
                                (width + 10.dp))
                            .toDouble()
                    )
                    .toInt()
                    .coerceAtLeast(1) + 1
            LazyRow(
                Modifier.clearAndSetSemantics {},
                userScrollEnabled = false,
                contentPadding = PaddingValues(horizontal = LocalDeviceLayout.current.gutter),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                            Modifier.width(104.dp).padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SkeletonBlock(
                                Modifier.size(88.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                            )
                            SkeletonTextLine(16.sp, 24.sp)
                            SkeletonTextLine(12.sp, 19.sp, .5f)
                        }
                    }
                }
            }
        }
    }
}
