package dev.joyfin.ui.features.subtitles.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun MobileSubtitleManagementLayout(
    header: @Composable () -> Unit,
    controls: @Composable () -> Unit,
    content: @Composable () -> Unit,
    actions: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        header()
        controls()
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        actions()
    }
}
