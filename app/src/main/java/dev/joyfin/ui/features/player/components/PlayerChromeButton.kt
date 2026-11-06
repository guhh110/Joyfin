package dev.joyfin.ui.features.player.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.ui.components.AppIcon
import dev.joyfin.ui.components.Glyph
import dev.joyfin.ui.components.stableAction
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.actionVisualFeedback
import dev.joyfin.ui.theme.Palette

/** The video is the surface: only focus, selection and presses reveal a button background. */
@Composable
internal fun PlayerChromeButton(
    label: String,
    glyph: Glyph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    compact: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val tv = LocalDeviceLayout.current.television
    val tint = Palette.Text.copy(alpha = if (enabled) 1f else .45f)
    Row(
        modifier
            .heightIn(min = 48.dp)
            .then(if (text == null) Modifier.width(48.dp) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .actionVisualFeedback(
                focused = focused,
                enabled = enabled,
                selected = selected,
                transparent = true,
                shape = CircleShape,
                interactionSource = interaction,
                overrideBackgroundColor = when {
                    focused -> Color.White.copy(alpha = .16f)
                    selected -> Color.White.copy(alpha = .08f)
                    else -> Color.Transparent
                },
                overrideBorderColor = if (focused) Palette.BorderFocus.copy(alpha = .85f) else Color.Transparent,
            )
            .semantics { contentDescription = label }
            .stableAction(enabled, interaction, onClick)
            .padding(horizontal = if (text == null) 0.dp else if (compact) 8.dp else if (tv) 16.dp else 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(glyph, Modifier.size(if (text == null) 22.dp else if (compact) 18.dp else 20.dp), color = tint)
        if (text != null) Text(
            text,
            color = tint,
            fontSize = if (tv && !compact) 15.sp else 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .65f), blurRadius = 6f)),
        )
    }
}
