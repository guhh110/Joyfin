package dev.joyfin.ui.features.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.joyfin.data.Media
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.model.BrowseActions

@Composable
fun ResumeQuickActionSheet(
    item: Media,
    actions: BrowseActions,
    onDismiss: () -> Unit,
) {
    val title = item.seriesName.ifBlank { item.name }
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
        ) {
            onDismiss()
            actions.play(item.copy(resumeTicks = 0), true, null)
        }
        ChoiceRow(
            "标为已看完",
            Modifier.focusTarget("resume-action:played"),
            subtitle = "同步为已看完状态并从继续观看中移除",
        ) {
            onDismiss()
            actions.markResumeItemPlayed(item)
        }
        ChoiceRow(
            "从继续观看中移除",
            Modifier.focusTarget("resume-action:remove"),
            subtitle = "在继续观看中隐藏此条目，不删除文件",
        ) {
            onDismiss()
            actions.removeResumeItem(item)
        }
        ChoiceRow(
            "查看详情",
            Modifier.focusTarget("resume-action:details"),
            subtitle = "进入影片或剧集的完整详情页",
        ) {
            onDismiss()
            actions.open(item)
        }
    }
}
