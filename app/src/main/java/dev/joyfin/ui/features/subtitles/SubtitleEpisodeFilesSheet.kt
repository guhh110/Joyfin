package dev.joyfin.ui.features.subtitles

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.joyfin.data.Media
import dev.joyfin.subtitles.SubtitleOrganizer
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.*
import dev.joyfin.ui.theme.Palette

@Composable
internal fun SubtitleEpisodeFilesSheet(controller: SubtitleOrganizer, media: Media, version: String, dismiss: () -> Unit) {
    val ui by controller.state.collectAsState()
    val pack = ui.pack ?: return
    // Keep order stable while toggling so TV focus never follows a moving file.
    val candidates = (pack.files + ui.available).distinctBy { it.fileId }
    val associated = pack.files.filter { it.mapping?.let { m -> m.itemId == media.id && m.versionId == version } == true }
    val initial = remember(media.id, version) { associated.firstOrNull()?.fileId ?: candidates.firstOrNull()?.fileId }
    RememberSelectionFocus(initial?.let { "episode-file:$it" } ?: "sheet:close") {
        Sheet(media.label, dismiss, actions = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Caption("已选 ${associated.size} 份", color = Palette.Text)
                    Caption("返回后统一保存本地")
                }
                ActionButton("完成", dismiss, primary = true)
            }
        }) {
            Caption(media.sources.firstOrNull { it.id == version }?.name.orEmpty(), maxLines = 2)
            Caption("勾选字幕，可选择多份；播放时在播放器中选择使用的字幕")
            SubtitleDivider()
            candidates.forEach { candidate -> key(candidate.fileId) {
                val row = pack.files.firstOrNull { it.fileId == candidate.fileId }
                val selected = associated.any { it.fileId == candidate.fileId }
                FocusSurface({ controller.toggleAvailable(candidate.fileId, media.id, version) },
                    Modifier.fillMaxWidth().focusTarget("episode-file:${candidate.fileId}").semantics {
                        role = Role.Checkbox
                        stateDescription = if (selected) "已勾选" else "未勾选"
                    }, selected = selected, transparent = true, enabled = !ui.loading && !ui.saving) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppIcon(if (selected) Glyph.Check else Glyph.Plus, Modifier.padding(top = 3.dp), color = if (selected) Palette.Gold else Palette.Muted)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            SubtitleFileName(candidate.name, emphasizeDetails = true)
                            Caption(when {
                                selected -> "已关联本集"
                                row?.mapping != null -> "选中后移自 ${row.mapping.itemName}"
                                else -> "未关联本集"
                            }, color = if (selected) Palette.Gold else Palette.Muted)
                            val uploadIssue = row?.uploadError ?: when (row?.upload) {
                                "failed" -> "回传失败，可通过回传操作重试"
                                "uncertain", "sending" -> "服务器回传结果待确认"
                                "identifying" -> "已回传，等待识别服务器字幕"
                                else -> null
                            }
                            uploadIssue?.let { Caption(it, color = Palette.Error) }
                        }
                    }
                }
                SubtitleDivider()
            } }
        }
    }
}
