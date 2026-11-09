package dev.joyfin.ui.features.tracking

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.joyfin.browse.TrackingUiState
import dev.joyfin.data.*
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.BrowseActions
import kotlin.math.floor

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TrackingPosterGrid(
    entries: List<TrackingEntry>, ui: TrackingUiState, actions: BrowseActions,
    images: ArtworkSource, onRemove: (String) -> Unit,
) {
    val scroll = rememberLazyGridState()
    var optionsId by rememberSaveable { mutableStateOf<String?>(null) }
    val pinStart = rememberTrackingStartPinned { scroll.isScrollInProgress }
    LaunchedEffect(entries.map { it.series.id }) {
        if (pinStart && !scroll.isScrollInProgress) scroll.requestScrollToItem(0)
    }
    val platformScroll = LocalBringIntoViewSpec.current
    val rowScroll = remember(platformScroll, scroll) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                val visible = scroll.layoutInfo.visibleItemsInfo
                val center = offset + size / 2
                val item = visible.firstOrNull { center >= it.offset.y && center <= it.offset.y + it.size.height }
                    ?: return platformScroll.calculateScrollDistance(offset, size, containerSize)
                val rowHeight = visible.filter { it.row == item.row }.maxOf { it.size.height }
                return if (item.offset.y >= 0 && item.offset.y + rowHeight <= containerSize) 0f
                    else platformScroll.calculateScrollDistance(item.offset.y.toFloat(), rowHeight.toFloat(), containerSize)
            }
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides rowScroll) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val gutter = LocalDeviceLayout.current.gutter
            val minWidth = 150.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
            val columns = floor(((maxWidth - gutter * 2 + 14.dp) / (minWidth + 14.dp)).toDouble()).toInt().coerceAtLeast(1)
            LazyVerticalGrid(
                GridCells.Fixed(columns), Modifier.fillMaxSize().testTag("tracking:list"), state = scroll,
                contentPadding = PaddingValues(start = gutter, end = gutter, top = 2.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(entries, key = { it.series.id }) { entry ->
                    TrackingPoster(entry, actions, images, onOptions = { optionsId = entry.series.id })
                }
            }
        }
    }
    entries.firstOrNull { it.series.id == optionsId }?.let { entry ->
        TrackingPosterOptions(entry, ui, actions, onDismiss = { optionsId = null }, onRemove = {
            optionsId = null
            onRemove(entry.series.id)
        })
    }
}

@Composable
private fun TrackingPoster(
    entry: TrackingEntry, actions: BrowseActions,
    images: ArtworkSource, onOptions: () -> Unit,
) {
    val subtitle = when (entry.progress?.status) {
        TrackingStatus.WATCHING, TrackingStatus.WAITING -> entry.playLabel()
        TrackingStatus.CAUGHT_UP -> if (entry.progress.unplayed > 0) "已追平 · 旧集未看" else "已追平 · 等待更新"
        TrackingStatus.UNAVAILABLE -> "暂无可播放剧集"
        null -> if (entry.error != null) "进度读取失败 · 长按重试" else "正在读取观看进度"
    }
    MediaCard(
        entry.series.copy(unplayedCount = entry.progress?.unplayed,
            played = entry.progress?.status == TrackingStatus.CAUGHT_UP && entry.progress.unplayed == 0),
        images, {
            entry.progress?.target?.let { actions.play(it, false, null) } ?: actions.open(entry.series)
        }, Modifier.fillMaxWidth().semantics {
            entry.error?.let { stateDescription = "进度更新失败：$it" }
            customActions = listOf(CustomAccessibilityAction("更多追剧操作") { onOptions(); true })
        },
        focusKey = "tracking:play:${entry.series.id}", subtitle = subtitle,
        onLongClick = onOptions,
    )
}

@Composable
private fun TrackingPosterOptions(
    entry: TrackingEntry, ui: TrackingUiState, actions: BrowseActions,
    onDismiss: () -> Unit, onRemove: () -> Unit,
) {
    Sheet(entry.series.name, onDismiss, preferredWidth = 400.dp,
        initialFocus = "tracking:detail:${entry.series.id}") {
        Caption(entry.statusLabel())
        entry.error?.let { Caption("进度读取失败：$it") }
        entry.progress?.target?.let { Caption("S${it.season} E${it.episode} · ${it.name}") }
        ChoiceRow("查看剧集", Modifier.focusTarget("tracking:detail:${entry.series.id}"),
            leading = { AppIcon(Glyph.Info) }) {
            onDismiss()
            actions.open(entry.series)
        }
        if (entry.error != null) ChoiceRow(
            if (entry.loading) "正在更新进度…" else "重试进度",
            Modifier.focusTarget("tracking:retry:${entry.series.id}"), enabled = !entry.loading,
            leading = { AppIcon(Glyph.Refresh) },
        ) { actions.retryTracking(entry.series.id) }
        ChoiceRow("取消追剧", Modifier.focusTarget("tracking:remove:${entry.series.id}"),
            enabled = ui.removingId == null, leading = { AppIcon(Glyph.Heart) }, onClick = onRemove)
    }
}
