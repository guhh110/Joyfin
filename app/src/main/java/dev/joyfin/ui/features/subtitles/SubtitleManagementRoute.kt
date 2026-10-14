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
import androidx.compose.ui.unit.dp
import dev.joyfin.data.Media
import dev.joyfin.subtitles.*
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.entry.components.EntryTextField
import dev.joyfin.ui.features.subtitles.mobile.MobileSubtitleManagementLayout
import dev.joyfin.ui.features.subtitles.tv.TvSubtitleManagementLayout
import dev.joyfin.ui.interaction.*
import dev.joyfin.ui.theme.Palette

@Composable
fun SubtitleManagementRoute(controller: SubtitlePageController, onExit: () -> Unit) {
    val ui by controller.state.collectAsState()
    val pages = rememberSaveableStateHolder()
    pages.SaveableStateProvider(ui.screen.name) {
        RememberScreenFocus(when (ui.screen) {
            SubtitleScreen.MANAGEMENT -> "subtitles:back"
            SubtitleScreen.SEARCH -> "subtitles:query"
            SubtitleScreen.ORGANIZE -> "subtitles:work"
        }) { SubtitleManagementScreen(controller, onExit) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubtitleManagementScreen(controller: SubtitlePageController, onExit: () -> Unit) {
    val ui by controller.state.collectAsState()
    val editor by controller.organizer.state.collectAsState()
    val upload by controller.uploader.state.collectAsState()
    val transfer by controller.download.state.collectAsState()
    val assrt by controller.assrt.state.collectAsState()
    val os by controller.openSubtitles.state.collectAsState()
    var group by rememberSaveable { mutableStateOf("pending") }
    var source by rememberSaveable { mutableStateOf("assrt") }
    var dialog by remember { mutableStateOf<String?>(null) }
    var pickedMedia by remember { mutableStateOf<Media?>(null) }
    var pickedVersion by remember { mutableStateOf<String?>(null) }
    var calibrationStart by remember { mutableStateOf<Media?>(null) }
    var calibrationVersion by remember { mutableStateOf<String?>(null) }
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconButton("返回", Glyph.Back, ::back, Modifier.focusTarget("subtitles:back"))
            Column(Modifier.weight(1f)) {
                SectionTitle(when (ui.screen) { SubtitleScreen.MANAGEMENT -> "字幕管理"; SubtitleScreen.SEARCH -> "搜索字幕"; SubtitleScreen.ORGANIZE -> "整理对应关系" })
                Caption(if (ui.screen == SubtitleScreen.ORGANIZE) pack?.title.orEmpty()
                    else if (ui.filterWork) listOfNotNull(ui.context?.work?.name, ui.context?.seasonMedia?.name).joinToString(" · ") else "下载、整理与回传 Jellyfin")
            }
            if (ui.screen == SubtitleScreen.MANAGEMENT) ActionButton("搜索字幕", controller::showSearch,
                Modifier.focusTarget("subtitles:search-entry"), primary = true, loading = ui.loading, enabled = !ui.loading)
            if (ui.screen == SubtitleScreen.SEARCH) ActionButton("待整理 ${visiblePacks.count { it.pending }}", controller::management,
                Modifier.focusTarget("subtitles:pending"))
        }
    }
    val controls: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (ui.screen) {
                SubtitleScreen.MANAGEMENT -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("pending" to "待整理 ${visiblePacks.count { it.pending }}", "linked" to "已关联 ${visiblePacks.count { it.localSaved }}", "failed" to "回传失败 ${visiblePacks.count { it.failed }}").forEach { (id, title) ->
                        ActionButton(title, { group = id }, Modifier.focusTarget("subtitles:group:$id"), selected = group == id)
                    }
                    if (ui.filterWork) ActionButton("全部作品", controller::allWorks, quiet = true)
                }
                SubtitleScreen.SEARCH -> {
                    EntryTextField("搜索关键词", searchText, "作品名、原名或文件名", Modifier.focusTarget("subtitles:query"), onSubmit = controller::search)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton("搜索", controller::search, Modifier.focusTarget("subtitles:search"), primary = true,
                            loading = assrt.loading && !assrt.loadingMore || os.loading && !os.loadingMore,
                            enabled = !assrt.loading && !os.loading)
                        ActionButton("ASSRT", { source = "assrt" }, Modifier.focusTarget("subtitles:source:assrt"), selected = source == "assrt")
                        ActionButton("OpenSubtitles", { source = "os" }, Modifier.focusTarget("subtitles:source:os"), selected = source == "os")
                        ActionButton(when (ui.language) { "zh" -> "中文"; "en" -> "英语"; else -> "全部语言" }, { dialog = "language" }, Modifier.focusTarget("subtitles:language"))
                        if (ui.seasons.isNotEmpty()) ActionButton(ui.context?.seasonMedia?.name ?: "选择季", { dialog = "search-season" }, Modifier.focusTarget("subtitles:search-season"))
                        ActionButton("请求历史", { dialog = "history" }, Modifier.focusTarget("subtitles:history"), quiet = true)
                        ActionButton("来源设置", { dialog = "settings" }, Modifier.focusTarget("subtitles:settings"), quiet = true)
                    }
                    transfer.notice?.let { Caption(it) }
                    transfer.error?.let { Caption(it, color = Palette.Error) }
                }
                SubtitleScreen.ORGANIZE -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton(if (pack?.workId == null) "选择作品" else pack.workName, {
                            workText.edit { replace(0, length, "") }
                            controller.findWorks("")
                            dialog = "work"
                        }, Modifier.focusTarget("subtitles:work"), enabled = !editor.loading && !editor.saving,
                            loading = editor.loading, primary = pack?.workId == null)
                        if (editor.seasons.isNotEmpty()) ActionButton(pack?.seasonName ?: "选择季", { dialog = "organize-season" },
                            Modifier.focusTarget("subtitles:organize-season"), enabled = !editor.loading && !editor.saving)
                        if (pack?.workId != null) ActionButton("媒体版本", { dialog = "version-family" }, Modifier.focusTarget("subtitles:version-family"), enabled = !editor.loading && !editor.saving)
                    }
                    if (pack?.workId != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Caption("${pack.files.mapNotNull { it.mapping?.itemId }.distinct().size} 集 · ${pack.matched} 份字幕已对应")
                        if (editor.work?.type == "Series") ActionButton("整包顺序校准", { dialog = "calibrate" },
                            Modifier.focusTarget("subtitles:calibrate"), enabled = !editor.loading && !editor.saving, quiet = true)
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
                upload.busy || editor.saving, managementScroll, controller::organize)
            SubtitleScreen.SEARCH -> if (source == "assrt") AssrtResults(controller.assrt, controller.download, assrtScroll) { dialog = "settings" }
                else OpenSubtitlesResults(controller.openSubtitles, controller.download, osScroll) { dialog = "settings" }
            SubtitleScreen.ORGANIZE -> SubtitleEpisodeList(editor, filesScroll, ::chooseEpisode)
        }
    }
    val actions: @Composable () -> Unit = {
        if (ui.screen == SubtitleScreen.ORGANIZE && pack != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(if (pack.localSaved && !editor.dirty) "本地关联已保存" else "保存本地关联", controller.organizer::save,
                Modifier.focusTarget("subtitles:save"), enabled = pack.workId != null && !editor.loading && !editor.saving,
                loading = editor.saving && !upload.busy)
            ActionButton(if (pack.failed) "重试回传" else "回传 Jellyfin", { controller.uploader.prepare(); dialog = "upload" },
                Modifier.focusTarget("subtitles:upload"), primary = true, loading = upload.busy,
                enabled = pack.matched > 0 && !editor.loading && !editor.saving )
            Caption("未对应文件暂时保留；一集可关联多份字幕，播放器可切换；回传时可替换旧字幕。")
        }
    }
    if (tv) TvSubtitleManagementLayout(header, controls, content, actions)
    else MobileSubtitleManagementLayout(header, controls, content, actions)

    if (transfer.needsEncoding) SubtitleEncodingSheet(controller.download)
    when (dialog) {
        "settings" -> SubtitleSourcesRoute(controller) { dismiss(); controller.search() }
        "history" -> {
            val urls by (if (source == "assrt") controller.assrt.requestUrls else controller.openSubtitles.requestUrls).collectAsState()
            SubtitleRequestHistorySheet(if (source == "assrt") "ASSRT" else "OpenSubtitles", urls, ::dismiss)
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
        "calibrate" -> SubtitleEpisodeSheet("第一份文件对应哪一集？", editor.media, editor.media.firstOrNull()?.id, ::dismiss, skip = null) { media ->
            calibrationStart = media
            calibrationVersion = media.sources.singleOrNull()?.name
            dialog = if (media.sources.size > 1) "calibrate-version" else "calibrate-confirm"
        }
        "version", "calibrate-version" -> {
            val calibration = dialog == "calibrate-version"
            val media = if (calibration) calibrationStart else pickedMedia
            val selected = if (calibration) null else pickedVersion ?: pack?.files?.firstOrNull { it.mapping?.itemId == media?.id }?.mapping?.versionId
            SubtitleChoiceSheet("选择媒体版本", media?.sources.orEmpty().map { it.id to it.name.ifBlank { it.container.uppercase() } }, selected, ::dismiss) { id ->
                if (calibration) { calibrationVersion = media?.sources?.firstOrNull { it.id == id }?.name; dialog = "calibrate-confirm" }
                else { pickedVersion = id; dialog = "episode-files" }
            }
        }
        "episode-files" -> pickedMedia?.let { media -> pickedVersion?.let { version ->
            SubtitleEpisodeFilesSheet(controller.organizer, media, version, ::dismiss)
        } }
        "calibrate-confirm" -> RememberSelectionFocus("subtitles:calibrate-apply") {
            Sheet("确认整包顺序", ::dismiss) {
                Caption("按文件名自然顺序：第一份文件 → ${calibrationStart?.label}")
                Caption("后续文件依次对应。本季范围外的文件、无法确定版本的文件保持未对应；现有手动对应会重新排列。")
                ActionButton("应用顺序", { calibrationStart?.let { controller.organizer.sequence(it.id, calibrationVersion) }; dismiss() },
                    Modifier.focusTarget("subtitles:calibrate-apply"), primary = true)
            }
        }
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

