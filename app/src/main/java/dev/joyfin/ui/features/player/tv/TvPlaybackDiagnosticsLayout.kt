package dev.joyfin.ui.features.player.tv

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun TvPlaybackDiagnosticsLayout(source: @Composable () -> Unit, output: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Box(Modifier.weight(1f)) { source() }
        Box(Modifier.weight(1f)) { output() }
    }
    Spacer(Modifier.height(4.dp))
}
