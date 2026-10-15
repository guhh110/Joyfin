package dev.joyfin.ui.features.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.joyfin.subtitles.*
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.entry.components.EntryTextField
import dev.joyfin.ui.features.player.mobile.MobilePlayerSubtitleSearchLayout
import dev.joyfin.ui.features.player.tv.TvPlayerSubtitleSearchLayout
import dev.joyfin.ui.features.subtitles.*
import dev.joyfin.ui.features.subtitles.mobile.MobileSubtitleSearchInput
import dev.joyfin.ui.features.subtitles.tv.TvSubtitleSearchInput
import dev.joyfin.ui.interaction.*
import dev.joyfin.ui.theme.Palette
import kotlinx.coroutines.flow.first

@Composable
fun PlayerSubtitleSearchRoute(controller: PlayerSubtitleSearchController) {
    val ui by controller.state.collectAsState()
    if (!ui.open) return
    Dialog(onDismissRequest = controller::back, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        RememberScreenFocus("quick:query") {
            FixedActionArea { PlayerSubtitleSearchContent(controller) }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PlayerSubtitleSearchContent(controller: PlayerSubtitleSearchController) {
    val ui by controller.state.collectAsState()
    val assrt by controller.assrt.state.collectAsState()
    val os by controller.openSubtitles.state.collectAsState()
    val transfer by controller.download.state.collectAsState()
    val tv = LocalDeviceLayout.current.television
    val compactInput = !tv && WindowInsets.isImeVisible
    val keyboard = LocalSoftwareKeyboardController.current
    val window = LocalWindowInfo.current
    val focus = LocalScreenFocus.current
    val assrtScroll = rememberLazyListState()
    val osScroll = rememberLazyListState()
    val filesScroll = rememberLazyListState()
    val fieldState = remember { TextFieldState(ui.query) }
    LaunchedEffect(ui.query) {
        if (fieldState.text.toString() != ui.query) fieldState.setTextAndPlaceCursorAtEnd(ui.query)
    }
    val assrtFiles = ui.source == PlayerSubtitleSource.ASSRT && assrt.files.size > 1 && assrt.selected != null
    val osFiles = ui.source == PlayerSubtitleSource.OPEN_SUBTITLES && (os.selected?.attributes?.files?.size ?: 0) > 1
    val choosingPack = ui.pack != null && ui.selectingFile
    val stage = when {
        transfer.needsEncoding -> "encoding"
        choosingPack -> "pack:${ui.pack?.id}"
        assrtFiles -> "assrt-files:${assrt.selected?.id}"
        osFiles -> "os-files:${os.selected?.id}"
        else -> "results"
    }
    val names = when {
        choosingPack -> ui.pack!!.files.map { it.name }
        assrtFiles -> assrt.files.map { it.f }
        osFiles -> os.selected!!.attributes.files.map { it.name }
        else -> emptyList()
    }
    val candidates = controller.related(names)
    LaunchedEffect(stage, ui.focusRevision) {
        if (stage != "results") {
            if (stage != "encoding") filesScroll.scrollToItem(0)
            keyboard?.hide()
        }
        if (!tv || ui.closing) return@LaunchedEffect
        val target = when {
            stage == "encoding" -> "encoding:${transfer.encoding}"
            choosingPack -> candidates.firstOrNull()?.let { "quick:local:${ui.pack!!.files[it].fileId}" } ?: "quick:files-back"
            assrtFiles -> candidates.firstOrNull()?.let { "quick:assrt-file:$it" } ?: "quick:files-back"
            osFiles -> candidates.firstOrNull()?.let { "quick:os-file:$it" } ?: "quick:files-back"
            else -> ui.focusKey
        }
        focus?.rememberTarget?.invoke(target)
        snapshotFlow { window.isWindowFocused }.first { it }
        withFrameNanos {}
        focus?.requesters?.get(target)?.requestFocus()
    }
    LaunchedEffect(ui.closing) { if (ui.closing) keyboard?.hide() }
    val searching = if (ui.source == PlayerSubtitleSource.ASSRT) assrt.loading else os.loading
    val close = { controller.close() }
    val content: @Composable ColumnScope.() -> Unit = {
        if (!compactInput) Caption(ui.contextLabel, maxLines = 2)
        if (stage == "results") {
            if (!compactInput) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextTab("ASSRT", { controller.query(fieldState.text.toString()); controller.source(PlayerSubtitleSource.ASSRT) },
                    ui.source == PlayerSubtitleSource.ASSRT, Modifier.focusTarget("quick:source:assrt"), enabled = !controller.busy)
                TextTab("OpenSubtitles", { controller.query(fieldState.text.toString()); controller.source(PlayerSubtitleSource.OPEN_SUBTITLES) },
                    ui.source == PlayerSubtitleSource.OPEN_SUBTITLES, Modifier.focusTarget("quick:source:os"), enabled = !controller.busy)
            }
            val search: () -> Unit = {
                controller.query(fieldState.text.toString())
                controller.search()
                keyboard?.hide()
            }
            val field: @Composable (Modifier) -> Unit = { modifier ->
                EntryTextField("搜索关键词", fieldState, "作品名或文件名", modifier.focusTarget("quick:query"),
                    enabled = !controller.busy, onSubmit = search)
            }
            val action: @Composable () -> Unit = {
                ActionButton("搜索", search, Modifier.focusTarget("quick:search"), Glyph.Search,
                    primary = true, loading = ui.initializing || searching,
                    enabled = !controller.busy && !searching)
            }
            if (tv) TvSubtitleSearchInput(field, action) else MobileSubtitleSearchInput(field, action)
            ui.error?.let { Caption(it, color = Palette.Error) }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (ui.source == PlayerSubtitleSource.ASSRT) QuickAssrtResults(controller, assrtScroll)
                else QuickOpenSubtitlesResults(controller, osScroll)
            }
        } else {
            ActionButton("返回搜索", controller::back, Modifier.focusTarget("quick:files-back"), Glyph.Back, quiet = true)
            if (stage == "encoding") {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    SubtitleEncodingContent(controller.download, "重新保存并继续")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextTab("本集相关", { controller.showAll(false) }, !ui.showAll,
                        Modifier.focusTarget("quick:related"), enabled = !controller.busy)
                    TextTab("包内全部", { controller.showAll(true) }, ui.showAll,
                        Modifier.focusTarget("quick:all"), enabled = !controller.busy)
                }
                val error = ui.error ?: transfer.error ?: if (ui.source == PlayerSubtitleSource.ASSRT) assrt.error else os.error
                error?.let { Caption(it, color = Palette.Error, maxLines = 4) }
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = filesScroll,
                    contentPadding = PaddingValues(bottom = 12.dp)) {
                    if (candidates.isEmpty()) item("empty") {
                        SubtitleEmpty("没有本集相关文件", "可切换到“包内全部”手动选择")
                    }
                    items(candidates, key = { names[it] + ":" + it }) { index ->
                        val pack = ui.pack.takeIf { choosingPack }
                        val fileKey = when {
                            pack != null -> "quick:local:${pack.files[index].fileId}"
                            assrtFiles -> "quick:assrt-file:$index"
                            else -> "quick:os-file:$index"
                        }
                        val loading = ui.applying && ui.downloadFocusKey == fileKey || when {
                            pack != null -> ui.applying && ui.selectedFileId == pack.files[index].fileId
                            assrtFiles -> transfer.busy && transfer.fileName == names[index]
                            else -> os.busyFile == os.selected!!.attributes.files[index].id ||
                                transfer.busy && os.transferringFile == os.selected!!.attributes.files[index].id
                        }
                        SubtitleResultRow(names[index], if (pack != null) "保存到当前集及版本并使用" else "下载后选择字幕", names[index]) {
                            ActionButton(if (pack != null) "使用" else "下载", {
                                when {
                                    pack != null -> controller.use(pack.files[index].fileId)
                                    assrtFiles -> controller.download(assrt.files[index])
                                    else -> controller.download(os.selected!!.attributes.files[index])
                                }
                            }, Modifier.focusTarget(fileKey), loading = loading, enabled = !controller.busy)
                        }
                    }
                }
            }
        }
    }
    val title = when {
        stage == "encoding" -> "字幕编码"
        choosingPack -> "选择字幕"
        stage != "results" -> "选择下载文件"
        else -> "搜索字幕"
    }
    if (tv) TvPlayerSubtitleSearchLayout(ui.closing, close, controller::finishClose, title, content)
    else MobilePlayerSubtitleSearchLayout(ui.closing, close, controller::finishClose, title, content)
}

@Composable
private fun QuickAssrtResults(controller: PlayerSubtitleSearchController, scroll: LazyListState) {
    val ui by controller.assrt.state.collectAsState()
    val search by controller.state.collectAsState()
    val transfer by controller.download.state.collectAsState()
    LazyColumn(state = scroll, contentPadding = PaddingValues(bottom = 12.dp)) {
        item("attribution") { Caption("字幕服务由 assrt.net 提供") }
        if (search.initializing || ui.loading && ui.results.isEmpty()) item("skeleton") { QuickSubtitleSkeleton() }
        if (!search.initializing && !ui.loading && !ui.available) item("configuration") {
            SubtitleEmpty("尚未配置 ASSRT", "请先在字幕管理的来源设置中启用 ASSRT 并填写 Token")
        }
        items(ui.results, key = { it.id }) { entry ->
            SubtitleResultRow(entry.nativeName.ifBlank { entry.videoname },
                listOf(entry.lang.desc, entry.subtype).filter { it.isNotBlank() }.joinToString(" · "), entry.videoname,
                error = ui.error.takeIf { ui.failedId == entry.id }
                    ?: transfer.error.takeIf { ui.transferringId == entry.id }) {
                ActionButton("下载", { controller.choose(entry) }, Modifier.focusTarget("quick:assrt:${entry.id}"),
                    loading = ui.busyId == entry.id || transfer.busy && ui.transferringId == entry.id ||
                        search.applying && search.downloadFocusKey == "quick:assrt:${entry.id}",
                    enabled = !controller.busy && !ui.loading)
            }
        }
        if (ui.error != null) item("error") {
            if (ui.results.isEmpty()) ErrorContent("暂时无法搜索字幕", ui.error!!, "重试搜索", controller::retry, loading = ui.loading)
            else RegionLoadFeedback(ui.loading, ui.error,
                if (ui.nextPosition != null && ui.failedId == null && ui.selected == null) controller::more else controller::retry,
                true, reserveSpace = false)
        }
        if (ui.available && !ui.loading && ui.results.isEmpty() && ui.error == null) item("empty") {
            SubtitleEmpty("没有找到字幕", "尝试修改关键词或切换来源")
        }
        if (ui.nextPosition != null || ui.loadingMore) item("more") {
            ActionButton("加载更多", controller::more, Modifier.focusTarget("quick:assrt-more"),
                loading = ui.loadingMore, enabled = !controller.busy && !ui.loading)
        }
    }
}

@Composable
private fun QuickOpenSubtitlesResults(controller: PlayerSubtitleSearchController, scroll: LazyListState) {
    val ui by controller.openSubtitles.state.collectAsState()
    val search by controller.state.collectAsState()
    val transfer by controller.download.state.collectAsState()
    LazyColumn(state = scroll, contentPadding = PaddingValues(bottom = 12.dp)) {
        item("attribution") { Caption("字幕服务由 OpenSubtitles.com 提供") }
        ui.remaining?.let { item("quota") { Caption("剩余下载额度：$it") } }
        if (search.initializing || ui.loading && ui.results.isEmpty()) item("skeleton") { QuickSubtitleSkeleton() }
        if (!search.initializing && !ui.loading && !ui.available) item("configuration") {
            SubtitleEmpty("尚未配置 OpenSubtitles", "请先在字幕管理的来源设置中配置并启用 OpenSubtitles")
        }
        items(ui.results, key = { it.id }) { entry ->
            val data = entry.attributes
            SubtitleResultRow(data.feature.title.ifBlank { data.release }, "${data.language} · ${data.files.size} 个文件", data.release,
                error = (ui.error ?: transfer.error).takeIf { ui.selected?.id == entry.id }) {
                ActionButton("下载", { controller.choose(entry) }, Modifier.focusTarget("quick:os:${entry.id}"),
                    loading = ui.selected?.id == entry.id && (ui.busyFile != null || transfer.busy) ||
                        search.applying && search.downloadFocusKey == "quick:os:${entry.id}",
                    enabled = !controller.busy && !ui.loading)
            }
        }
        if (ui.error != null) item("error") {
            if (ui.results.isEmpty()) ErrorContent("暂时无法搜索字幕", ui.error!!, "重试搜索", controller::retry, loading = ui.loading)
            else RegionLoadFeedback(ui.loading, ui.error,
                if (ui.nextPage != null && ui.selected == null) controller::more else controller::retry,
                true, reserveSpace = false)
        }
        if (ui.available && !ui.loading && ui.results.isEmpty() && ui.error == null) item("empty") {
            SubtitleEmpty("没有找到字幕", "尝试修改关键词或切换来源")
        }
        if (ui.nextPage != null || ui.loadingMore) item("more") {
            ActionButton("加载更多", controller::more, Modifier.focusTarget("quick:os-more"),
                loading = ui.loadingMore, enabled = !controller.busy && !ui.loading)
        }
    }
}

@Composable
private fun QuickSubtitleSkeleton() {
    repeat(3) { SkeletonBlock(Modifier.fillMaxWidth().height(96.dp).padding(vertical = 8.dp)) }
}
