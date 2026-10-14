package dev.joyfin.ui.features.subtitles

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val candidates = (pack.files + ui.available).distinctBy { it.fileId }
    val associated = pack.files.filter { it.mapping?.let { m -> m.itemId == media.id && m.versionId == version } == true }
    val chosen = pack.defaults[media.id + ":" + version]?.takeIf { id -> associated.any { it.fileId == id } } ?: associated.firstOrNull()?.fileId
    val initial = remember(media.id, version) { associated.firstOrNull()?.fileId ?: candidates.firstOrNull()?.fileId }
    RememberSelectionFocus(initial?.let { "episode-file:$it" } ?: "sheet:close") {
        Sheet(media.label, dismiss, actions = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Caption("已选 ${associated.size} 份", color = Palette.Text)
                ActionButton("完成", dismiss, primary = true)
            }
        }) {
            Caption(media.sources.firstOrNull { it.id == version }?.name.orEmpty(), maxLines = 2)
            Caption("确认勾选多份字幕，右侧设置默认播放。")
            SubtitleDivider()
            candidates.forEach { candidate -> key(candidate.fileId) {
                val row = pack.files.firstOrNull { it.fileId == candidate.fileId }
                val selected = associated.any { it.fileId == candidate.fileId }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusSurface({ controller.toggleAvailable(candidate.fileId, media.id, version) },
                        Modifier.weight(1f).focusTarget("episode-file:${candidate.fileId}"), selected = selected, transparent = true) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AppIcon(if (selected) Glyph.Check else Glyph.Plus, color = if (selected) Palette.Gold else Palette.Muted)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                SubtitleFileName(candidate.name)
                                Caption(if (selected) "已关联本集" else row?.mapping?.itemName?.let { "移自 $it" } ?: "未关联本集", maxLines = 1)
                            }
                        }
                    }
                    ActionButton(if (chosen == candidate.fileId && selected) "默认 ✓" else "默认", {
                        controller.defaultFile(media.id, version, candidate.fileId)
                    }, Modifier.focusTarget("episode-default:${candidate.fileId}"), selected = chosen == candidate.fileId && selected,
                        quiet = true, enabled = selected)
                }
            } }
        }
    }
}
