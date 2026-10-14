package dev.joyfin.ui.features.subtitles.tv

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun TvSubtitleManagementLayout(
    header: @Composable () -> Unit,
    controls: @Composable () -> Unit,
    content: @Composable () -> Unit,
    actions: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        header()
        controls()
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        actions()
    }
}

@Composable
internal fun TvSubtitleResultLayout(details: @Composable () -> Unit, action: @Composable () -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(Modifier.weight(1f)) { details() }
        action()
    }
}

@Composable
internal fun TvSubtitleSearchInput(field: @Composable (Modifier) -> Unit, action: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        field(Modifier.weight(1f))
        action()
    }
}
