package dev.joyfin.ui.features.subtitles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.subtitles.AssrtSearchController
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.*
import dev.joyfin.ui.theme.Palette

@Composable
internal fun AssrtResults(controller: AssrtSearchController, download: dev.joyfin.subtitles.LocalSubtitleDownload, scroll: LazyListState, settings: () -> Unit) {
    val ui by controller.state.collectAsState()
    val transfer by download.state.collectAsState()
    val focus = LocalScreenFocus.current
    var returning by remember { mutableStateOf(false) }
    LaunchedEffect(ui.selected) {
        if (ui.selected != null) returning = true
        else if (returning) { withFrameNanos {}; focus?.restore(); returning = false }
    }
    LazyColumn(state = scroll, verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
        item("attribution") { Caption("字幕服务由 assrt.net 提供") }
        if (!ui.loading && !ui.available) item("configure") { ActionButton("配置 ASSRT Token", settings) }
        if (ui.loading && ui.results.isEmpty()) item("skeleton") { repeat(3) { SkeletonBlock(Modifier.fillMaxWidth().height(90.dp).padding(bottom = 8.dp)) } }
        items(ui.results, key = { it.id }) { item ->
            SubtitleResultRow(item.nativeName.ifBlank { item.videoname },
                listOf(item.lang.desc, item.subtype).filter { it.isNotBlank() }.joinToString(" · "), item.videoname,
                error = ui.error.takeIf { ui.failedId == item.id }
                    ?: transfer.error.takeIf { transfer.source == "ASSRT" && ui.transferringId == item.id }) {
                ActionButton("下载", { controller.choose(item) }, Modifier.focusTarget("assrt:result:${item.id}"),
                    loading = ui.busyId == item.id || transfer.busy && ui.transferringId == item.id,
                    enabled = ui.busyId == null && !transfer.busy && !transfer.needsEncoding)
            }
        }
        ui.error?.let { message -> item("error") {
            Caption(message, color = Palette.Error)
            ActionButton("重试 ASSRT 搜索", controller::retry, loading = ui.loading, enabled = !ui.loading)
        } }
        if (ui.available && !ui.loading && ui.results.isEmpty() && ui.error == null) item("empty") { SubtitleEmpty("没有找到字幕", "尝试修改关键词或语言") }
        if (ui.nextPosition != null || ui.loadingMore) item("more") {
            ActionButton("加载更多", controller::more,
                loading = ui.loadingMore, enabled = !ui.loading && ui.nextPosition != null)
        }
    }
    if (ui.selected != null && ui.files.size > 1) RememberSelectionFocus("assrt:file:0") {
        Sheet("ASSRT 文件", { controller.dismissFiles(); download.cancel() }) {
            if (ui.selected?.filelist.orEmpty().size > 1)
                ActionButton("下载全部文件（${ui.selected?.filelist.orEmpty().size}）", controller::downloadAllFiles,
                    loading = transfer.busy && transfer.fileName == "整包文件", enabled = !transfer.busy)
            transfer.error?.takeIf { transfer.source == "ASSRT" }?.let { Caption(it, color = Palette.Error) }
            ui.files.forEachIndexed { index, file ->
                ActionButton(file.f, { controller.download(file) }, Modifier.focusTarget("assrt:file:$index"),
                    loading = transfer.busy && transfer.fileName == file.f, enabled = !transfer.busy)
            }
        }
    }
}
