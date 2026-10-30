package dev.joyfin.ui.features.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.joyfin.data.Media
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.model.BrowseActions
import dev.joyfin.browse.ResumeActionState
import dev.joyfin.ui.interaction.RememberSelectionFocus

@Composable
fun ResumeQuickActionSheet(
    item: Media,
    actions: BrowseActions,
    request: ResumeActionState = ResumeActionState(),
    onDismiss: () -> Unit,
) {
    val title = item.seriesName.ifBlank { item.name }
    val state = request.takeIf { it.itemId == item.id } ?: ResumeActionState()
    RememberSelectionFocus("resume-action:replay") {
        Sheet(
            title = title,
            onDismiss = onDismiss,
            bottom = true,
        ) {
            if (item.seriesName.isNotBlank() && item.name.isNotBlank()) {
                Caption("第 ${item.season} 季 · 第 ${item.episode} 集 · ${item.name}")
            }
            ChoiceRow(
                "从头播放",
                Modifier.focusTarget("resume-action:replay"),
                subtitle = "从 00:00 重新开始播放此作品",
                enabled = !state.busy,
            ) {
                onDismiss()
                actions.play(item.copy(resumeTicks = 0), true, null)
            }
            ActionButton(
                "标为已看完",
                { actions.markResumeItemPlayed(item) },
                Modifier.fillMaxWidth().focusTarget("resume-action:played"),
                loading = state.busy && state.markPlayed,
                enabled = !state.busy || state.markPlayed,
            )
            ActionButton(
                "从继续观看中移除",
                { actions.removeResumeItem(item) },
                Modifier.fillMaxWidth().focusTarget("resume-action:remove"),
                loading = state.busy && !state.markPlayed,
                enabled = !state.busy || !state.markPlayed,
            )
            Caption("移除只隐藏继续观看条目，不改变已看状态或删除文件。")
            state.error?.let { Caption(it) }
            ChoiceRow(
                "查看详情",
                Modifier.focusTarget("resume-action:details"),
                subtitle = "进入影片或剧集的完整详情页",
                enabled = !state.busy,
            ) {
                onDismiss()
                actions.open(item)
            }
        }
    }
}
