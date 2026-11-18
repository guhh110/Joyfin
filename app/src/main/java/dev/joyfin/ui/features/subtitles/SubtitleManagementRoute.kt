package dev.joyfin.ui.features.subtitles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
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
import dev.joyfin.ui.model.BrowseActions
import dev.joyfin.ui.features.subtitles.mobile.MobileSubtitleSearchInput

@Composable
fun SubtitleManagementRoute(controller: SubtitlePageController, images: ArtworkSource, onExit: () -> Unit,
    browseActions: BrowseActions? = null) {
    val ui by controller.state.collectAsState()
    val pages = rememberSaveableStateHolder()
    // Separate focus nodes as well as saved state: reusing the header on a new page
    // can otherwise replace the departing page's remembered row with its back button.
    key(ui.screen) {
        pages.SaveableStateProvider(ui.screen.name) {
            RememberScreenFocus(when (ui.screen) {
                SubtitleScreen.MANAGEMENT -> "subtitles:package:initial"
                SubtitleScreen.SEARCH -> "subtitles:query"
                SubtitleScreen.ORGANIZE -> "subtitles:episode:initial"
            }) { SubtitleManagementScreen(controller, images, onExit, browseActions) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubtitleManagementScreen(controller: SubtitlePageController, images: ArtworkSource, onExit: () -> Unit,
    browseActions: BrowseActions?) {
    val ui by controller.state.collectAsState()
    val editor by controller.organizer.state.collectAsState()
    val upload by controller.uploader.state.collectAsState()
    val transfer by controller.download.state.collectAsState()
    val assrt by controller.assrt.state.collectAsState()
    val os by controller.openSubtitles.state.collectAsState()
    var group by rememberSaveable { mutableStateOf("all") }
    var source by rememberSaveable { mutableStateOf("assrt") }
    var dialog by remember { mutableStateOf<String?>(null) }
    var pendingNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pickedMedia by remember { mutableStateOf<Media?>(null) }
    var pickedVersion by remember { mutableStateOf<String?>(null) }
    var returnFocus by remember { mutableIntStateOf(0) }
    val pageFocus = LocalScreenFocus.current
    val tv = LocalDeviceLayout.current.television
    val inputMode = LocalInputModeManager.current
    LaunchedEffect(tv) { if (tv) inputMode.requestInputMode(InputMode.Keyboard) }
    val searchText = rememberTextFieldState(ui.query)
    val workText = rememberTextFieldState()
    val assrtScroll = rememberLazyListState()
    val osScroll = rememberLazyListState()
    val managementScroll = rememberLazyListState()
    val filesScroll = rememberLazyListState()
    fun dismiss() { dialog = null; pendingNavigation = null; returnFocus++ }
    fun back() {
        pendingNavigation = null
        if (editor.dirty && ui.screen == SubtitleScreen.ORGANIZE) dialog = "discard"
        else if (!controller.back()) onExit()
    }
    fun navigate(action: () -> Unit) {
        if (editor.dirty && ui.screen == SubtitleScreen.ORGANIZE) {
            pendingNavigation = action
            dialog = "discard"
        } else action()
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
    val pageTitle = when (ui.screen) {
            SubtitleScreen.ORGANIZE -> "整理字幕"
            SubtitleScreen.SEARCH -> "搜索字幕"
            else -> "字幕管理"
        }
    val header: @Composable () -> Unit = {
        if (tv && browseActions != null) CinemaHeader(pageTitle, browseActions.copy(
            back = ::back,
            home = { navigate(browseActions.home) }, tracking = { navigate(browseActions.tracking) },
            library = { id, title -> navigate { browseActions.library(id, title) } },
            search = { navigate(browseActions.search) }, settings = { navigate(browseActions.settings) },
            subtitleManager = { navigate(controller::management) }), active = "subtitles", backFocusKey = "subtitles:back",
            tools = { IconButton("字幕工具", Glyph.Settings, { dialog = "more" }, Modifier.focusTarget("subtitles:more")) })
        else PageHeader(pageTitle, onBack = ::back, backFocusKey = "subtitles:back") {
            IconButton("字幕工具", Glyph.Settings, { dialog = "more" }, Modifier.focusTarget("subtitles:more"))
        }
    }
    val inspection = remember { SubtitleInspection() }
    val controls: @Composable () -> Unit = {
        when (ui.screen) {
            SubtitleScreen.MANAGEMENT -> SubtitleManagementControls(visiblePacks, group, { group = it }, controller::showSearch,
                if (ui.filterWork) listOfNotNull(ui.context?.work?.name, ui.context?.seasonMedia?.name).joinToString(" · ") else null,
                controller::allWorks)
            SubtitleScreen.SEARCH -> SubtitleSearchControls(
                field = { modifier -> EntryTextField("作品名称", searchText, "作品名、原名或文件名", modifier.focusTarget("subtitles:query"),
                    showLabel = false, onSubmit = controller::search) },
                submit = { ActionButton("搜索", controller::search, Modifier.focusTarget("subtitles:search"), primary = true,
                    loading = if (source == "assrt") assrt.loading && !assrt.loadingMore else os.loading && !os.loadingMore,
                    enabled = !assrt.loading && !os.loading, maxLines = 1) },
                source = source, selectSource = { source = it; inspection.value = null },
                language = when (ui.language) { "zh" -> "中文"; "en" -> "英语"; else -> "全部语言" },
                season = if (ui.seasons.isNotEmpty()) ui.context?.seasonMedia?.name ?: "选择季" else null,
                chooseLanguage = { dialog = "language" }, chooseSeason = { dialog = "search-season" }, filters = { dialog = "filters" })
            SubtitleScreen.ORGANIZE -> Unit
        }
    }
    val content: @Composable () -> Unit = {
        CompositionLocalProvider(LocalSubtitleInspection provides inspection) {
            Column(Modifier.fillMaxSize()) {
                ui.error?.let {
                    SubtitleFullText(it, Palette.Error)
                    if (ui.screen == SubtitleScreen.MANAGEMENT) ActionButton("重新读取", controller::reloadPackages, enabled = !ui.packagesLoading)
                }
                Box(Modifier.weight(1f)) {
                    when (ui.screen) {
                        SubtitleScreen.MANAGEMENT -> SubtitleBrowsePane {
                            SubtitlePackageList(visiblePacks, group, ui.loading || !ui.packagesLoaded || ui.packagesLoading, upload.busy || editor.saving,
                                managementScroll, images, controller::organize)
                        }
                        SubtitleScreen.SEARCH -> SubtitleBrowsePane {
                            if (source == "assrt") AssrtResults(controller.assrt, controller.download, assrtScroll) { dialog = "settings" }
                            else OpenSubtitlesResults(controller.openSubtitles, controller.download, osScroll) { dialog = "settings" }
                        }
                        SubtitleScreen.ORGANIZE -> SubtitleOrganizerPage(editor,
                            chooseWork = { workText.edit { replace(0, length, "") }; controller.findWorks(""); dialog = "work" },
                            chooseSeason = { dialog = "organize-season" }, chooseVersion = { dialog = "version-family" },
                            viewFiles = { dialog = "package-info" }) {
                            SubtitleEpisodeList(editor, filesScroll, ::chooseEpisode)
                        }
                    }
                }
                if (ui.screen == SubtitleScreen.SEARCH) {
                    transfer.error?.let { SubtitleFullText(it, Palette.Error) }
                    transfer.notice?.let {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Caption(it, Modifier.weight(1f), maxLines = 2)
                            ActionButton("查看下载包", controller::management, quiet = true, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
    val actions: @Composable () -> Unit = {
        if (ui.screen == SubtitleScreen.ORGANIZE && pack != null) {
            val status: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Caption(when { editor.dirty -> "有未保存的修改"; pack.localSaved -> "对应关系已保存"; else -> "对应关系待保存" },
                        color = if (editor.dirty) Palette.Text else Palette.Muted, maxLines = 1)
                    Caption("${pack.matched} / ${pack.files.size} 份字幕已对应", maxLines = 1)
                }
            }
            val save: @Composable (Modifier) -> Unit = { modifier ->
                ActionButton(if (pack.localSaved && !editor.dirty) "已保存本地" else "保存本地", { dialog = "save" },
                    modifier.focusTarget("subtitles:save"), primary = true, enabled = pack.workId != null && !editor.loading && !editor.saving,
                    loading = editor.saving && !upload.busy, maxLines = 1)
            }
            val send: @Composable (Modifier) -> Unit = { modifier ->
                ActionButton(if (pack.failed) "重试回传" else "回传 Jellyfin", { controller.uploader.prepare(); dialog = "upload" },
                    modifier.focusTarget("subtitles:upload"), loading = upload.busy,
                    enabled = pack.matched > 0 && !editor.loading && !editor.saving, maxLines = 1)
            }
            if (tv) TvSubtitleOrganizerActions(status, save, send)
            else MobileSubtitleOrganizerActions(status, save, send)
        }
    }
    if (tv) TvSubtitleManagementLayout(header, controls, content, actions, hasActions = ui.screen == SubtitleScreen.ORGANIZE && pack != null)
    else MobileSubtitleManagementLayout(header, controls, content, actions, hasActions = ui.screen == SubtitleScreen.ORGANIZE && pack != null)

    if (inspection.opened) Sheet("完整名称与详情", { inspection.opened = false }, initialFocus = "sheet:close") {
        inspection.value?.let { SubtitleDetailsContent(it) }
    }
    if (transfer.needsEncoding) SubtitleEncodingSheet(controller.download)
    when (dialog) {
        "package-info" -> Sheet("字幕包信息", ::dismiss, initialFocus = "sheet:close") {
            pack?.let {
                Caption(listOf(it.workName, it.seasonName).filter(String::isNotBlank).joinToString(" · "))
                SubtitleFullText(it.title)
                Caption(it.origin)
                it.files.forEach { file ->
                    SubtitleDivider()
                    SubtitleFullText(file.name, Palette.Text)
                    Caption(file.mapping?.let { mapping -> "${mapping.itemName} · ${mapping.versionName}" } ?: "未关联")
                    file.uploadError?.let { error -> SubtitleFullText(error, Palette.Error) }
                }
            }
        }
        "settings" -> SubtitleSourcesRoute(controller) { dismiss(); controller.refreshSources(repeatSearch = ui.screen == SubtitleScreen.SEARCH) }
        "more" -> Sheet("字幕工具", ::dismiss, initialFocus = "sheet:close") {
            ActionButton("来源设置", { dialog = "settings" }, Modifier.fillMaxWidth())
            if (ui.screen == SubtitleScreen.SEARCH) ActionButton("请求历史", { dialog = "history" }, Modifier.fillMaxWidth())
        }
        "filters" -> Sheet("搜索筛选", ::dismiss, initialFocus = "sheet:close") {
            ActionButton("语言：${when (ui.language) { "zh" -> "中文"; "en" -> "英语"; else -> "全部语言" }}", { dialog = "language" })
            if (ui.seasons.isNotEmpty()) ActionButton(ui.context?.seasonMedia?.name ?: "选择季", { dialog = "search-season" })
        }
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
            Sheet("尚有未保存的对应关系", ::dismiss, side = false) {
                ActionButton("继续整理", ::dismiss, Modifier.focusTarget("subtitles:stay"), primary = true)
                ActionButton("放弃本次修改并返回", {
                    val navigation = pendingNavigation
                    dismiss()
                    controller.management()
                    navigation?.invoke()
                })
            }
        }
    }
}

