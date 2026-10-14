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
    Column(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        header()
        controls()
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        actions()
    }
}
