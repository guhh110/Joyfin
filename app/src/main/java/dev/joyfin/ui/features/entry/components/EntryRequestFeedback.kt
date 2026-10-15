package dev.joyfin.ui.features.entry.components

import dev.joyfin.ui.theme.Palette
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.joyfin.ui.device.LocalDeviceLayout

@Composable
internal fun EntryError(message: String) {
    androidx.tv.material3.Text(
        message,
        color = Palette.Error,
        fontSize = 12.sp,
        lineHeight = 20.sp,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** A fixed status row never introduces a separate cancellation focus target. */
@Composable
internal fun EntryRequestFeedback(busy: Boolean, error: String?, modifier: Modifier = Modifier) {
    val tv = LocalDeviceLayout.current.television
    val message = if (!busy) error ?: " " else " "
    Box(modifier.fillMaxWidth().height(if (tv) 22.dp else 21.dp)) {
        androidx.tv.material3.Text(
            message,
            color = if (!busy && error != null) Palette.Error else Palette.Muted,
            fontSize = if (tv) 13.sp else 12.sp,
            lineHeight = if (tv) 22.sp else 21.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}
