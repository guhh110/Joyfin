package dev.joyfin.ui.features.subtitles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.joyfin.data.Media
import dev.joyfin.data.subtitles.ManagedSubtitle
import dev.joyfin.subtitles.SubtitleCandidateScope
import dev.joyfin.subtitles.SubtitleOrganizer
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.*
import dev.joyfin.ui.theme.Palette

@Composable
internal fun SubtitleEpisodeFilesSheet(controller: SubtitleOrganizer, media: Media, version: String, dismiss: () -> Unit) {
    val state by controller.state.collectAsState()
    key(state.pack?.id, media.id, version) {
        EpisodeCandidateSheet(controller, media, version, dismiss)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EpisodeCandidateSheet(controller: SubtitleOrganizer, media: Media, version: String, dismiss: () -> Unit) {
    val ui by controller.state.collectAsState()
    val pack = ui.pack ?: return
    var sourceId by remember { mutableStateOf(pack.id) }
    var showAll by remember { mutableStateOf(false) }
    var choosingPackage by remember { mutableStateOf(false) }
    var focusEvent by remember { mutableIntStateOf(0) }
    var requestedFocus by remember { mutableStateOf<String?>(null) }
    val queryText = rememberTextFieldState()
    val keyboard = LocalSoftwareKeyboardController.current
    val tv = LocalDeviceLayout.current.television
    val source = if (sourceId == pack.id) pack else ui.candidatePackages.packages.firstOrNull { it.id == sourceId } ?: pack
    val sourceNames = source.files.map { it.fileId to it.name }
    val scope = remember(source.id, sourceNames, ui.media) { SubtitleCandidateScope(source, ui.media) }
    // Freeze membership/order for a view. Checkbox changes update rows, never the visible list.
    val candidates = remember(scope, media.id, version, showAll) {
        scope.candidates(pack, source, media, version, showAll)
    }
    val sourceFiles = source.files.map { it.fileId }.toSet()
    val currentFiles = pack.files.distinctBy { it.fileId }.associateBy { it.fileId }
    val associated = pack.files.filter { it.mapping?.itemId == media.id && it.mapping?.versionId == version }.distinctBy { it.fileId }
    val firstFile = associated.firstOrNull { row -> candidates.any { it.fileId == row.fileId } }?.fileId
        ?: candidates.firstOrNull()?.fileId
    val firstFileKey = firstFile?.let { "episode-file:$it" } ?: "candidate:source"
    val initialFileIndex = candidates.indexOfFirst { it.fileId == firstFile }.coerceAtLeast(0)
    val filesScroll = key(source.id, showAll) { rememberLazyListState(initialFirstVisibleItemIndex = if (tv && candidates.isNotEmpty()) initialFileIndex + 1 else 0) }
    val packagesScroll = rememberLazyListState()
    val sortedPackages = remember(ui.candidatePackages.packages, pack.workId, pack.seasonId) {
        ui.candidatePackages.packages.sortedWith(compareByDescending<ManagedSubtitle> {
            pack.workId != null && it.workId == pack.workId && it.seasonId == pack.seasonId
        }.thenByDescending { it.created }.thenBy { it.id })
    }
    val query = queryText.text.toString().trim()
    val packages = remember(sortedPackages, query) {
        sortedPackages.filter { candidate ->
            query.isBlank() || listOf(candidate.workName, candidate.seasonName, candidate.title)
                .any { it.contains(query, ignoreCase = true) }
        }
    }
    fun returnToFiles(id: String = sourceId, focusSource: Boolean = true) {
        sourceId = id
        choosingPackage = false
        keyboard?.hide()
        requestedFocus = if (focusSource) "candidate:source" else null
        focusEvent++
    }
    fun dismissView() { if (choosingPackage) returnToFiles() else dismiss() }
    RememberScreenFocus(firstFileKey) {
        val memory = LocalScreenFocus.current
        LaunchedEffect(focusEvent) {
            if (!tv) return@LaunchedEffect
            val target = requestedFocus ?: firstFileKey
            memory?.rememberTarget?.invoke(target)
            if (choosingPackage) {
                val index = packages.indexOfFirst { "candidate:package:${it.id}" == target }
                if (index >= 0) packagesScroll.scrollToItem(index)
            }
            withFrameNanos {}
            memory?.requesters?.get(target)?.requestFocus()
        }
        // Only the initial package read may replace the waiting target; typing never steals focus.
        LaunchedEffect(ui.candidatePackages.loaded, ui.candidatePackages.loading, choosingPackage) {
            if (tv && choosingPackage && ui.candidatePackages.loaded && !ui.candidatePackages.loading &&
                memory?.target?.invoke() == "candidate:packages-reload") {
                val focus = memory ?: return@LaunchedEffect
                val target = packages.firstOrNull { it.id == sourceId } ?: packages.firstOrNull()
                if (target != null) {
                    val index = packages.indexOfFirst { it.id == target.id }
                    packagesScroll.scrollToItem(index)
                    withFrameNanos {}
                    if (focus.target() == "candidate:packages-reload") {
                        focus.rememberTarget("candidate:package:${target.id}")
                        focus.requesters["candidate:package:${target.id}"]?.requestFocus()
                    }
                }
            }
        }
        Sheet(if (choosingPackage) "选择字幕来源包" else media.label, ::dismissView, scrollContent = false, preferredWidth = if (tv) 680.dp else null, actions = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Caption("已选 ${associated.size} 份", color = Palette.Text)
                }
                ActionButton(if (choosingPackage) "返回字幕选择" else "完成", ::dismissView,
                    Modifier.focusTarget("candidate:done"), primary = true)
            }
        }) {
            if (choosingPackage) {
                SubtitleCandidatePackagesContent(ui.candidatePackages, packages, sourceId, queryText, packagesScroll,
                    reload = { controller.loadCandidatePackages(reload = true) },
                    select = { showAll = false; returnToFiles(it, focusSource = false) })
            } else {
                Caption(media.sources.firstOrNull { it.id == version }?.name.orEmpty(), maxLines = 2)
                Caption("${if (source.id == pack.id) "当前包" else "来源包"} · ${source.title}",
                    maxLines = 1)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ActionButton("从其他包添加", {
                        queryText.edit { replace(0, length, "") }
                        controller.loadCandidatePackages()
                        choosingPackage = true
                        val target = sortedPackages.firstOrNull { it.id == sourceId } ?: sortedPackages.firstOrNull()
                        requestedFocus = target?.let { "candidate:package:${it.id}" } ?: "candidate:packages-reload"
                        focusEvent++
                    }, Modifier.focusTarget("candidate:source"), Glyph.Plus, quiet = true)
                    if (source.id != pack.id) ActionButton("返回当前包", { showAll = false; returnToFiles(pack.id) }, quiet = true)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextTab("相关文件", { showAll = false }, !showAll, Modifier.focusTarget("candidate:related"))
                    TextTab("包内全部 (${sourceFiles.size})", { showAll = true }, showAll, Modifier.focusTarget("candidate:all"))
                }
                SubtitleDivider()
                LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth(), state = filesScroll,
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // The complete source name scrolls with the list, so a long name cannot
                    // consume the file viewport or push the fixed footer off screen.
                    item("source:name") { SubtitleFileName(source.title) }
                    if (candidates.isEmpty()) item("empty") { Caption("没有相关文件，可查看包内全部或从其他包添加") }
                    items(candidates, key = { it.fileId }) { candidate ->
                        val row = currentFiles[candidate.fileId]
                        val selected = row?.mapping?.itemId == media.id && row?.mapping?.versionId == version
                        FocusSurface({ controller.toggleCandidate(
                            if (candidate.fileId in sourceFiles) source.id else pack.id,
                            candidate.fileId, media.id, version,
                        ) }, Modifier.fillMaxWidth().focusTarget("episode-file:${candidate.fileId}").semantics {
                            role = Role.Checkbox
                            stateDescription = if (selected) "已勾选" else "未勾选"
                        }, selected = selected, transparent = true, enabled = !ui.loading && !ui.saving) {
                            SubtitleCandidateFileContent(row ?: candidate, row, selected)
                        }
                        SubtitleDivider()
                    }
                }
            }
        }
    }
}
