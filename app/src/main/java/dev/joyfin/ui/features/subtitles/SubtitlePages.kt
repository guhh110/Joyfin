package dev.joyfin.ui.features.subtitles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.joyfin.data.subtitles.ManagedSubtitle
import dev.joyfin.subtitles.SubtitleOrganizerState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.*
import dev.joyfin.ui.theme.Palette

internal data class SubtitleDetails(val title: String, val name: String = "", val metadata: String = "", val error: String? = null)
internal class SubtitleInspection {
    var value by mutableStateOf<SubtitleDetails?>(null)
    var opened by mutableStateOf(false)
}
internal val LocalSubtitleInspection = staticCompositionLocalOf<SubtitleInspection?> { null }

@Composable
internal fun SubtitleListFocus(ids: List<String>, prefix: String, loading: Boolean, scroll: LazyListState, fallback: String,
    headerItems: Int = 0) {
    if (!LocalDeviceLayout.current.television) return
    val memory = LocalScreenFocus.current
    var previous by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var entering by remember { mutableStateOf(true) }
    val target = memory?.target?.invoke()
    LaunchedEffect(ids, loading) {
        if (loading) return@LaunchedEffect
        val key = target ?: return@LaunchedEffect
        val id = key.removePrefix("$prefix:")
        val removed = key.startsWith("$prefix:") && id in previous && id !in ids
        if (key == "$prefix:initial" || removed || entering && id in ids) {
            val index = if (removed) previous.indexOf(id).coerceIn(0, (ids.size - 1).coerceAtLeast(0)) else ids.indexOf(id).coerceAtLeast(0)
            val next = ids.getOrNull(index)?.let { "$prefix:$it" } ?: fallback
            memory.rememberTarget(next)
            withFrameNanos {}
            if (memory.target() != next) return@LaunchedEffect
            if (ids.isNotEmpty() && scroll.layoutInfo.visibleItemsInfo.none { it.index == index + headerItems })
                scroll.scrollToItem(index + headerItems)
            withFrameNanos {}
            memory.restore()
        }
        entering = false
        previous = ids
    }
}

/** Keep the remote's list geometry stable while the full release name changes. */
@Composable
internal fun SubtitleBrowsePane(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val tv = LocalDeviceLayout.current.television
    val inspection = LocalSubtitleInspection.current
    if (!tv || inspection == null) { Box(modifier.fillMaxSize()) { content() }; return }
    val memory = LocalScreenFocus.current
    var reading by remember { mutableStateOf(false) }
    var returnKey by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    LaunchedEffect(inspection.value) { scroll.scrollTo(0) }
    BackHandler(reading) { memory?.requesters?.get(returnKey)?.requestFocus() }
    Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(Modifier.weight(.7f).fillMaxHeight()) { content() }
        Column(Modifier.weight(.3f).fillMaxHeight()
            .background(Palette.Surface, RoundedCornerShape(12.dp))
            .border(if (reading) 2.dp else .5.dp, if (reading) Palette.Text else Palette.Line, RoundedCornerShape(12.dp))
            .onFocusChanged {
                if (it.isFocused && !reading) returnKey = memory?.target?.invoke().orEmpty()
                reading = it.isFocused
            }.focusable().verticalScroll(scroll).padding(18.dp).testTag("subtitle:inspection"),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Caption("详细信息")
            val value = inspection.value
            if (value == null) Caption("选择列表中的条目，查看完整名称与关联信息")
            else SubtitleDetailsContent(value)
        }
    }
}

@Composable
internal fun SubtitleDetailsContent(value: SubtitleDetails) {
    SubtitleFullText(value.title, color = Palette.Text)
    if (value.name.isNotBlank() && value.name != value.title) SubtitleFullText(value.name)
    if (value.metadata.isNotBlank()) SubtitleFullText(value.metadata)
    value.error?.let { SubtitleFullText(it, Palette.Error) }
}

@Composable
internal fun SubtitleManagementControls(packages: List<ManagedSubtitle>, group: String, select: (String) -> Unit,
    search: () -> Unit, context: String?, allWorks: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().testTag("subtitle:management-tools"), verticalAlignment = Alignment.CenterVertically) {
            SubtitleTitle("下载包", Modifier.weight(1f))
            ActionButton("搜索字幕", search, Modifier.focusTarget("subtitles:search-tab"), Glyph.Search, primary = true, maxLines = 1)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("subtitle:filters"),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("all" to "全部 ${packages.size}", "pending" to "待整理 ${packages.count { it.pending }}",
                "linked" to "已关联 ${packages.count { it.localSaved }}", "failed" to "失败 ${packages.count { it.failed }}")
                .forEach { (id, title) -> TextTab(title, { select(id) }, group == id, Modifier.focusTarget("subtitles:group:$id"), compact = true) }
        }
        if (context != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Caption(context, Modifier.weight(1f), maxLines = 2)
            ActionButton("全部作品", allWorks, quiet = true, maxLines = 1)
        }
    }
}

@Composable
internal fun SubtitleSearchControls(field: @Composable (Modifier) -> Unit, submit: @Composable () -> Unit,
    source: String, selectSource: (String) -> Unit, language: String, season: String?,
    chooseLanguage: () -> Unit, chooseSeason: () -> Unit, filters: () -> Unit) {
    val tv = LocalDeviceLayout.current.television
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().testTag("subtitle:search-input"), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { field(Modifier.fillMaxWidth()) }
            submit()
        }
        Row(Modifier.fillMaxWidth().testTag("subtitle:search-sources"), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextTab("ASSRT", { selectSource("assrt") }, source == "assrt", Modifier.focusTarget("subtitles:source:assrt"), compact = true)
            TextTab("OpenSubtitles", { selectSource("os") }, source == "os", Modifier.focusTarget("subtitles:source:os"), compact = true)
            Spacer(Modifier.weight(1f))
            if (tv) {
                ActionButton(language, chooseLanguage, Modifier.focusTarget("subtitles:language"), quiet = true, maxLines = 1)
                if (season != null) ActionButton(season, chooseSeason, Modifier.widthIn(max = 160.dp).focusTarget("subtitles:search-season"), quiet = true, maxLines = 1)
            } else IconButton("筛选", Glyph.Settings, filters, Modifier.focusTarget("subtitles:filters"))
        }
    }
}

@Composable
internal fun SubtitleOrganizerPage(editor: SubtitleOrganizerState, chooseWork: () -> Unit, chooseSeason: () -> Unit,
    chooseVersion: () -> Unit, viewFiles: () -> Unit, content: @Composable () -> Unit) {
    val tv = LocalDeviceLayout.current.television
    var expanded by rememberSaveable { mutableStateOf(false) }
    val pack = editor.pack
    val summary: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (tv) {
                Caption("对应作品")
                SubtitleTitle(pack?.workName?.ifBlank { "尚未选择作品" } ?: "整理字幕", maxLines = 2)
            } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SubtitleTitle(pack?.workName?.ifBlank { "尚未选择作品" } ?: "整理字幕", Modifier.weight(1f), maxLines = 2)
                IconButton(if (expanded) "收起作品与包信息" else "展开作品与包信息", Glyph.Down, { expanded = !expanded })
            }
            if (tv || expanded || pack?.workId == null) {
                ActionButton(if (pack?.workId == null) "选择作品" else "更换作品", chooseWork,
                    Modifier.fillMaxWidth().focusTarget("subtitles:work"), enabled = !editor.loading && !editor.saving,
                    primary = pack?.workId == null, quiet = pack?.workId != null, maxLines = 1)
                if (editor.seasons.isNotEmpty()) ActionButton(pack?.seasonName ?: "选择季", chooseSeason,
                    Modifier.fillMaxWidth().focusTarget("subtitles:organize-season"), quiet = true, enabled = !editor.loading && !editor.saving, maxLines = 1)
                if (pack?.workId != null) {
                    val versions = pack.files.mapNotNull { it.mapping?.versionName }.distinct()
                    Caption("媒体版本：${versions.singleOrNull() ?: "按集选择"}", maxLines = 2)
                    ActionButton("统一媒体版本", chooseVersion, Modifier.fillMaxWidth().focusTarget("subtitles:version-family"),
                        quiet = true, enabled = !editor.loading && !editor.saving, maxLines = 1)
                }
                SubtitleDivider()
                SubtitleFileName(pack?.title.orEmpty())
                Caption("${pack?.matched ?: 0} / ${pack?.files?.size ?: 0} 份已关联", maxLines = 1)
                ActionButton("查看包内文件", viewFiles, Modifier.fillMaxWidth().focusTarget("subtitles:package-info"), quiet = true, maxLines = 1)
            } else {
                val versions = pack.files.mapNotNull { it.mapping?.versionName }.distinct()
                Caption(listOfNotNull(pack.seasonName.ifBlank { null }, "媒体版本：${versions.singleOrNull() ?: "按集选择"}")
                    .joinToString(" · "), maxLines = 2)
                SubtitleFileName(pack.title, maxLines = 1)
            }
            editor.error?.let { SubtitleFullText(it, Palette.Error) }
            editor.notice?.let { Caption(it) }
        }
    }
    if (tv) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(Modifier.weight(.28f).fillMaxHeight().background(Palette.Surface, RoundedCornerShape(12.dp))
            .verticalScroll(rememberScrollState()).padding(16.dp)) { summary() }
        Column(Modifier.weight(.72f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SubtitleTitle("单集对应")
            Box(Modifier.weight(1f)) { content() }
        }
    } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().heightIn(max = if (pack?.workId == null) 220.dp else 260.dp)
            .verticalScroll(rememberScrollState()).background(Palette.Surface, RoundedCornerShape(12.dp)).padding(12.dp)) { summary() }
        Box(Modifier.weight(1f)) { content() }
    }
}
