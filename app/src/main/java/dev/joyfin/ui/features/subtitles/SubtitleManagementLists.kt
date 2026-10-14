package dev.joyfin.ui.features.subtitles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.joyfin.data.subtitles.ManagedSubtitle
import dev.joyfin.subtitles.SubtitleOrganizerState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.theme.Palette

@Composable
internal fun SubtitlePackageList(packages: List<ManagedSubtitle>, group: String, loading: Boolean, busy: Boolean, scroll: LazyListState, open: (String) -> Unit) {
    val shown = packages.filter { when (group) { "linked" -> it.localSaved; "failed" -> it.failed; else -> it.pending } }
    LazyColumn(state = scroll, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
        if (loading && shown.isEmpty()) item("skeleton") { SubtitleRowsSkeleton(112) }
        if (!loading && shown.isEmpty()) item("empty") { SubtitleEmpty(if (group == "pending") "一切就绪" else "暂无字幕",
            if (group == "pending") "搜索并下载字幕后，在这里整理对应关系" else "保存的关联与回传状态会显示在这里") }
        items(shown, key = { it.id }) { value ->
            FocusSurface({ open(value.id) }, Modifier.fillMaxWidth().focusTarget("subtitles:package:${value.id}"), enabled = !busy) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (LocalDeviceLayout.current.television) SubtitleNumber(value.files.size.toString())
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            if (value.workName.isBlank()) SubtitleFileName(value.title)
                            else SubtitleTitle(value.workName)
                            Caption(listOf(value.seasonName, value.origin.substringBefore(" · "), "${value.files.size} 份字幕").filter { it.isNotBlank() }.joinToString(" · "), maxLines = 1)
                        }
                        SubtitleStatus(when { value.failed -> "回传异常"; value.uploaded > 0 -> "已回传 ${value.uploaded}"; value.localSaved -> "已关联"; else -> "待整理" }, value.failed)
                    }
                    if (value.workName.isNotBlank()) SubtitleFileName(value.title)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Caption(if (value.workId == null) "选择作品开始整理" else "${value.matched} / ${value.files.size} 已对应", maxLines = 1)
                        Caption("查看详情  ›", color = Palette.Text)
                    }
                    Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Palette.Muted.copy(alpha = .12f))) {
                        val fraction = value.matched.toFloat() / value.files.size.coerceAtLeast(1)
                        if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(Palette.Gold.copy(alpha = .65f)))
                    }
                }
            }
        }
    }
}

@Composable
internal fun SubtitleEpisodeList(editor: SubtitleOrganizerState, scroll: LazyListState, choose: (dev.joyfin.data.Media) -> Unit) {
    val pack = editor.pack
    LazyColumn(state = scroll, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
        if (editor.loading) item("skeleton") { SubtitleRowsSkeleton(90) }
        else if (pack?.workId == null) item("choose") { SubtitleEmpty("为字幕选择作品", "选定作品与季后，自动整理每集对应关系") }
        else if (pack != null) {
            if (editor.media.isEmpty()) item("empty") { SubtitleEmpty("当前季没有剧集", "切换季或作品后继续整理") }
            items(editor.media, key = { it.id }) { media ->
                val files = pack.files.filter { it.mapping?.itemId == media.id }
                val error = files.firstNotNullOfOrNull { it.uploadError }
                FocusSurface({ choose(media) }, Modifier.fillMaxWidth().focusTarget("subtitles:episode:${media.id}"), enabled = !editor.loading && !editor.saving) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        SubtitleNumber(if (media.type == "Episode") media.episode.toString().padStart(2, '0') else "片")
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            SubtitleTitle(media.name)
                            files.firstOrNull()?.let { SubtitleFileName(it.name) } ?: Caption("选择字幕")
                            if (error != null) Caption(error, color = Palette.Error, maxLines = 2)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (error != null) SubtitleStatus("待处理", warning = true)
                            else if (files.isNotEmpty()) SubtitleStatus("${files.size} 份字幕")
                            else Caption("未关联")
                            Caption("编辑  ›", color = Palette.Text)
                        }
                    }
                }
            }
            val unassigned = pack.files.count { it.mapping == null }
            if (unassigned > 0) item("unassigned") { Caption("$unassigned 份未分配文件 · 进入任一集可选择", Modifier.padding(vertical = 10.dp)) }
        }
    }
}

@Composable
private fun SubtitleRowsSkeleton(height: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { repeat(3) { SkeletonBlock(Modifier.fillMaxWidth().height(height.dp)) } }
}
