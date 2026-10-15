package dev.joyfin.ui.features.subtitles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import dev.joyfin.data.subtitles.ManagedSubtitle
import dev.joyfin.data.subtitles.ManagedSubtitleFile
import dev.joyfin.subtitles.SubtitleCandidatePackages
import dev.joyfin.ui.components.*
import dev.joyfin.ui.features.entry.components.EntryTextField
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.theme.Palette
import dev.joyfin.ui.theme.MotionTokens

@Composable
internal fun ColumnScope.SubtitleCandidatePackagesContent(
    state: SubtitleCandidatePackages,
    packages: List<ManagedSubtitle>,
    selected: String,
    query: TextFieldState,
    scroll: LazyListState,
    reload: () -> Unit,
    select: (String) -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    EntryTextField("筛选下载包", query, "作品名、季名或字幕包名",
        Modifier.focusTarget("candidate:package-query"), onSubmit = { keyboard?.hide() })
    ActionButton(if (state.error != null) "重试读取" else if (state.loaded) "重新读取" else "读取下载包",
        reload, Modifier.focusTarget("candidate:packages-reload"), loading = state.loading, quiet = true)
    state.error?.let { Caption(it, color = Palette.Error) }
    LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth(), state = scroll, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (state.loading && !state.loaded) items(3, key = { "skeleton:$it" }) {
            SkeletonBlock(Modifier.fillMaxWidth().height(100.dp))
        }
        if (state.loaded && packages.isEmpty()) item("empty") {
            Caption(if (query.text.isBlank()) "没有其他下载包" else "没有符合筛选条件的下载包")
        }
        items(packages, key = { it.id }) { pack ->
            FocusSurface({ select(pack.id) }, Modifier.fillMaxWidth().focusTarget("candidate:package:${pack.id}"),
                selected = pack.id == selected, transparent = true, enabled = !state.loading, focusedScale = MotionTokens.FixedScale) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SubtitleTitle(listOf(pack.workName, pack.seasonName).filter(String::isNotBlank).joinToString(" · ")
                        .ifBlank { "未指定作品与季" })
                    SubtitleFileName(pack.title)
                    Caption("${pack.origin} · ${pack.files.size} 份字幕")
                }
            }
            SubtitleDivider()
        }
    }
}

@Composable
internal fun SubtitleCandidateFileContent(file: ManagedSubtitleFile, current: ManagedSubtitleFile?, selected: Boolean) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val shape = RoundedCornerShape(4.dp)
        Box(Modifier.padding(top = 3.dp).size(22.dp)
            .border(1.dp, if (selected) Palette.Text else Palette.Muted, shape)
            .background(if (selected) Palette.Text else androidx.compose.ui.graphics.Color.Transparent, shape),
            contentAlignment = Alignment.Center) {
            if (selected) AppIcon(Glyph.Check, Modifier.size(16.dp), color = Palette.Background)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            SubtitleFileName(file.name, emphasizeDetails = true, color = Palette.Text)
            if (!selected && current?.mapping != null)
                Caption("选中后移自 ${current.mapping.itemName} · ${current.mapping.versionName}")
            val issue = current?.uploadError ?: when (current?.upload) {
                "failed" -> "回传失败，可通过回传操作重试"
                "uncertain", "sending" -> "服务器回传结果待确认"
                "identifying" -> "已回传，等待识别服务器字幕"
                else -> null
            }
            issue?.let { Caption(it, color = Palette.Error) }
        }
    }
}
