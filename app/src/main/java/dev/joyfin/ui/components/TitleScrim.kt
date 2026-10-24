package dev.joyfin.ui.components

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** A soft local scrim behind detail titles so bright logo artwork stays readable. */
fun Modifier.titleScrim(): Modifier = background(
    Brush.horizontalGradient(
        0f to Color.Transparent,
        0.08f to Color.Black.copy(alpha = 0.55f),
        0.72f to Color.Black.copy(alpha = 0.32f),
        1f to Color.Transparent,
    )
)
