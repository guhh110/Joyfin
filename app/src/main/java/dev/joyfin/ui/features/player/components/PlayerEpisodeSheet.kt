package dev.joyfin.ui.features.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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

/** Only visible rows are composed, with the season switch kept outside the scrolling viewport. */
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
            Sheet(panel.title, onDismiss, side = true, preferredWidth = 420.dp, scrollContent = false) {
                val episodes = panel == PlayerPanel.Episodes
                val rows = if (episodes) ui.episodes else ui.seasons
                val openingTarget = remember { initialFocus }
                val waitingForRows = remember { rows.isEmpty() }
                var positioned by remember { mutableStateOf(false) }
                val index = remember(rows, openingTarget) {
                    rows.indexOfFirst { panel.choiceKey(it.id) == openingTarget }.coerceAtLeast(0)
                }
                val scroll = rememberLazyListState(initialFirstVisibleItemIndex = index)
                val focus = LocalScreenFocus.current
                val window = LocalWindowInfo.current
                val tv = LocalDeviceLayout.current.television
                LaunchedEffect(rows, tv) {
                    if (positioned) return@LaunchedEffect
                    // Late metadata may replace the waiting target, but never a deliberate move.
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
                if (episodes) {
                    ActionButton(ui.seasons.firstOrNull { it.id == ui.selectedSeasonId }?.name ?: "切换季",
                        { onPanel(PlayerPanel.Seasons) }, Modifier.focusTarget("player:episode-season"), icon = Glyph.Down)
                    ui.episodeError?.let { error ->
                        Caption(error)
                        ActionButton("重试读取剧集", { ui.selectedSeasonId?.let(actions.selectSeason) })
                    }
                }
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f).testTag("player:episode-list"),
                    state = scroll,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    if (rows.isEmpty()) item("empty") {
                        Caption(if (episodes) "暂无可选剧集" else "暂无季信息")
                    }
                    items(rows, key = { it.id }, contentType = { if (episodes) "episode" else "season" }) { row ->
                        val selected = row.id == if (episodes) ui.itemId else ui.selectedSeasonId
                        ChoiceRow(
                            if (episodes) row.label else row.name,
                            Modifier.focusTarget(panel.choiceKey(row.id)),
                            subtitle = if (!episodes) "" else if (selected) "正在播放" else if (row.played) "已看" else "",
                            selected = selected,
                            leading = if (episodes) ({ EpisodeNumber(row, selected) }) else null,
                        ) {
                            if (episodes) {
                                onDismiss()
                                actions.selectEpisode(row)
                            } else {
                                actions.selectSeason(row.id)
                                onPanel(PlayerPanel.Episodes)
                            }
                        }
                    }
                }
            }
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
