package dev.joyfin.ui.features.subtitles

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.joyfin.data.Media
import dev.joyfin.subtitles.SubtitleOrganizer
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.*

@Composable
internal fun SubtitleEpisodeFilesSheet(controller: SubtitleOrganizer, media: Media, version: String, dismiss: () -> Unit) {
    val ui by controller.state.collectAsState()
    val pack = ui.pack ?: return
    val candidates = (pack.files + ui.available).distinctBy { it.fileId }
    val initial = remember(media.id, version) {
        candidates.firstOrNull { it.mapping?.let { m -> m.itemId == media.id && m.versionId == version } == true }?.fileId
            ?: candidates.firstOrNull()?.fileId
    }
    RememberSelectionFocus(initial?.let { "episode-file:$it" } ?: "sheet:close") {
        Sheet(media.label, dismiss, actions = { ActionButton("完成选择", dismiss, primary = true) }) {
            Caption("勾选多份字幕，再指定默认播放的一份。已对应其他集的文件会移动到本集。")
            Caption(media.sources.firstOrNull { it.id == version }?.name.orEmpty())
            candidates.forEach { candidate -> key(candidate.fileId) {
                val row = pack.files.firstOrNull { it.fileId == candidate.fileId }
                val selected = row?.mapping?.let { it.itemId == media.id && it.versionId == version } == true
                ChoiceRow(candidate.name, Modifier.focusTarget("episode-file:${candidate.fileId}"),
                    subtitle = if (selected) "已关联本集" else row?.mapping?.itemName.orEmpty(), selected = selected) {
                    controller.toggleAvailable(candidate.fileId, media.id, version)
                }
                if (selected) {
                    val chosen = pack.defaults[media.id + ":" + version]?.takeIf { id -> pack.files.any { it.fileId == id && it.mapping?.let { m -> m.itemId == media.id && m.versionId == version } == true } }
                        ?: pack.files.firstOrNull { it.mapping?.let { m -> m.itemId == media.id && m.versionId == version } == true }?.fileId
                    ActionButton(if (chosen == candidate.fileId) "默认播放" else "设为默认", {
                        controller.defaultFile(media.id, version, candidate.fileId)
                    }, selected = chosen == candidate.fileId)
                }
            } }
        }
    }
}
