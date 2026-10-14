package dev.joyfin.ui.features.subtitles.tv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import dev.joyfin.ui.theme.Palette
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity

@Composable
internal fun TvSubtitleEpisodeLayout(number: @Composable () -> Unit, details: @Composable () -> Unit, status: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height((76 * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        number()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) { details() }
        status()
    }
}

@Composable
internal fun TvSubtitleOrganizerActions(status: @Composable () -> Unit, save: @Composable (Modifier) -> Unit, upload: @Composable (Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f)) { status() }
        save(Modifier)
        upload(Modifier)
    }
}

@Composable
internal fun TvSubtitleUploadActions(summary: @Composable () -> Unit, action: @Composable (Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f)) { summary() }
        action(Modifier)
    }
}

/** Read-only focus stops let a remote scroll through the complete upload manifest. */
@Composable
internal fun TvSubtitleUploadReviewGroup(modifier: Modifier, content: @Composable () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(modifier.onFocusChanged { focused = it.isFocused }
        .border(2.dp, if (focused) Palette.Text else Color.Transparent, RoundedCornerShape(8.dp))
        .focusable().padding(10.dp)) { content() }
}
