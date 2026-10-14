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
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        header()
        controls()
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        actions()
    }
}

@Composable
internal fun MobileSubtitleResultLayout(title: @Composable () -> Unit, release: @Composable () -> Unit, metadata: @Composable () -> Unit, action: @Composable () -> Unit) {
    val device = dev.joyfin.ui.device.LocalDeviceLayout.current
    val narrow = !device.television && device.widthDp < 380
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        title()
        release()
        if (narrow) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                metadata()
                Box(Modifier.fillMaxWidth()) { action() }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { metadata() }
                action()
            }
        }
    }
}
