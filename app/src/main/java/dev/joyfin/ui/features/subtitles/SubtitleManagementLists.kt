package dev.joyfin.ui.features.subtitles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.joyfin.data.Media
import dev.joyfin.data.subtitles.ManagedSubtitle
import dev.joyfin.subtitles.SubtitleOrganizerState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.theme.MotionTokens
import dev.joyfin.ui.theme.Palette
import dev.joyfin.ui.features.subtitles.tv.*
import dev.joyfin.ui.features.subtitles.mobile.*

@Composable
internal fun SubtitlePackageList(packages: List<ManagedSubtitle>, group: String, loading: Boolean,
    busy: Boolean, scroll: LazyListState, images: ArtworkSource, open: (String) -> Unit) {
    val shown = packages.filter { when (group) {
        "all" -> true; "linked" -> it.localSaved; "failed" -> it.failed; else -> it.pending
    } }
    val tv = LocalDeviceLayout.current.television
    LazyColumn(state = scroll, contentPadding = PaddingValues(top = 6.dp, bottom = 14.dp)) {
        if (loading && shown.isEmpty()) item("skeleton") { SubtitlePackageSkeleton() }
        if (!loading && shown.isEmpty()) item("empty") {
            SubtitleEmpty(if (group == "pending") "没有待整理的字幕" else "暂无字幕包",
                if (group == "pending" || group == "all") "搜索下载后，字幕包会保存在这里" else "切换筛选查看其他字幕包")
        }
        items(shown, key = { it.id }) { value ->
            FocusSurface({ open(value.id) }, Modifier.fillMaxWidth().focusTarget("subtitles:package:${value.id}"),
                enabled = !busy, transparent = true, focusedScale = MotionTokens.FixedScale) {
                val artwork: @Composable () -> Unit = { SubtitlePackageArtwork(value, images) }
                val details: @Composable () -> Unit = {
                    SubtitleTitle(listOf(value.workName.ifBlank { "未指定作品" }, value.seasonName)
                        .filter(String::isNotBlank).joinToString(" · "))
                    SubtitleFileName(value.title)
                    Caption("${value.origin.substringBefore(" · ")} · ${value.files.size} 个文件", maxLines = 2)
                }
                val status: @Composable () -> Unit = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            SubtitleStatus(when {
                                value.failed -> "回传失败"
                                value.uploaded > 0 -> "已回传 ${value.uploaded} 份"
                                value.localSaved -> "已关联"
                                else -> "待整理"
                            }, warning = value.failed)
                            if (value.matched > 0) Caption("${value.matched} / ${value.files.size} 份已关联", maxLines = 1)
                        }
                        AppIcon(Glyph.Next, color = Palette.Muted)
                    }
                }
                if (tv) TvSubtitlePackageLayout(artwork, details, status)
                else MobileSubtitlePackageLayout(artwork, details, status)
            }
            SubtitleDivider()
        }
    }
}

@Composable
internal fun SubtitleEpisodeList(editor: SubtitleOrganizerState, scroll: LazyListState, choose: (Media) -> Unit) {
    val pack = editor.pack
    val tv = LocalDeviceLayout.current.television
    LazyColumn(state = scroll, contentPadding = PaddingValues(top = 6.dp, bottom = 14.dp)) {
        if (editor.loading) item("skeleton") { SubtitleEpisodeSkeleton() }
        else if (pack?.workId == null) item("choose") { SubtitleEmpty("为字幕选择作品", "选择作品与季后，按集整理字幕") }
        else if (pack != null) {
            if (editor.media.isEmpty()) item("empty") { SubtitleEmpty("当前季没有剧集", "切换季或作品后继续整理") }
            items(editor.media, key = { it.id }) { media ->
                val files = pack.files.filter { it.mapping?.itemId == media.id }
                val error = files.firstNotNullOfOrNull { it.uploadError }
                val incomplete = files.any { it.upload in setOf("failed", "uncertain", "sending", "identifying") }
                FocusSurface({ choose(media) }, Modifier.fillMaxWidth().focusTarget("subtitles:episode:${media.id}"),
                    enabled = !editor.loading && !editor.saving, transparent = true, focusedScale = MotionTokens.FixedScale) {
                    val number: @Composable () -> Unit = {
                        SubtitleNumber(if (media.type == "Episode") media.episode.toString().padStart(2, '0') else "片")
                    }
                    val details: @Composable () -> Unit = {
                        SubtitleTitle(media.name, maxLines = if (tv) 1 else 2)
                        val formats = files.map { it.name.substringAfterLast('.', "").uppercase() }.filter(String::isNotBlank).distinct()
                        val versions = files.mapNotNull { it.mapping?.versionId }.distinct().size
                        Caption(when {
                            error != null || incomplete -> "回传未完成"
                            files.isEmpty() -> "未关联字幕"
                            else -> listOf("${files.size} 份字幕", formats.joinToString(" / "), "$versions 个版本")
                                .filter(String::isNotBlank).joinToString(" · ")
                        }, color = if (error != null || incomplete) Palette.Error else Palette.Muted, maxLines = 1)
                    }
                    val status: @Composable () -> Unit = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SubtitleStatus(if (files.isEmpty()) "选择" else "编辑", subdued = true)
                            AppIcon(Glyph.Next, color = Palette.Muted)
                        }
                    }
                    if (tv) TvSubtitleEpisodeLayout(number, details, status)
                    else MobileSubtitleEpisodeLayout(number, details, status)
                }
                SubtitleDivider()
            }
        }
    }
}

@Composable
private fun SubtitlePackageSkeleton() {
    Column {
        repeat(3) {
            Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SkeletonBlock(Modifier.width(if (LocalDeviceLayout.current.television) 52.dp else 44.dp).aspectRatio(2f / 3f))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonTextLine(16.sp, 23.sp); SkeletonTextLine(13.sp, 21.sp)
                    SkeletonTextLine(13.sp, 21.sp, .7f); SkeletonTextLine(12.5.sp, 18.sp, .6f)
                }
            }
            SubtitleDivider()
        }
    }
}

@Composable
private fun SubtitleEpisodeSkeleton() {
    Column {
        repeat(4) {
            Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonBlock(Modifier.width(28.dp).height(20.dp))
                Column(Modifier.weight(1f)) { SkeletonTextLine(16.sp, 23.sp); SkeletonTextLine(12.5.sp, 18.sp, .6f) }
            }
            SubtitleDivider()
        }
    }
}
