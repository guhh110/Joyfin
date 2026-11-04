package dev.joyfin.ui.features.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import dev.joyfin.playback.PlayerActions
import dev.joyfin.playback.PlayerUiState
import dev.joyfin.ui.components.*

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
    Box {
        Box(
            Modifier.fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = .82f),
                        .28f to Color.Black.copy(alpha = .08f),
                        .52f to Color.Transparent,
                        1f to Color.Black.copy(alpha = .94f),
                    )
                )
                .safeDrawingPadding()
                .padding(horizontal = horizontalPadding, vertical = 14.dp)
        ) {
            PlayerHeader(ui, actions, onPanel, Modifier.align(Alignment.TopStart))
            if (showTransport)
                Box(Modifier.align(Alignment.Center)) {
                    TransportControls(ui, actions, onInteraction)
                }
            extra()
            PlayerBottomBar(ui, actions, onPanel, Modifier.align(Alignment.BottomCenter), onInteraction)
        }
    }
}
