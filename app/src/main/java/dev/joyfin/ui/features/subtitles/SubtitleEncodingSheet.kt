package dev.joyfin.ui.features.subtitles

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.subtitles.LocalSubtitleDownload
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.*
import dev.joyfin.ui.theme.Palette

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SubtitleEncodingSheet(controller: LocalSubtitleDownload) {
    val ui by controller.state.collectAsState()
    RememberSelectionFocus("encoding:${ui.encoding}") {
        Sheet("保存下载文件", controller::cancel) {
            ui.error?.let { Caption(it, color = Palette.Error) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("自动", "UTF-8", "GB18030", "Big5", "UTF-16LE", "UTF-16BE").forEach { encoding ->
                    ActionButton(encoding, { controller.encoding(encoding) }, Modifier.focusTarget("encoding:$encoding"), selected = ui.encoding == encoding, enabled = !ui.busy)
                }
            }
            ActionButton("重新保存到待整理", controller::retryEncoding, primary = true, loading = ui.busy, enabled = !ui.busy)
        }
    }
}
