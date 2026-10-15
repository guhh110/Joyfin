package dev.joyfin.ui.features.entry.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.ui.components.AppIcon
import dev.joyfin.ui.components.Glyph
import dev.joyfin.ui.components.browseErrorText
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.theme.Palette

@Composable
internal fun EntryError(message: String) {
    Text(
        browseErrorText(message),
        color = Palette.Text,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** A fixed status row never introduces a separate cancellation focus target. */
@Composable
internal fun EntryRequestFeedback(busy: Boolean, error: String?, modifier: Modifier = Modifier) {
    val tv = LocalDeviceLayout.current.television
    val message = if (!busy) error?.let(::browseErrorText) else null
    if (message != null) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(Palette.Surface, RoundedCornerShape(8.dp))
                .border(1.dp, Palette.BorderSubtle, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppIcon(Glyph.Info, Modifier.size(16.dp), color = Palette.Muted)
            Text(
                message,
                color = Palette.Text,
                fontSize = if (tv) 13.sp else 12.sp,
                lineHeight = if (tv) 18.sp else 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        Spacer(modifier.fillMaxWidth().height(if (tv) 22.dp else 21.dp))
    }
}
