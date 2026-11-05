package dev.joyfin.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.joyfin.ui.interaction.actionVisualFeedback
import dev.joyfin.ui.theme.MotionTokens

/** Share button focus styling on the poster; keep its text and measured bounds stationary. */
@Composable
internal fun Modifier.posterFocus(focused: Boolean): Modifier =
    actionVisualFeedback(
        focused = focused,
        transparent = true,
        focusedScale = MotionTokens.CardFocusedScale,
        focusedBackground = false,
        borderOnly = true,
        shape = RoundedCornerShape(MediaCardLayout.Corner),
    )
