package dev.joyfin.ui.features.subtitles

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.subtitles.SubtitlePageController
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.subtitles.mobile.MobileSubtitleUploadActions
import dev.joyfin.ui.features.subtitles.tv.TvSubtitleUploadReviewGroup
import dev.joyfin.ui.features.subtitles.tv.TvSubtitleUploadActions
import dev.joyfin.ui.interaction.*
import dev.joyfin.ui.theme.Palette

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SubtitleUploadConfirmation(controller: SubtitlePageController, dismiss: () -> Unit, confirm: () -> Unit) {
    val state by controller.organizer.state.collectAsState()
    val upload by controller.uploader.state.collectAsState()
    val pack = state.pack ?: return
    val pending = pack.files.filter { it.mapping != null && it.upload != "done" }
    val groups = pending.groupBy { requireNotNull(it.mapping).let { mapping -> mapping.itemId to mapping.versionId } }
    val tv = LocalDeviceLayout.current.television
    var settings by remember(pack.id) { mutableStateOf(false) }
    var languages by remember(pack.id) { mutableStateOf(false) }
    var showAll by remember(pack.id) { mutableStateOf(false) }
    val languageOptions = listOf("chi" to "简体中文", "cht" to "繁体中文", "eng" to "英语", "jpn" to "日语", "und" to "未指定")
    val language = languageOptions.firstOrNull { it.first == pack.language }?.second ?: pack.language
    LaunchedEffect(pack.id, pack.language, pack.forced, pack.hearingImpaired) { controller.uploader.prepare() }
    val removals = upload.tracks.count { it.selected }
    RememberSelectionFocus("subtitles:upload-confirm") {
        Sheet("回传 Jellyfin", dismiss, actions = {
            SubtitleDivider()
            Spacer(Modifier.height(10.dp))
            val summary: @Composable () -> Unit = {
                Caption("上传 ${pending.size} 份 · 删除 $removals 份旧字幕", color = Palette.Text)
            }
            val action: @Composable (Modifier) -> Unit = { modifier ->
                ActionButton("确认回传", confirm, modifier.focusTarget("subtitles:upload-confirm"),
                    primary = true, loading = upload.preparing,
                    enabled = (pending.isNotEmpty() || removals > 0) && !state.loading && !state.saving && !upload.preparing && upload.error == null)
            }
            if (tv) TvSubtitleUploadActions(summary, action)
            else MobileSubtitleUploadActions(summary, action)
        }) {
            SubtitleTitle(listOf(pack.workName, pack.seasonName).filter(String::isNotBlank).joinToString(" · "), maxLines = 3)
            val options = listOfNotNull(language, "强制".takeIf { pack.forced }, "听障".takeIf { pack.hearingImpaired }).joinToString(" · ")
            ActionButton("$options · ${if (settings) "收起设置" else "修改"}", { settings = !settings; languages = false },
                Modifier.focusTarget("subtitles:upload-settings"), quiet = true)
            if (settings) {
                Caption(controller.serverAddress, maxLines = 2)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ActionButton(language, { languages = !languages }, Modifier.focusTarget("subtitles:upload-language"), quiet = true,
                        enabled = !state.loading && !state.saving)
                    ActionButton("强制字幕", { controller.organizer.options(pack.language, !pack.forced, pack.hearingImpaired) },
                        Modifier.focusTarget("subtitles:upload-forced"), selected = pack.forced, quiet = true, enabled = !state.loading && !state.saving)
                    ActionButton("听障字幕", { controller.organizer.options(pack.language, pack.forced, !pack.hearingImpaired) },
                        Modifier.focusTarget("subtitles:upload-hearing"), selected = pack.hearingImpaired, quiet = true, enabled = !state.loading && !state.saving)
                }
                if (languages) languageOptions.forEach { (id, label) ->
                    ChoiceRow(label, Modifier.focusTarget("subtitles:upload-language:$id"), selected = pack.language == id) {
                        controller.organizer.options(id, pack.forced, pack.hearingImpaired)
                        languages = false
                    }
                }
            }
            upload.error?.let { Caption(it, color = Palette.Error) }
            if (pack.files.any { it.upload in setOf("sending", "uncertain") }) {
                Caption("回传结果未确认，请先核对服务器。", color = Palette.Error)
            }
            SubtitleDivider()
            if (pending.isNotEmpty()) ActionButton(if (showAll) "收起字幕清单" else "查看 ${pending.size} 份字幕", { showAll = !showAll },
                Modifier.fillMaxWidth().focusTarget("subtitles:upload-files"), quiet = true)
            if (pack.matched < pack.files.size) Caption("${pack.files.size - pack.matched} 份未对应，不回传")
            if (showAll) groups.values.forEach { files ->
                val mapping = requireNotNull(files.first().mapping)
                key(mapping.itemId, mapping.versionId) {
                    val details: @Composable () -> Unit = {
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Caption(mapping.itemName, color = Palette.Text)
                            if (mapping.versionName.isNotBlank()) Caption(mapping.versionName, maxLines = 2)
                            files.forEach { file -> SubtitleFileName(file.name, emphasizeDetails = true) }
                        }
                    }
                    if (tv) TvSubtitleUploadReviewGroup(Modifier.fillMaxWidth().focusTarget("subtitles:upload-review:${mapping.itemId}:${mapping.versionId}"), details)
                    else details()
                }
                SubtitleDivider()
            }
            if (!upload.preparing && upload.tracks.isNotEmpty()) {
                SubtitleTitle("替换旧字幕")
                Caption("上传成功后删除勾选项。")
                upload.tracks.forEach { track -> key(track.receipt.itemId, track.receipt.path) {
                    val mapping = track.receipt.mapping
                    FocusSurface({ controller.uploader.toggleDelete(track.receipt.itemId, track.receipt.path) },
                        Modifier.fillMaxWidth().focusTarget("subtitles:upload-delete:${track.receipt.itemId}:${track.receipt.path}"),
                        selected = track.selected, transparent = true) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AppIcon(if (track.selected) Glyph.Check else Glyph.Plus, color = if (track.selected) Palette.Text else Palette.Muted)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Caption(mapping.itemName, color = Palette.Text)
                                // This prefix is authored by SubtitleUploadController, not parsed server data.
                                Caption(track.label.removePrefix("${mapping.itemName} · "))
                            }
                        }
                    }
                } }
            }
        }
    }
}
