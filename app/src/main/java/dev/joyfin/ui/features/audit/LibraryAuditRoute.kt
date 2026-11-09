package dev.joyfin.ui.features.audit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import dev.joyfin.audit.*
import dev.joyfin.data.Media
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.audit.mobile.MobileLibraryAuditLayout
import dev.joyfin.ui.features.audit.tv.TvLibraryAuditLayout
import dev.joyfin.ui.features.audit.components.*
import dev.joyfin.ui.interaction.LocalScreenFocus
import dev.joyfin.ui.interaction.RememberSelectionFocus
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.model.BrowseActions
import dev.joyfin.ui.theme.Palette
import kotlinx.coroutines.flow.first

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun LibraryAuditRoute(controller: LibraryAuditController, actions: BrowseActions, libraries: List<Media>) {
    val ui by controller.state.collectAsState()
    val tv = LocalDeviceLayout.current.television
    val focus = LocalScreenFocus.current
    val window = LocalWindowInfo.current
    var scopePicker by remember { mutableStateOf(false) }
    var warnings by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var returnTarget by remember { mutableStateOf<String?>(null) }
    val finding = ui.report.findings.firstOrNull { it.key == selected }
    LaunchedEffect(finding?.key, ui.rechecking) {
        if (finding == null && ui.rechecking == null) selected = null
    }
    val dialogOpen = scopePicker || warnings || finding != null
    val visible = ui.visible
    val groups = visible.groupBy { it.target.seriesId?.takeIf(String::isNotBlank) ?: it.target.id }
        .mapValues { (_, findings) -> findings.sortedWith(compareBy<AuditFinding>(
            { if (it.target.type == "Episode") 1 else 0 },
            { it.target.season ?: -1 }, { it.target.episode ?: -1 },
            { it.target.id }, { it.category.ordinal }, { it.code },
        )) }
    val displayed = groups.values.flatten()
    LaunchedEffect(dialogOpen) {
        if (!dialogOpen && returnTarget != null && tv) {
            snapshotFlow { window.isWindowFocused }.first { it }
            withFrameNanos {}
            val wanted = returnTarget!!
            val target = if (wanted.startsWith("audit:row:") && visible.none { "audit:row:${it.key}" == wanted })
                displayed.getOrNull(selectedIndex.coerceAtMost((displayed.size - 1).coerceAtLeast(0)))
                    ?.let { "audit:row:${it.key}" } ?: "audit:start"
            else wanted
            focus?.rememberTarget?.invoke(target)
            focus?.requesters?.get(target)?.requestFocus()
            returnTarget = null
        }
    }
    val counts = ui.report.findings.filter { ui.showIgnored || it.fingerprint !in ui.ignored }.groupingBy { it.category }.eachCount()
    val header: @Composable () -> Unit = {
        PageHeader("片库体检", onBack = actions.back, backFocusKey = "audit:back")
    }
    val controls: @Composable () -> Unit = {
        AuditControls(ui, counts,
            onScope = { returnTarget = "audit:scope"; scopePicker = true },
            onScan = { if (ui.running) controller.cancel() else controller.start() },
            onFilter = controller::filter, onIgnored = { controller.showIgnored(!ui.showIgnored) },
            onWarnings = { returnTarget = "audit:warnings"; warnings = true })
    }
    val listStates = rememberSaveableStateHolder()
    val listKey = "${ui.report.scope.id}:${ui.category}:${ui.showIgnored}"
    val content: @Composable () -> Unit = {
      listStates.SaveableStateProvider(listKey) {
        val listState = rememberLazyListState()
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    ui.report.status == AuditStatus.READY -> EmptyContent("尚未扫描")
                    ui.report.status == AuditStatus.PARTIAL && ui.report.checked == 0 && ui.report.warnings.isNotEmpty() ->
                        ErrorContent("暂时无法完成片库体检", ui.report.warnings.first(), action = "重新扫描", onAction = controller::start)
                    ui.report.status in setOf(AuditStatus.PARTIAL, AuditStatus.CANCELED) -> EmptyContent("本次检查尚未完成", "当前范围暂无可显示的结果，不能据此判断片库完整。可以重新扫描。")
                    ui.report.findings.isNotEmpty() -> EmptyContent("当前分类暂无提示")
                    ui.report.checked == 0 -> EmptyContent("没有可检查的电影或剧集", "当前范围没有返回可见作品，可更换媒体库后再试。")
                    else -> EmptyContent("未发现符合当前规则的提示", "已完成本次可见范围检查；此体检不包含文件内容校验。")
                }
            }
            groups.forEach { (id, findings) ->
                stickyHeader(key = "work:$id") {
                    Box(Modifier.fillMaxWidth().background(Palette.Background)) {
                        AuditWorkHeader(findings.first().target.workName, findings.size)
                    }
                }
                items(findings, key = { it.key }) { row ->
                    AuditFindingRow(row, row.fingerprint in ui.ignored) {
                        returnTarget = "audit:row:${row.key}"
                        selectedIndex = displayed.indexOfFirst { it.key == row.key }.coerceAtLeast(0)
                        selected = row.key
                    }
                }
            }
        }
      }
    }
    if (tv) TvLibraryAuditLayout(header, controls, content) else MobileLibraryAuditLayout(header, controls, content)
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
        Sheet("检查依据", { selected = null }, side = true, scrollContent = false, preferredWidth = 440.dp) {
            AuditFindingDetails(finding, ui,
                onOpen = { selected = null; actions.openAuditItem(finding) },
                onSubtitles = { selected = null; actions.auditSubtitles(finding) },
                onRecheck = { controller.recheck(finding) },
                onIgnore = { controller.ignore(finding); selected = null })
        }
    }
}
