package dev.joyfin.ui.features.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

internal val PlayerWarmWhite = Color(0xFFF1EFE9)
internal val PlayerChampagne = Color(0xFFC9B98F)

/** Fixed geometry: remote focus only changes color, never scale or measured size. */
@Composable
internal fun PlayerTransportButton(
    label: String,
    size: Dp,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val highlighted = focused && enabled
    val lightSurface = primary || highlighted
    val tint = if (lightSurface) Color(0xFF17191D) else PlayerWarmWhite
    val surface =
        when {
            highlighted -> PlayerChampagne
            primary -> PlayerWarmWhite.copy(alpha = if (enabled) .98f else .88f)
            else -> Color(0xFF17191D).copy(alpha = .94f)
        }
    val outline =
        when {
            focused -> if (enabled) PlayerWarmWhite else PlayerChampagne
            primary -> PlayerWarmWhite.copy(alpha = .85f)
            else -> PlayerWarmWhite.copy(alpha = if (enabled) .65f else .4f)
        }
    Box(
        modifier
            .size(size)
            .onFocusChanged { focused = it.isFocused }
            .clip(CircleShape)
            .background(surface)
            .border(if (focused) 2.dp else 1.dp, outline, CircleShape)
            .semantics { contentDescription = label }
            .stableAction(enabled, onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalTransportTint provides tint.copy(alpha = if (enabled) 1f else .6f)
        ) {
            content()
        }
    }
}

internal val LocalTransportTint = staticCompositionLocalOf { PlayerWarmWhite }
