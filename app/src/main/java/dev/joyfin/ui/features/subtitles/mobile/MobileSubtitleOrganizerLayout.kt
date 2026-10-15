package dev.joyfin.ui.features.subtitles.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun MobileSubtitleEpisodeLayout(number: @Composable () -> Unit, details: @Composable () -> Unit, status: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(horizontal = 4.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        number()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) { details() }
        status()
    }
}

@Composable
internal fun MobileSubtitleOrganizerActions(status: @Composable () -> Unit, save: @Composable (Modifier) -> Unit, upload: @Composable (Modifier) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        status()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            save(Modifier.weight(1f))
            upload(Modifier.weight(1f))
        }
    }
}

@Composable
internal fun MobileSubtitleUploadActions(summary: @Composable () -> Unit, action: @Composable (Modifier) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        summary()
        action(Modifier.fillMaxWidth())
    }
}
