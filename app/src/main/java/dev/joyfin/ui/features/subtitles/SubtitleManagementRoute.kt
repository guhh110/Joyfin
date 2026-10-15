package dev.joyfin.ui.features.subtitles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.joyfin.data.Media
import dev.joyfin.subtitles.*
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.entry.components.EntryTextField
import dev.joyfin.ui.features.subtitles.mobile.MobileSubtitleOrganizerActions
import dev.joyfin.ui.features.subtitles.mobile.MobileSubtitleManagementLayout
import dev.joyfin.ui.features.subtitles.tv.TvSubtitleOrganizerActions
import dev.joyfin.ui.features.subtitles.tv.TvSubtitleManagementLayout
import dev.joyfin.ui.features.subtitles.tv.TvSubtitleSearchInput
import dev.joyfin.ui.interaction.*
import dev.joyfin.ui.theme.Palette
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.features.subtitles.mobile.MobileSubtitleSearchInput

@Composable
fun SubtitleManagementRoute(controller: SubtitlePageController, images: ArtworkSource, onExit: () -> Unit) {
    val ui by controller.state.collectAsState()
    val pages = rememberSaveableStateHolder()
    pages.SaveableStateProvider(ui.screen.name) {
        RememberScreenFocus(when (ui.screen) {
            SubtitleScreen.MANAGEMENT -> "subtitles:back"
            SubtitleScreen.SEARCH -> "subtitles:query"
            SubtitleScreen.ORGANIZE -> "subtitles:work"
        }) { SubtitleManagementScreen(controller, images, onExit) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubtitleManagementScreen(controller: SubtitlePageController, images: ArtworkSource, onExit: () -> Unit) {
    val ui by controller.state.collectAsState()
    val editor by controller.organizer.state.collectAsState()
    val upload by controller.uploader.state.collectAsState()
    val transfer by controller.download.state.collectAsState()
    val assrt by controller.assrt.state.collectAsState()
    val os by controller.openSubtitles.state.collectAsState()
    var group by rememberSaveable { mutableStateOf("all") }
    var source by rememberSaveable { mutableStateOf("assrt") }
    var dialog by remember { mutableStateOf<String?>(null) }
    var pickedMedia by remember { mutableStateOf<Media?>(null) }
    var pickedVersion by remember { mutableStateOf<String?>(null) }
    var returnFocus by remember { mutableIntStateOf(0) }
    val pageFocus = LocalScreenFocus.current
    val tv = LocalDeviceLayout.current.television
    val searchText = rememberTextFieldState(ui.query)
    val workText = rememberTextFieldState()
    val assrtScroll = rememberLazyListState()
    val osScroll = rememberLazyListState()
    val managementScroll = rememberLazyListState()
    val filesScroll = rememberLazyListState()
    fun dismiss() { dialog = null; returnFocus++ }
    fun back() {
        if (editor.dirty && ui.screen == SubtitleScreen.ORGANIZE) dialog = "discard"
        else if (!controller.back()) onExit()
    }
    fun chooseEpisode(media: Media) {
        pickedMedia = media
        pickedVersion = media.sources.singleOrNull()?.id
        dialog = if (media.sources.size == 1) "episode-files" else "version"
    }
    BackHandler { if (dialog != null) dismiss() else back() }
    LaunchedEffect(returnFocus) {
        if (returnFocus > 0 && tv) {
            withFrameNanos {}
            val target = pageFocus?.target?.invoke()
            if (target != null && pageFocus?.requesters?.containsKey(target) == true) pageFocus?.restore()
            else pageFocus?.requesters?.get("subtitles:work")?.requestFocus()
        }
    }
    LaunchedEffect(ui.query) { if (searchText.text.toString() != ui.query) searchText.edit { replace(0, length, ui.query) } }
    LaunchedEffect(searchText) { snapshotFlow { searchText.text.toString() }.collect { if (it != controller.state.value.query) controller.query(it) } }
    LaunchedEffect(transfer.notice, transfer.needsEncoding) {
        if (transfer.notice != null || transfer.needsEncoding) { controller.assrt.dismissFiles(); controller.openSubtitles.dismissFiles() }
    }
    LaunchedEffect(upload.result, upload.error, ui.context?.owner) {
        val belongs = upload.result?.owner == ui.context?.owner || ui.packages.any { it.id == upload.packageId }
        if (!upload.busy && belongs && (upload.result != null || upload.error != null)) dialog = "upload-result"
    }
    val pack = editor.pack
    val visiblePacks = ui.packages.filter { !ui.filterWork || it.workId == ui.context?.work?.id }
    val header: @Composable () -> Unit = {
        val heading: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SubtitleTitle(when (ui.screen) {
                    SubtitleScreen.ORGANIZE -> pack?.takeIf { it.workName.isNotBlank() }?.let {
                        listOf(it.workName, it.seasonName).filter(String::isNotBlank).joinToString(" · ")
                    } ?: "整理字幕"
                    SubtitleScreen.SEARCH -> "搜索字幕"
                    else -> "字幕管理"
                }, large = true, maxLines = if (tv) 2 else 3)
                if (ui.screen == SubtitleScreen.ORGANIZE) {
                    Caption(pack?.title.orEmpty(), maxLines = 1)
                    Caption(pack?.origin?.substringBefore(" · ").orEmpty(), maxLines = 1)
                } else if (ui.filterWork) Caption(listOfNotNull(ui.context?.work?.name, ui.context?.seasonMedia?.name)
                    .joinToString(" · "), maxLines = 2)
            }
        }
        val headerAction: @Composable () -> Unit = {
            when (ui.screen) {
                SubtitleScreen.MANAGEMENT -> ActionButton("搜索字幕", controller::showSearch,
                    Modifier.focusTarget("subtitles:search-entry"), Glyph.Search, quiet = true,
                    enabled = !ui.loading, maxLines = 1)
                SubtitleScreen.SEARCH -> ActionButton("请求历史", { dialog = "history" },
                    Modifier.focusTarget("subtitles:history"), quiet = true, maxLines = 1)
                else -> Unit
            }
        }
        val narrow = !tv && LocalDeviceLayout.current.widthDp / LocalDensity.current.fontScale < 380
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconButton("返回", Glyph.Back, ::back, Modifier.focusTarget("subtitles:back"))
                Box(Modifier.weight(1f)) { heading() }
                if (!narrow) headerAction()
            }
            if (narrow && ui.screen != SubtitleScreen.ORGANIZE) Box(Modifier.padding(start = 58.dp)) { headerAction() }
        }
    }
    val controls: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (ui.screen) {
                SubtitleScreen.MANAGEMENT -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextTab("下载的字幕包", {}, true, Modifier.focusTarget("subtitles:manage-tab"))
                        TextTab("搜索下载", controller::showSearch, false, Modifier.focusTarget("subtitles:search-tab"), enabled = !ui.loading)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("all" to "全部 ${visiblePacks.size}", "pending" to "待整理 ${visiblePacks.count { it.pending }}",
                            "linked" to "已关联 ${visiblePacks.count { it.localSaved }}", "failed" to "失败 ${visiblePacks.count { it.failed }}").forEach { (id, title) ->
                            TextTab(title, { group = id }, group == id, Modifier.focusTarget("subtitles:group:$id"), compact = true)
                        }
                        if (ui.filterWork) ActionButton("全部作品", controller::allWorks, quiet = true, maxLines = 1)
                    }
                }
                SubtitleScreen.SEARCH -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextTab("下载的字幕包", controller::management, false, Modifier.focusTarget("subtitles:pending"))
                        TextTab("搜索下载", {}, true, Modifier.focusTarget("subtitles:search-tab"))
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextTab("ASSRT", { source = "assrt" }, source == "assrt", Modifier.focusTarget("subtitles:source:assrt"))
                        TextTab("OpenSubtitles", { source = "os" }, source == "os", Modifier.focusTarget("subtitles:source:os"))
                    }
                    val field: @Composable (Modifier) -> Unit = { modifier ->
                        EntryTextField("作品名称", searchText, "作品名、原名或文件名", modifier.focusTarget("subtitles:query"), onSubmit = controller::search)
                    }
                    val searchAction: @Composable () -> Unit = {
                        ActionButton("搜索", controller::search, Modifier.focusTarget("subtitles:search"), primary = true,
                            loading = assrt.loading && !assrt.loadingMore || os.loading && !os.loadingMore,
                            enabled = !assrt.loading && !os.loading, maxLines = 1)
                    }
                    if (tv) TvSubtitleSearchInput(field, searchAction) else MobileSubtitleSearchInput(field, searchAction)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ActionButton(when (ui.language) { "zh" -> "中文"; "en" -> "英语"; else -> "全部语言" }, { dialog = "language" }, Modifier.focusTarget("subtitles:language"), quiet = true)
                        if (ui.seasons.isNotEmpty()) ActionButton(ui.context?.seasonMedia?.name ?: "选择季", { dialog = "search-season" }, Modifier.focusTarget("subtitles:search-season"), quiet = true)
                        ActionButton("来源设置", { dialog = "settings" }, Modifier.focusTarget("subtitles:settings"), quiet = true)
                    }
                    transfer.notice?.let { Caption(it) }
                    transfer.error?.let { Caption(it, color = Palette.Error) }
                }
                SubtitleScreen.ORGANIZE -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ActionButton(if (pack?.workId == null) "选择作品" else "更换作品", {
                            workText.edit { replace(0, length, "") }
                            controller.findWorks("")
                            dialog = "work"
                        }, Modifier.focusTarget("subtitles:work"), enabled = !editor.loading && !editor.saving,
                            loading = editor.loading, primary = pack?.workId == null, quiet = pack?.workId != null)
                        if (editor.seasons.isNotEmpty()) ActionButton(pack?.seasonName ?: "选择季", { dialog = "organize-season" },
                            Modifier.focusTarget("subtitles:organize-season"), enabled = !editor.loading && !editor.saving, quiet = true)
                        if (pack?.workId != null) ActionButton("媒体版本", { dialog = "version-family" }, Modifier.focusTarget("subtitles:version-family"), enabled = !editor.loading && !editor.saving, quiet = true)
                    }
                    if (pack?.workId != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Caption("${pack.files.mapNotNull { it.mapping?.itemId }.distinct().size} 集 · ${pack.matched} 份字幕已对应")
                    }
                    editor.error?.let { Caption(it, color = Palette.Error) }
                    editor.notice?.let { Caption(it) }
                }
            }
            ui.error?.let { Caption(it, color = Palette.Error); ActionButton("重新读取", controller::reloadPackages, enabled = !ui.loading) }
        }
    }
    val content: @Composable () -> Unit = {
        when (ui.screen) {
            SubtitleScreen.MANAGEMENT -> SubtitlePackageList(visiblePacks, group, ui.loading,
                upload.busy || editor.saving, managementScroll, images, controller::organize)
            SubtitleScreen.SEARCH -> if (source == "assrt") AssrtResults(controller.assrt, controller.download, assrtScroll) { dialog = "settings" }
                else OpenSubtitlesResults(controller.openSubtitles, controller.download, osScroll) { dialog = "settings" }
            SubtitleScreen.ORGANIZE -> SubtitleEpisodeList(editor, filesScroll, ::chooseEpisode)
        }
    }

    val actions: @Composable () -> Unit = {
        if (ui.screen == SubtitleScreen.ORGANIZE && pack != null) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val status: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Caption(when { editor.dirty -> "有未保存的修改"; pack.localSaved -> "对应关系已保存"; else -> "对应关系待保存" },
                        color = if (editor.dirty) Palette.Text else Palette.Muted)
                    Caption("${pack.matched} / ${pack.files.size} 份字幕已对应", maxLines = 1)
                }
            }
            val save: @Composable (Modifier) -> Unit = { modifier ->
                ActionButton(if (pack.localSaved && !editor.dirty) "已保存本地" else "保存本地", { dialog = "save" },
                    modifier.focusTarget("subtitles:save"), primary = true, enabled = pack.workId != null && !editor.loading && !editor.saving,
                    loading = editor.saving && !upload.busy)
            }
            val send: @Composable (Modifier) -> Unit = { modifier ->
                ActionButton(if (pack.failed) "重试回传" else "回传 Jellyfin", { controller.uploader.prepare(); dialog = "upload" },
                    modifier.focusTarget("subtitles:upload"), loading = upload.busy,
                    enabled = pack.matched > 0 && !editor.loading && !editor.saving)
            }
            if (tv) TvSubtitleOrganizerActions(status, save, send)
            else MobileSubtitleOrganizerActions(status, save, send)
        }
    }
    if (tv) TvSubtitleManagementLayout(header, controls, content, actions, hasActions = ui.screen == SubtitleScreen.ORGANIZE && pack != null)
    else MobileSubtitleManagementLayout(header, controls, content, actions, hasActions = ui.screen == SubtitleScreen.ORGANIZE && pack != null)

    if (transfer.needsEncoding) SubtitleEncodingSheet(controller.download)
    when (dialog) {
        "settings" -> SubtitleSourcesRoute(controller) { dismiss(); controller.search() }
        "history" -> {
            val records by (if (source == "assrt") controller.assrt.requestHistory else controller.openSubtitles.requestHistory).collectAsState()
            SubtitleRequestHistorySheet(if (source == "assrt") "ASSRT" else "OpenSubtitles", records, ::dismiss)
        }
        "work" -> RememberSelectionFocus("subtitles:work-query") {
            Sheet("选择 Jellyfin 作品", ::dismiss) {
                EntryTextField("查找作品", workText, "电影或剧名", Modifier.focusTarget("subtitles:work-query"),
                    onSubmit = { controller.findWorks(workText.text.toString()) })
                ActionButton("查找", { controller.findWorks(workText.text.toString()) }, loading = ui.workLoading, enabled = !ui.workLoading)
                ui.workError?.let { Caption(it, color = Palette.Error) }
                if (!ui.workLoading && ui.works.isEmpty()) Caption("没有找到作品")
                ui.works.forEach { media -> ChoiceRow(media.name, Modifier.focusTarget("subtitles:work:${media.id}"),
                    subtitle = if (media.type == "Series") "剧集" else "电影 · ${media.year}", selected = media.id == pack?.workId) {
                    controller.organizer.work(media); dismiss()
                } }
                if (ui.workNext != null) ActionButton("加载更多作品", { controller.findWorks(ui.workQuery, true) }, enabled = !ui.workLoading)
            }
        }
        "language", "search-season", "organize-season", "version-family" -> {
            val kind = dialog
            val options = when (kind) {
                "language" -> listOf("zh" to "中文", "en" to "英语", "" to "全部语言")
                "search-season" -> ui.seasons.map { it.id to it.name }
                "organize-season" -> editor.seasons.map { it.id to it.name }
                else -> editor.media.flatMap { it.sources }.map { it.name to it.name.ifBlank { it.container.uppercase() } }.distinctBy { it.first }
            }
            val selected = when (kind) { "language" -> ui.language; "search-season" -> ui.context?.seasonMedia?.id; "organize-season" -> pack?.seasonId
                else -> pack?.files?.mapNotNull { it.mapping?.versionName }?.distinct()?.singleOrNull() }
            SubtitleChoiceSheet(when (kind) { "language" -> "字幕语言"; "version-family" -> "整包媒体版本"; else -> "选择季" }, options, selected, ::dismiss) { id ->
                when (kind) { "language" -> controller.language(id); "search-season" -> controller.searchSeason(id)
                    "organize-season" -> controller.organizer.season(id); else -> controller.organizer.versionFamily(id) }
                dismiss()
            }
        }
        "version" -> {
            val media = pickedMedia
            val selected = pickedVersion ?: pack?.files?.firstOrNull { it.mapping?.itemId == media?.id }?.mapping?.versionId
            SubtitleChoiceSheet("选择媒体版本", media?.sources.orEmpty().map { it.id to it.name.ifBlank { it.container.uppercase() } }, selected, ::dismiss) { id ->
                pickedVersion = id
                dialog = "episode-files"
            }
        }
        "episode-files" -> pickedMedia?.let { media -> pickedVersion?.let { version ->
            SubtitleEpisodeFilesSheet(controller.organizer, media, version, ::dismiss)
        } }
        "save" -> SubtitleSaveConfirmation(controller.organizer, ::dismiss)
        "upload" -> SubtitleUploadConfirmation(controller, ::dismiss) { dismiss(); controller.uploader.upload() }
        "upload-result" -> RememberSelectionFocus("sheet:close") {
            Sheet(if (upload.result?.failed == true || upload.error != null) "回传未全部完成" else "回传完成", {
                controller.uploader.dismissResult(); dismiss()
            }) {
                val result = upload.result
                Caption("成功 ${result?.uploaded ?: 0} 个 · 待回传 ${result?.files?.count { it.mapping != null && it.upload != "done" } ?: 0} 个")
                upload.error?.let { Caption(it, color = Palette.Error) }
                result?.cleanupError?.let { Caption(it, color = Palette.Error) }
                result?.files?.filter { it.upload in setOf("failed", "uncertain", "sending", "identifying") }?.forEach {
                    Caption(it.name); Caption(it.uploadError ?: "未收到回传确认，请先在服务器核对", color = Palette.Error)
                }
                Caption("文件与本地关联已保留，重试跳过已成功文件。")
                ActionButton("返回管理", { controller.uploader.dismissResult(); dismiss(); controller.management() })
            }
        }
        "discard" -> RememberSelectionFocus("subtitles:stay") {
            Sheet("尚有未保存的对应关系", ::dismiss) {
                ActionButton("继续整理", ::dismiss, Modifier.focusTarget("subtitles:stay"), primary = true)
                ActionButton("放弃本次修改并返回", { dismiss(); controller.management() })
            }
        }
    }
}

