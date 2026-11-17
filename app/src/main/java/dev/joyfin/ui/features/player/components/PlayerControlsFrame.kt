package dev.joyfin.ui.features.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.joyfin.playback.PlayerActions
import dev.joyfin.playback.PlayerUiState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.theme.Palette

@Composable
internal fun PlayerControlsFrame(
    ui: PlayerUiState,
    actions: PlayerActions,
    onPanel: (PlayerPanel) -> Unit,
    onInteraction: () -> Unit,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    showTransport: Boolean,
    extra: @Composable BoxScope.() -> Unit = {},
) {
    val television = LocalDeviceLayout.current.television
    Box {
        Box(
            Modifier.fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = .62f),
                        .24f to Color.Transparent,
                        .58f to Color.Transparent,
                        .78f to Color.Black.copy(alpha = .22f),
                        1f to Color.Black.copy(alpha = .78f),
                    )
                )
                .safeDrawingPadding()
                .padding(horizontal = horizontalPadding, vertical = 14.dp)
        ) {
            PlayerHeader(ui, actions, onPanel, Modifier.align(Alignment.TopStart))
            if (showTransport && !television)
                Box(Modifier.align(Alignment.Center)) {
                    TransportControls(ui, actions, onInteraction)
                }
            extra()
            if (television) {
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().testTag("player:controls")
                    .background(Palette.Background.copy(alpha = .78f), RoundedCornerShape(12.dp))
                    .border(.5.dp, Palette.Line, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    if (showTransport) Box(Modifier.fillMaxWidth().height(112.dp), contentAlignment = Alignment.TopCenter) {
                        TransportControls(ui, actions, onInteraction)
                    }
                    PlayerBottomBar(ui, actions, onPanel, onInteraction = onInteraction)
                }
            } else PlayerBottomBar(ui, actions, onPanel, Modifier.align(Alignment.BottomCenter), onInteraction)
        }
    }
}
