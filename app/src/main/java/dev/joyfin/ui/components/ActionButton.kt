package dev.joyfin.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.actionVisualFeedback
import dev.joyfin.ui.theme.MotionTokens
import dev.joyfin.ui.theme.Palette

private val LocalActionFocusedScale = staticCompositionLocalOf<Float?> { null }

/** Fixed footer actions keep their measured and drawn size, including retry/loading states. */
@Composable
fun FixedActionArea(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalActionFocusedScale provides MotionTokens.FixedScale, content = content)
}

@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    selected: Boolean = false,
    transparent: Boolean = false,
    focusedScale: Float? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.() -> Unit,
) {
    val tv = LocalDeviceLayout.current.television
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    val scale = focusedScale ?: if (tv && !transparent) MotionTokens.ButtonFocusedScale else MotionTokens.FixedScale
    Box(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .actionVisualFeedback(
                focused = focused,
                enabled = enabled,
                selected = selected,
                primary = primary,
                transparent = transparent,
                focusedScale = scale,
                shape = shape,
                interactionSource = interactionSource,
            )
            .stableAction(enabled, interactionSource, onClick)
            .padding(3.dp),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Glyph? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
    selected: Boolean = false,
    quiet: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    loading: Boolean = false,
    enabledWhileLoading: Boolean = false,
    loadingLabel: String = "正在加载",
    focusedScale: Float? = LocalActionFocusedScale.current,
) {
    val color =
        if (!enabled && !loading) Palette.Muted
        else if (primary) Palette.Background else Palette.Text
    FocusSurface(
        onClick,
        modifier
            .heightIn(min = 48.dp)
            .semantics {
                if (loading) {
                    contentDescription = text
                    stateDescription = loadingLabel
                    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                }
            },
        enabled && (!loading || enabledWhileLoading),
        primary && (enabled || loading),
        selected,
        quiet,
        focusedScale = focusedScale,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(
                Modifier.padding(horizontal = if (LocalDeviceLayout.current.television) 18.dp else 12.dp, vertical = 11.dp)
                    .then(if (loading) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) AppIcon(icon, color = color)
                Text(
                    text,
                    color = color,
                    fontSize = 15.sp,
                    fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
                    letterSpacing = 0.2.sp,
                    textAlign = TextAlign.Center,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                )
            }
            if (loading) LoadingSpinner(tint = color)
        }
    }
}

@Composable
fun IconButton(
    label: String,
    glyph: Glyph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    FocusSurface(
        onClick,
        modifier.size(48.dp).semantics {
            contentDescription = label
            if (loading) {
                stateDescription = "正在加载"
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            }
        },
        selected = selected,
        enabled = enabled && !loading,
        transparent = true,
    ) {
        if (loading) LoadingSpinner()
        else AppIcon(glyph, color = if (enabled) Palette.Text else Palette.Muted)
    }
}
