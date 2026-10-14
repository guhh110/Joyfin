package dev.joyfin.ui.features.subtitles

import dev.joyfin.data.subtitles.SubtitleRequestRecord
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import dev.joyfin.ui.components.Sheet
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.RememberSelectionFocus

/** An overlay drawer leaves the search list's dimensions and scroll position intact. */
@Composable
internal fun SubtitleRequestHistorySheet(source: String, records: List<SubtitleRequestRecord>, onDismiss: () -> Unit) {
    val mobileWidth = (LocalConfiguration.current.screenWidthDp.dp * .9f).coerceAtMost(420.dp)
    RememberSelectionFocus("sheet:close") {
        Sheet(
            "$source 请求历史",
            onDismiss,
            side = true,
            modifier = if (LocalDeviceLayout.current.television) Modifier else Modifier.width(mobileWidth),
        ) {
            SubtitleRequestHistoryContent(records)
        }
    }
}
