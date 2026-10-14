package dev.joyfin.ui.features.subtitles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.data.subtitles.ManagedSubtitle
import dev.joyfin.subtitles.SubtitleOrganizerState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.theme.Palette

@Composable
internal fun SubtitlePackageList(packages: List<ManagedSubtitle>, group: String, loading: Boolean, busy: Boolean, scroll: LazyListState, open: (String) -> Unit) {
    val shown = packages.filter { when (group) { "linked" -> it.localSaved; "failed" -> it.failed; else -> it.pending } }
    LazyColumn(state = scroll, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (loading && shown.isEmpty()) item("skeleton") { SubtitleRowsSkeleton(90) }
        if (!loading && shown.isEmpty()) item("empty") { Caption(if (group == "pending") "没有待整理字幕，搜索下载后会出现在这里" else "暂无字幕") }
        items(shown, key = { it.id }) { value ->
            FocusSurface({ open(value.id) }, Modifier.fillMaxWidth().focusTarget("subtitles:package:${value.id}"), enabled = !busy) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionTitle(value.title)
                    Caption("${value.origin} · ${value.files.size} 个文件")
                    Caption(if (value.workId == null) "尚未选择作品" else "${value.workName} ${value.seasonName} · ${value.matched} 个已对应")
                    Caption(when { value.failed -> "部分回传失败或未确认 · 进入查看"; value.uploaded > 0 -> "已回传 ${value.uploaded} 个文件"; value.localSaved -> "本地关联已保存 · 进入管理"; else -> "进入整理" })
                }
            }
        }
    }
}

@Composable
internal fun SubtitleEpisodeList(editor: SubtitleOrganizerState, scroll: LazyListState, choose: (dev.joyfin.data.Media) -> Unit) {
    val pack = editor.pack
    LazyColumn(state = scroll, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (editor.loading) item("skeleton") { SubtitleRowsSkeleton(90) }
        else if (pack?.workId == null) item("choose") { Caption("文件已下载到本机。选择作品后整理每集字幕。") }
        else if (pack != null) {
            if (editor.media.isEmpty()) item("empty") { Caption("当前季没有剧集") }
            items(editor.media, key = { it.id }) { media ->
                val files = pack.files.filter { it.mapping?.itemId == media.id }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionTitle(media.label)
                    Caption(if (files.isEmpty()) "尚未关联字幕" else "已关联 ${files.size} 份字幕")
                    files.forEach { file ->
                        Caption(file.name + " · " + file.mapping?.versionName.orEmpty())
                        file.uploadError?.let { Caption(it, color = Palette.Error) }
                    }
                    ActionButton("选择字幕（${files.size}）", { choose(media) }, Modifier.focusTarget("subtitles:episode:${media.id}"),
                        enabled = !editor.loading && !editor.saving, primary = files.isEmpty())
                }
            }
            val unassigned = pack.files.count { it.mapping == null }
            if (unassigned > 0) item("unassigned") { Caption("另有 $unassigned 个文件尚未对应，可在各集字幕选择中勾选。") }
        }
    }
}

@Composable
private fun SubtitleRowsSkeleton(height: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { SkeletonBlock(Modifier.fillMaxWidth().height(height.dp)) }
    }
}
