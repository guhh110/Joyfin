package dev.joyfin.ui.features.player.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.joyfin.ui.components.LoadingSpinner
import dev.joyfin.ui.device.LocalDeviceLayout

@Composable
fun PlayerLoadingIndicator(modifier: Modifier = Modifier) {
    LoadingSpinner(
        modifier.testTag("player:loading"),
        if (LocalDeviceLayout.current.television) 32.dp else 28.dp,
        LocalTransportTint.current.copy(alpha = 1f),
    )
}
