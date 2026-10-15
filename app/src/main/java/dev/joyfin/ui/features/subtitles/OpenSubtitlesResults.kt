package dev.joyfin.ui.features.subtitles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.subtitles.OpenSubtitlesSearchController
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.*
import dev.joyfin.ui.theme.Palette

@Composable
internal fun OpenSubtitlesResults(controller: OpenSubtitlesSearchController, download: dev.joyfin.subtitles.LocalSubtitleDownload, scroll: LazyListState, settings: () -> Unit) {
    val ui by controller.state.collectAsState()
    val transfer by download.state.collectAsState()
    val focus = LocalScreenFocus.current
    var returning by remember { mutableStateOf(false) }
    LaunchedEffect(ui.selected) {
        if (ui.selected != null) returning = true
        else if (returning) { withFrameNanos {}; focus?.restore(); returning = false }
    }
    LazyColumn(state = scroll, verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
        item("attribution") { Caption("字幕服务由 OpenSubtitles.com 提供") }
        ui.remaining?.let { item("quota") { Caption("剩余下载额度：$it") } }
        if (!ui.loading && !ui.available) item("configure") { ActionButton("配置 OpenSubtitles", settings) }
        if (ui.loading && ui.results.isEmpty()) item("skeleton") { repeat(3) { SkeletonBlock(Modifier.fillMaxWidth().height(90.dp).padding(bottom = 8.dp)) } }
        items(ui.results, key = { it.id }) { item ->
            val data = item.attributes
            SubtitleResultRow(data.feature.title.ifBlank { data.release },
                "${data.language} · ${data.files.size} 个文件", data.release,
                error = if (ui.selected?.id == item.id) ui.error ?: transfer.error.takeIf { transfer.source == "OpenSubtitles" } else null) {
                ActionButton("下载", { controller.choose(item) }, Modifier.focusTarget("opensubtitles:result:${item.id}"),
                    loading = ui.selected?.id == item.id && (ui.busyFile != null || transfer.busy),
                    enabled = ui.busyFile == null && !transfer.busy && !transfer.needsEncoding)
            }
        }
        if (ui.results.isEmpty() && ui.error != null && !ui.loading) item("error") {
            ErrorContent("暂时无法读取字幕", ui.error ?: "搜索失败", "重试搜索", controller::retry, loading = ui.loading)
        } else if (ui.error != null) item("error") {
            RegionLoadFeedback(ui.loading, ui.error, controller::retry, true, reserveSpace = false)
        }
        if (ui.available && !ui.loading && ui.results.isEmpty() && ui.error == null) item("empty") { SubtitleEmpty("没有找到字幕", "尝试修改关键词或语言") }
        if (ui.nextPage != null || ui.loadingMore) item("more") {
            ActionButton("加载更多", controller::more,
                loading = ui.loadingMore, enabled = !ui.loading && ui.nextPage != null)
        }
    }
    ui.selected?.takeIf { it.attributes.files.size > 1 }?.let { entry -> RememberSelectionFocus(entry.attributes.files.firstOrNull()?.let { "opensubtitles:file:${it.id}" } ?: "sheet:close") {
        Sheet("OpenSubtitles 文件", { controller.dismissFiles(); download.cancel() }) {
            if (entry.attributes.files.isEmpty()) Caption("该结果没有可下载文件")
            entry.attributes.files.forEach { file ->
                ActionButton(file.name, { controller.download(file) }, Modifier.focusTarget("opensubtitles:file:${file.id}"),
                    loading = ui.busyFile == file.id || transfer.busy && ui.transferringFile == file.id, enabled = ui.busyFile == null && !transfer.busy)
            }
            ui.error?.let { Caption(it, color = Palette.Error) }
            transfer.error?.takeIf { transfer.source == "OpenSubtitles" }?.let { Caption(it, color = Palette.Error) }
        }
    } }
}
