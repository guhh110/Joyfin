package dev.joyfin.ui.features.tracking

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import dev.joyfin.browse.TrackingUiState
import dev.joyfin.data.*
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.LocalScreenFocus
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.BrowseActions

@Composable
fun TrackingRoute(ui: TrackingUiState, actions: BrowseActions, images: ArtworkSource) {
    val device = LocalDeviceLayout.current
    var filter by rememberSaveable { mutableStateOf("all") }
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    val scroll = rememberLazyListState()
    val focus = LocalScreenFocus.current
    val sorted = remember(ui.entries, filter) {
        ui.entries.sortedWith(trackingOrder).filter {
            filter == "all" || it.progress?.status?.name == filter
        }
    }
    val pinStart = rememberTrackingStartPinned { scroll.isScrollInProgress }
    LaunchedEffect(sorted.map { it.series.id }) {
        // Progress arrives incrementally. Reordering must not anchor the initial viewport
        // to an unfinished item that later moves to the end of the list.
        if (!device.television && pinStart && !scroll.isScrollInProgress) scroll.requestScrollToItem(0)
    }
    // Saveable list state and stable series keys restore both scroll and focus after Details.
    val ids = sorted.map { it.series.id }
    LaunchedEffect(ids) {
        val target = focus?.target?.invoke().orEmpty()
        if (target.substringBeforeLast(':') in setOf("tracking:detail", "tracking:play", "tracking:remove", "tracking:retry") &&
            target.substringAfterLast(':') !in ids) {
            focus?.rememberTarget?.invoke("tracking:filter:$filter")
            withFrameNanos { }
            focus?.restore()
        }
    }
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    var pull by remember { mutableFloatStateOf(0f) }
    val refresh by rememberUpdatedState(actions.refreshTracking)
    val busy by rememberUpdatedState(ui.loading)
    val connection = remember(scroll, threshold) { object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput && available.y < 0 && pull > 0) {
                val consume = available.y.coerceAtLeast(-pull)
                pull += consume
                return Offset(0f, consume)
            }
            return Offset.Zero
        }
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput && available.y > 0 && !scroll.canScrollBackward && !busy) {
                pull = (pull + available.y * .5f).coerceAtMost(threshold * 1.5f)
                return Offset(0f, available.y)
            }
            return Offset.Zero
        }
        override suspend fun onPreFling(available: Velocity): Velocity {
            val pulled = pull > 0
            if (pull >= threshold && !busy) refresh()
            pull = 0f
            return if (pulled) available else Velocity.Zero
        }
    } }
    Column(Modifier.fillMaxSize()) {
        AppHeader(actions, active = "tracking")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = device.gutter, vertical = 4.dp)
                .testTag("tracking:toolbar"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (device.television) SectionTitle("我的追剧", Modifier.weight(1f))
            Row(
                (if (device.television) Modifier else Modifier.weight(1f)).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf("all" to "全部", "WAITING" to "待看", "WATCHING" to "观看中", "CAUGHT_UP" to "已追平")
                    .forEach { (key, label) ->
                        TextTab(label, { filter = key }, selected = filter == key,
                            modifier = Modifier.focusTarget("tracking:filter:$key"), compact = !device.television)
                    }
            }
            IconButton("刷新追剧", Glyph.Refresh, actions.refreshTracking,
                Modifier.focusTarget("tracking:refresh"), loading = ui.loading)
        }
        Column(Modifier.padding(horizontal = device.gutter), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ui.error?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Caption(it, Modifier.weight(1f), maxLines = 2)
                    ActionButton("重试读取名单", actions.refreshTracking, Modifier.focusTarget("tracking:retry"), quiet = true)
                }
            }
            ui.actionError?.let { Caption(it) }
            if (!ui.loaded && ui.error == null) SkeletonText(Modifier.fillMaxWidth(), 3)
            if (ui.empty) {
                Caption("收藏整部剧，即可在这里追剧")
                ActionButton("浏览媒体库", { actions.library(null, "媒体库") }, Modifier.focusTarget("tracking:library"))
            } else if (ui.loaded && sorted.isEmpty()) Caption("这个分类暂时没有剧集")
        }
        if (device.television) {
            TrackingPosterGrid(sorted, ui, actions, images, onRemove = { removing = it })
        } else {
            if (pull > 0) Caption(
                if (pull >= threshold) "松开刷新" else "下拉刷新",
                Modifier.align(Alignment.CenterHorizontally).padding(8.dp),
            )
            LazyColumn(
                Modifier.fillMaxSize().testTag("tracking:list").nestedScroll(connection),
                state = scroll, contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(sorted, key = { it.series.id }, contentType = { "tracking-series" }) { entry ->
                    TrackingMobileRow(entry, ui, actions, images, onRemove = { removing = it })
                }
            }
        }
    }
    removing?.let { id ->
        ConfirmDialog("取消追剧？这也会取消该剧在 Jellyfin 中的收藏。", { removing = null }, {
            removing = null
            actions.removeTracking(id)
        })
    }
}

@Composable
internal fun rememberTrackingStartPinned(isScrolling: () -> Boolean): Boolean {
    var pinned by rememberSaveable { mutableStateOf(true) }
    val target = LocalScreenFocus.current?.target?.invoke().orEmpty()
    LaunchedEffect(Unit) {
        snapshotFlow { isScrolling() }.collect { if (it) pinned = false }
    }
    LaunchedEffect(target) {
        if (listOf("tracking:detail:", "tracking:play:", "tracking:remove:", "tracking:retry:").any(target::startsWith)) pinned = false
    }
    return pinned
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrackingMobileRow(
    entry: TrackingEntry, ui: TrackingUiState, actions: BrowseActions,
    images: ArtworkSource, onRemove: (String) -> Unit,
) {
    Column(Modifier.padding(horizontal = LocalDeviceLayout.current.gutter).fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FocusSurface({ actions.open(entry.series) },
            Modifier.fillMaxWidth().focusTarget("tracking:detail:${entry.series.id}"), transparent = true) {
            Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Artwork(images.poster(entry.series), images,
                    Modifier.width(76.dp).aspectRatio(2f / 3f), entry.series.name)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle(entry.series.name)
                    Caption(entry.statusLabel())
                    entry.progress?.target?.let { Caption("S${it.season} E${it.episode} · ${it.name}") }
                    if (entry.loading) Caption("正在更新进度…")
                    entry.error?.let { Caption("进度读取失败：$it") }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(entry.playLabel(), {
                entry.progress?.target?.let { actions.play(it, false, null) } ?: actions.open(entry.series)
            }, Modifier.focusTarget("tracking:play:${entry.series.id}"),
                icon = if (entry.progress?.target != null) Glyph.Play else null, primary = true,
                enabled = entry.progress != null || entry.error != null)
            if (entry.error != null) ActionButton("重试进度", { actions.retryTracking(entry.series.id) },
                Modifier.focusTarget("tracking:retry:${entry.series.id}"), loading = entry.loading)
            ActionButton("取消追剧", { onRemove(entry.series.id) },
                Modifier.focusTarget("tracking:remove:${entry.series.id}"),
                loading = ui.removingId == entry.series.id, enabled = ui.removingId == null)
        }
    }
}
