package dev.joyfin.ui.features.player.subtitles

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
internal fun SubtitleEncodingContent(controller: LocalSubtitleDownload, retryLabel: String = "重新解码并继续", showAction: Boolean = true) {
    val ui by controller.state.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ui.error?.let { SubtitleFullText(it, Palette.Error) }
        Column {
            listOf("自动", "UTF-8", "GB18030", "Big5", "UTF-16LE", "UTF-16BE").forEach { encoding ->
                ChoiceRow(encoding, Modifier.focusTarget("encoding:$encoding"), selected = ui.encoding == encoding, enabled = !ui.busy) { controller.encoding(encoding) }
            }
        }
        if (showAction) ActionButton(retryLabel, controller::retryEncoding, primary = true, loading = ui.busy, enabled = !ui.busy)
    }
}
