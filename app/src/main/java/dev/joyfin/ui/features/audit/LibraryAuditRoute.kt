package dev.joyfin.ui.features.audit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import dev.joyfin.audit.*
import dev.joyfin.data.Media
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.audit.mobile.MobileLibraryAuditLayout
import dev.joyfin.ui.features.audit.tv.TvLibraryAuditLayout
import dev.joyfin.ui.interaction.LocalScreenFocus
import dev.joyfin.ui.interaction.RememberSelectionFocus
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.model.BrowseActions
import dev.joyfin.ui.theme.Palette
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun LibraryAuditRoute(controller: LibraryAuditController, actions: BrowseActions, libraries: List<Media>) {
    val ui by controller.state.collectAsState()
    val tv = LocalDeviceLayout.current.television
    val focus = LocalScreenFocus.current
    val window = LocalWindowInfo.current
    var scopePicker by remember { mutableStateOf(false) }
    var warnings by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }
    var returnTarget by remember { mutableStateOf<String?>(null) }
    val finding = ui.report.findings.firstOrNull { it.key == selected }
    LaunchedEffect(finding?.key, ui.rechecking) {
        if (finding == null && ui.rechecking == null) selected = null
    }
    val dialogOpen = scopePicker || warnings || finding != null
    val visible = ui.visible
    LaunchedEffect(dialogOpen) {
        if (!dialogOpen && returnTarget != null && tv) {
            snapshotFlow { window.isWindowFocused }.first { it }
            withFrameNanos {}
            val wanted = returnTarget!!
            val target = if (wanted.startsWith("audit:row:") && visible.none { "audit:row:${it.key}" == wanted }) "audit:start" else wanted
            focus?.rememberTarget?.invoke(target)
            focus?.requesters?.get(target)?.requestFocus()
            returnTarget = null
        }
    }
    val counts = ui.report.findings.filter { ui.showIgnored || it.fingerprint !in ui.ignored }.groupingBy { it.category }.eachCount()
    val header: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton("返回", Glyph.Back, actions.back, Modifier.focusTarget("audit:back"))
            SectionTitle("片库体检")
        }
        Caption("检查电影、剧集和播放版本；结果基于当前账号可见的数据。")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(ui.report.scope.title, { returnTarget = "audit:scope"; scopePicker = true },
                Modifier.focusTarget("audit:scope"), Glyph.Library, enabled = !ui.loading && !ui.running && ui.rechecking == null)
            ActionButton(if (ui.running) "取消扫描" else if (ui.report.status == AuditStatus.READY) "开始扫描" else "重新扫描",
                { if (ui.running) controller.cancel() else controller.start() },
                Modifier.widthIn(min = 132.dp).focusTarget("audit:start"), Glyph.Refresh, primary = true,
                enabled = !ui.loading && ui.rechecking == null, loading = ui.running, enabledWhileLoading = true,
                loadingLabel = "正在扫描，可取消")
        }
        val progress = ui.progress ?: ui.report
        Caption(if (ui.running) "${ui.phase} · 已检查 ${progress.checked} 个条目 · ${progress.processedWorks}/${progress.totalWorks ?: "?"} 部作品"
            else "${ui.report.status.label} · 已检查 ${ui.report.checked} 个条目" +
                if (ui.report.finishedAt > 0) " · ${auditDate(ui.report.finishedAt)}" else "")
        if (ui.running && ui.report.startedAt != progress.startedAt && ui.report.findings.isNotEmpty()) Caption("正在重新扫描，当前保留上次结果。")
        if (ui.report.futureEpisodes > 0) Caption("另有 ${ui.report.futureEpisodes} 集尚未播出，未计为缺集。")
        if (ui.report.warnings.isNotEmpty()) ActionButton("${ui.report.warnings.size} 项读取未完成", {
            returnTarget = "audit:warnings"; warnings = true
        }, Modifier.focusTarget("audit:warnings"), Glyph.Info)
        ui.cacheError?.let { Caption(it, color = Palette.Muted) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TextTab("全部 ${counts.values.sum()}", { controller.filter(null) }, ui.category == null,
                Modifier.focusTarget("audit:filter:all"), compact = true)
            AuditCategory.entries.forEach { category ->
                TextTab("${category.label} ${counts[category] ?: 0}", { controller.filter(category) }, ui.category == category,
                    Modifier.focusTarget("audit:filter:${category.name}"), compact = true)
            }
            TextTab(if (ui.showIgnored) "含已忽略" else "显示已忽略", { controller.showIgnored(!ui.showIgnored) }, ui.showIgnored,
                Modifier.focusTarget("audit:ignored"), compact = true)
        }
    }
    val listStates = rememberSaveableStateHolder()
    val listKey = "${ui.report.scope.id}:${ui.category}:${ui.showIgnored}"
    val content: @Composable () -> Unit = {
      listStates.SaveableStateProvider(listKey) {
        val listState = rememberLazyListState()
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
            if (ui.error != null) item("error") {
                RegionLoadFeedback(false, ui.error, null, visible.isNotEmpty(), reserveSpace = false)
            }
            if (ui.loading) item("cache") {
                val show = rememberSkeletonVisible(true, ui.report.scope)
                Box(Modifier.fillMaxWidth().height(180.dp)) {
                    if (show) Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { repeat(3) { SkeletonText(lines = 2) } }
                }
            }
            else if (visible.isEmpty()) item("empty") {
                when {
                    ui.running -> Caption("正在检查，已确认的结果会逐步出现在这里。", Modifier.padding(vertical = 28.dp))
                    ui.report.status == AuditStatus.READY -> EmptyContent("开始一次片库体检", "选择扫描范围，再点击开始扫描。只读取服务器信息，结果和忽略记录保存在本机。")
                    ui.report.status == AuditStatus.PARTIAL && ui.report.checked == 0 && ui.report.warnings.isNotEmpty() ->
                        ErrorContent("暂时无法完成片库体检", ui.report.warnings.first(), action = "重新扫描", onAction = controller::start)
                    ui.report.status in setOf(AuditStatus.PARTIAL, AuditStatus.CANCELED) -> EmptyContent("本次检查尚未完成", "当前范围暂无可显示的结果，不能据此判断片库完整。可以重新扫描。")
                    ui.report.findings.isNotEmpty() -> EmptyContent("当前筛选下没有结果", "可切换分类或显示已忽略的提示。")
                    ui.report.checked == 0 -> EmptyContent("没有可检查的电影或剧集", "当前范围没有返回可见作品，可更换媒体库后再试。")
                    else -> EmptyContent("未发现符合当前规则的提示", "已完成本次可见范围检查；此体检不包含文件内容校验。")
                }
            }
            items(visible, key = { it.key }) { row ->
                ChoiceRow(row.target.name, Modifier.focusTarget("audit:row:${row.key}"),
                    subtitle = "${row.category.label} · ${row.confidence.label}" +
                        if (row.fingerprint in ui.ignored) " · 已忽略\n${row.title}" else "\n${row.title}") {
                    returnTarget = "audit:row:${row.key}"
                    selected = row.key
                }
                Box(Modifier.fillMaxWidth().height(.5.dp).background(Palette.Line))
            }
        }
      }
    }
    if (tv) TvLibraryAuditLayout(header, content) else MobileLibraryAuditLayout(header, content)
    if (scopePicker) RememberSelectionFocus("audit:scope:${ui.report.scope.id ?: "all"}") {
        Sheet("扫描范围", { scopePicker = false }, side = true) {
            (listOf(AuditScope(), ui.report.scope) + libraries.map { AuditScope(it.id, it.name) }).distinctBy { it.id }.forEach { target ->
                ChoiceRow(target.title, Modifier.focusTarget("audit:scope:${target.id ?: "all"}"), selected = target.id == ui.report.scope.id) {
                    controller.open(target)
                    selected = null
                    scopePicker = false
                }
            }
        }
    }
    if (warnings) Sheet("未完成的检查", { warnings = false }, side = true) {
        ui.report.warnings.forEach { Caption(it, Modifier.padding(vertical = 8.dp)) }
    }
    if (finding != null) RememberSelectionFocus("audit:detail:open") {
        Sheet("检查依据", { selected = null }, side = true) {
            SectionTitle(finding.target.name)
            Caption("${finding.category.label} · ${finding.confidence.label}")
            SectionTitle(finding.title)
            finding.evidence.filter { it.isNotBlank() }.forEach { Caption(it, Modifier.padding(vertical = 4.dp)) }
            ActionButton("查看作品", { selected = null; actions.openAuditItem(finding) },
                Modifier.focusTarget("audit:detail:open"), Glyph.Info, enabled = !ui.running && ui.rechecking == null)
            if (finding.category == AuditCategory.SUBTITLES) ActionButton("搜索与整理字幕", {
                selected = null; actions.auditSubtitles(finding)
            }, Modifier.focusTarget("audit:detail:subtitles"), Glyph.Subtitle, enabled = !ui.running && ui.rechecking == null)
            ActionButton("重新检查此项", { controller.recheck(finding) }, Modifier.focusTarget("audit:detail:recheck"), Glyph.Refresh,
                enabled = !ui.running && (ui.rechecking == null || ui.rechecking == finding.key), loading = ui.rechecking == finding.key)
            ui.error?.let { Caption(it) }
            ActionButton(if (finding.fingerprint in ui.ignored) "恢复此提示" else "忽略此提示", {
                controller.ignore(finding); selected = null
            }, Modifier.focusTarget("audit:detail:ignore"), Glyph.CheckCircle, enabled = !ui.running && ui.rechecking == null)
        }
    }
}

private fun auditDate(time: Long): String = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    .format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()))
