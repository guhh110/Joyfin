package dev.joyfin.ui.features.subtitles

import dev.joyfin.data.subtitles.SubtitleRequestRecord
import androidx.compose.runtime.Composable
import dev.joyfin.ui.components.Sheet
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.RememberSelectionFocus

/** An overlay drawer leaves the search list's dimensions and scroll position intact. */
@Composable
internal fun SubtitleRequestHistorySheet(source: String, records: List<SubtitleRequestRecord>, onDismiss: () -> Unit) {
    RememberSelectionFocus("sheet:close") {
        Sheet(
            "$source 请求历史",
            onDismiss,
            side = LocalDeviceLayout.current.television,
        ) {
            SubtitleRequestHistoryContent(records)
        }
    }
}
