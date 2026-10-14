package dev.joyfin.ui.features.subtitles

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.subtitles.SubtitlePageController
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SubtitleUploadConfirmation(controller: SubtitlePageController, dismiss: () -> Unit, confirm: () -> Unit) {
    val state by controller.organizer.state.collectAsState()
    val upload by controller.uploader.state.collectAsState()
    val pack = state.pack ?: return
    val pending = pack.files.filter { it.mapping != null && it.upload != "done" }
    var languages by remember { mutableStateOf(false) }
    LaunchedEffect(pack.id, pack.language, pack.forced, pack.hearingImpaired) { controller.uploader.prepare() }
    val removals = upload.tracks.count { it.selected }
    RememberSelectionFocus("subtitles:upload-confirm") {
        Sheet("确认回传 Jellyfin", dismiss, actions = {
            ActionButton("回传 ${pending.size} 份 · 删除 $removals 份旧字幕", confirm, Modifier.focusTarget("subtitles:upload-confirm"),
                primary = true, loading = upload.preparing, enabled = (pending.isNotEmpty() || removals > 0) && !state.loading && !state.saving && !upload.preparing && upload.error == null)
        }) {
            Caption(controller.serverAddress)
            SectionTitle("${pack.workName} ${pack.seasonName}")
            ActionButton("语言：${when (pack.language) { "chi" -> "简体中文"; "cht" -> "繁体中文"; "eng" -> "英语"; else -> pack.language }}", { languages = !languages })
            if (languages) listOf("chi" to "简体中文", "cht" to "繁体中文", "eng" to "英语", "jpn" to "日语", "und" to "未指定").forEach { (id, label) ->
                ChoiceRow(label, selected = pack.language == id) {
                    controller.organizer.options(id, pack.forced, pack.hearingImpaired)
                    languages = false
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(if (pack.forced) "强制字幕：是" else "强制字幕：否", {
                    controller.organizer.options(pack.language, !pack.forced, pack.hearingImpaired)
                }, selected = pack.forced)
                ActionButton(if (pack.hearingImpaired) "听障字幕：是" else "听障字幕：否", {
                    controller.organizer.options(pack.language, pack.forced, !pack.hearingImpaired)
                }, selected = pack.hearingImpaired)
            }
            Caption("先保存本地关联并上传新字幕，全部确认后再删除下方勾选的旧字幕。")
            upload.error?.let { Caption(it, color = dev.joyfin.ui.theme.Palette.Error) }
            if (!upload.preparing) {
                SectionTitle("替换旧字幕（$removals）")
                Caption("已记录身份且被替换的旧字幕默认勾选；旧版上传及其他外部字幕需手动勾选。未勾选的保留。")
                upload.tracks.forEach { track ->
                    ChoiceRow(track.label, selected = track.selected) {
                        controller.uploader.toggleDelete(track.receipt.itemId, track.receipt.path)
                    }
                }
                if (upload.tracks.isEmpty()) Caption("没有可替换的旧字幕")
            }
            if (pack.matched < pack.files.size) Caption("${pack.files.size - pack.matched} 个未对应文件不回传")
            if (pack.files.any { it.upload in setOf("sending", "uncertain") }) Caption("有文件未收到服务器确认。请先核对服务器，重试可能重复添加。")
            pending.forEach { file ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SubtitleFileName(file.name)
                    Caption("→ ${file.mapping?.itemName} · ${file.mapping?.versionName}")
                }
            }
        }
    }
}
