package dev.joyfin.ui.features.player.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.data.Media
import dev.joyfin.playback.PlayerActions
import dev.joyfin.playback.PlayerUiState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.LocalScreenFocus
import dev.joyfin.ui.interaction.RememberScreenFocus
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.theme.Palette
import kotlinx.coroutines.flow.first

/** Season and range navigation stay fixed; episode rows retain one continuous lazy viewport. */
@Composable
internal fun PlayerEpisodeSheet(
    panel: PlayerPanel,
    ui: PlayerUiState,
    actions: PlayerActions,
    onPanel: (PlayerPanel) -> Unit,
    onDismiss: () -> Unit,
    initialFocus: String,
) {
    key(panel, if (panel == PlayerPanel.Episodes) ui.selectedSeasonId else null) {
        RememberScreenFocus(initialFocus) {
            var groupsOpen by remember { mutableStateOf(false) }
            var jumpIndex by remember { mutableStateOf<Int?>(null) }
            var jumpEvent by remember { mutableIntStateOf(0) }
            var handledJump by remember { mutableIntStateOf(0) }
            fun closeGroups() { groupsOpen = false; jumpIndex = null; jumpEvent++ }
            val episodes = panel == PlayerPanel.Episodes
            val rows = if (episodes) ui.episodes else ui.seasons
            val loading = if (episodes) ui.episodesLoading else ui.seasonsLoading
            val error = if (episodes) ui.episodeError else ui.seasonsError
            val groups = remember(rows, episodes) { if (episodes) episodeGroups(rows) else emptyList() }
            val openingTarget = remember { initialFocus }
            val waitingForRows = remember { rows.isEmpty() }
            var positioned by remember { mutableStateOf(false) }
            val index = remember(rows, openingTarget) {
                rows.indexOfFirst { panel.choiceKey(it.id) == openingTarget }.coerceAtLeast(0)
            }
            val scroll = rememberLazyListState(initialFirstVisibleItemIndex = index)
            val activeGroup by remember(groups, scroll) { derivedStateOf {
                groups.lastOrNull { it.start <= scroll.firstVisibleItemIndex }
            } }
            val grid = rememberLazyGridState()
            Sheet(if (groupsOpen) "集数区间" else panel.title, { if (groupsOpen) closeGroups() else onDismiss() },
                side = true, preferredWidth = 420.dp, scrollContent = false, header = {
                    if (groupsOpen) PlayerChromeButton("返回选集", Glyph.Back, ::closeGroups,
                        Modifier.focusTarget("player:episode-groups-back"))
                    BoxWithConstraints(Modifier.weight(1f)) {
                        val showRangeLabel = maxWidth >= 280.dp
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            EpisodeSheetHeading(if (groupsOpen) "集数区间" else panel.title,
                                if (rows.isEmpty()) null else "共 ${rows.size} ${if (episodes) "集" else "季"}",
                                loading && rows.isNotEmpty(),
                                if (episodes && !groupsOpen) Modifier else Modifier.weight(1f))
                            if (episodes && !groupsOpen) {
                                PlayerChromeButton("切换季", Glyph.Down, { onPanel(PlayerPanel.Seasons) },
                                    Modifier.weight(1f).focusTarget("player:episode-season"),
                                    text = ui.seasons.firstOrNull { it.id == ui.selectedSeasonId }?.name ?: "切换季",
                                    compact = true)
                                if (groups.isNotEmpty()) PlayerChromeButton("集数区间", Glyph.List, { groupsOpen = true },
                                    Modifier.widthIn(max = 140.dp).focusTarget("player:episode-groups"),
                                    text = if (showRangeLabel) activeGroup?.label ?: groups.first().label else null,
                                    compact = true)
                            }
                        }
                    }
                }) {
                BackHandler(groupsOpen) { closeGroups() }
                val focus = LocalScreenFocus.current
                val window = LocalWindowInfo.current
                val tv = LocalDeviceLayout.current.television
                LaunchedEffect(groupsOpen, jumpEvent, rows, tv) {
                    if (groupsOpen) {
                        val selected = groups.indexOf(activeGroup).coerceAtLeast(0)
                        grid.scrollToItem(selected)
                        snapshotFlow { grid.layoutInfo.visibleItemsInfo.any { it.index == selected } }.first { it }
                        if (tv) {
                            snapshotFlow { window.isWindowFocused }.first { it }
                            val target = "player:episode-group:$selected"
                            focus?.rememberTarget?.invoke(target)
                            withFrameNanos {}
                            focus?.requesters?.get(target)?.requestFocus()
                        }
                        return@LaunchedEffect
                    }
                    if (jumpEvent > handledJump) {
                        val targetIndex = jumpIndex
                        if (targetIndex != null && targetIndex in rows.indices) {
                            scroll.scrollToItem(targetIndex)
                            snapshotFlow { scroll.layoutInfo.visibleItemsInfo.any { it.index == targetIndex } }.first { it }
                        }
                        if (tv) {
                            val target = targetIndex?.let { rows.getOrNull(it)?.let { row -> panel.choiceKey(row.id) } }
                                ?: "player:episode-groups"
                            focus?.rememberTarget?.invoke(target)
                            withFrameNanos {}
                            focus?.requesters?.get(target)?.requestFocus()
                        }
                        positioned = true
                        handledJump = jumpEvent
                        return@LaunchedEffect
                    }
                    if (positioned) return@LaunchedEffect
                    if (tv && waitingForRows && rows.isNotEmpty() && focus?.target?.invoke() != openingTarget) {
                        positioned = true
                        return@LaunchedEffect
                    }
                    val target = if (waitingForRows && rows.isNotEmpty()) panel.initialFocus(ui) else openingTarget
                    val targetIndex = rows.indexOfFirst { panel.choiceKey(it.id) == target }
                    if (targetIndex >= 0) {
                        scroll.scrollToItem(targetIndex)
                        snapshotFlow { scroll.layoutInfo.visibleItemsInfo.any { it.index == targetIndex } }.first { it }
                    }
                    if (tv) {
                        snapshotFlow { window.isWindowFocused }.first { it }
                        focus?.rememberTarget?.invoke(target)
                        withFrameNanos {}
                        focus?.requesters?.get(target)?.requestFocus()
                    }
                    positioned = rows.isNotEmpty()
                }
                if (groupsOpen) {
                    LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxWidth().weight(1f).testTag("player:episode-group-list"),
                        state = grid, verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                        itemsIndexed(groups, key = { _, group -> group.start }) { i, group ->
                            FocusSurface({
                                jumpIndex = group.start; jumpEvent++; groupsOpen = false
                            }, Modifier.fillMaxWidth().heightIn(min = 48.dp).focusTarget("player:episode-group:$i"),
                                selected = group == activeGroup, transparent = true,
                                focusedScale = dev.joyfin.ui.theme.MotionTokens.FixedScale) {
                                Text(group.label.removeSuffix(" 集"), Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
                                    color = Palette.Text, fontSize = 14.sp, maxLines = 1, textAlign = TextAlign.Center)
                            }
                        }
                    }
                } else {
                    if (rows.isNotEmpty()) {
                        if (error != null) {
                            Caption(error, maxLines = 2)
                            PlayerChromeButton("重试加载", Glyph.Refresh, actions.retryEpisodes,
                                Modifier.focusTarget("player:episode-retry"), text = "重试")
                        }
                    }
                    if (rows.isEmpty()) {
                        Column(Modifier.fillMaxWidth().weight(1f).padding(vertical = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            when {
                                loading -> {
                                    LoadingSpinner(Modifier.size(24.dp))
                                    Caption(if (episodes) "正在读取剧集…" else "正在读取季信息…")
                                }
                                error != null -> {
                                    AppIcon(Glyph.Info, Modifier.size(28.dp), color = Palette.Muted)
                                    Caption(error)
                                    ActionButton("重试加载", actions.retryEpisodes,
                                        Modifier.focusTarget("player:episode-retry"), icon = Glyph.Refresh, quiet = true)
                                }
                                else -> {
                                    AppIcon(Glyph.List, Modifier.size(28.dp), color = Palette.Muted)
                                    Caption(if (episodes) "本季暂无可选剧集" else "暂无季信息")
                                }
                            }
                        }
                    } else LazyColumn(
                        Modifier.fillMaxWidth().weight(1f).testTag("player:episode-list"), state = scroll,
                        verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        items(rows, key = { it.id }, contentType = { if (episodes) "episode" else "season" }) { row ->
                            val selected = row.id == if (episodes) ui.itemId else ui.selectedSeasonId
                            ChoiceRow(if (episodes) row.label else row.name, Modifier.focusTarget(panel.choiceKey(row.id)),
                                subtitle = if (!episodes) "" else if (selected) "正在播放" else if (row.played) "已看" else "",
                                selected = selected, leading = if (episodes) ({ EpisodeNumber(row, selected) }) else null) {
                                if (episodes) { onDismiss(); actions.selectEpisode(row) }
                                else { actions.selectSeason(row.id); onPanel(PlayerPanel.Episodes) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeSheetHeading(title: String, count: String?, loading: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SectionTitle(title)
        if (count != null) Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Caption(count, maxLines = 1)
            if (loading) LoadingSpinner(Modifier.size(12.dp))
        }
    }
}

@Composable
private fun EpisodeNumber(episode: Media, selected: Boolean) {
    Box(Modifier.size(36.dp)
        .background(if (selected) Palette.Text else Palette.Elevated, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center) {
        if (selected) AppIcon(Glyph.Play, Modifier.size(18.dp), color = Palette.Background)
        else Caption(episode.episode.toString(), color = Palette.Text)
    }
}
