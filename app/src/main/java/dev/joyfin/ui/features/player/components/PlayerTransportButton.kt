package dev.joyfin.ui.features.player.components

import dev.joyfin.ui.theme.Palette
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.joyfin.ui.components.stableAction
import dev.joyfin.ui.theme.MotionTokens
import dev.joyfin.ui.theme.rememberMotionSpec

/** Fixed geometry: remote focus only changes color, never scale or measured size. */
@Composable
internal fun PlayerTransportButton(
    label: String,
    size: Dp,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val pressed by interactionSource.collectIsPressedAsState()

    val colorSpec = rememberMotionSpec<Color>(MotionTokens.FocusDurationMs)
    val pressColorSpec = rememberMotionSpec<Color>(MotionTokens.PressDurationMs)
    val dpSpec = rememberMotionSpec<Dp>(MotionTokens.FocusDurationMs)

    val highlighted = focused && enabled
    val lightSurface = primary || highlighted
    val baseTint = if (lightSurface) Palette.Surface else Palette.Text
    val targetTint = if (pressed && enabled) baseTint.copy(alpha = 0.82f) else baseTint

    val baseSurface =
        when {
            highlighted -> Palette.Text
            primary -> Palette.Text.copy(alpha = if (enabled) .98f else .88f)
            else -> Palette.Surface.copy(alpha = .94f)
        }
    val targetSurface =
        if (pressed && enabled) {
            if (lightSurface) baseSurface.copy(alpha = 0.85f) else Palette.Pressed
        } else {
            baseSurface
        }

    val targetOutline =
        when {
            focused -> Palette.BorderFocus
            primary -> Palette.Text.copy(alpha = .85f)
            else -> Palette.Text.copy(alpha = if (enabled) .65f else .4f)
        }

    val targetBorderWidth = if (focused) 2.dp else 1.dp

    val animatedTint by
        animateColorAsState(targetValue = targetTint, animationSpec = if (pressed) pressColorSpec else colorSpec, label = "tint")
    val animatedSurface by
        animateColorAsState(targetValue = targetSurface, animationSpec = if (pressed) pressColorSpec else colorSpec, label = "surface")
    val animatedOutline by
        animateColorAsState(targetValue = targetOutline, animationSpec = colorSpec, label = "outline")
    val animatedBorderWidth by
        animateDpAsState(targetValue = targetBorderWidth, animationSpec = dpSpec, label = "borderWidth")

    Box(
        modifier
            .size(size)
            .onFocusChanged { focused = it.isFocused }
            .clip(CircleShape)
            .background(animatedSurface)
            .border(animatedBorderWidth, animatedOutline, CircleShape)
            .semantics { contentDescription = label }
            .stableAction(enabled, interactionSource, onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalTransportTint provides animatedTint.copy(alpha = if (enabled) 1f else .6f)
        ) {
            content()
        }
    }
}

internal val LocalTransportTint = staticCompositionLocalOf { Palette.Text }
