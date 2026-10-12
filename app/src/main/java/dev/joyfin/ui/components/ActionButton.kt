package dev.joyfin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
import dev.joyfin.ui.theme.Palette

@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    selected: Boolean = false,
    transparent: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) Palette.Text else if (selected) Palette.Gold else Color.Transparent,
                shape,
            )
            .stableAction(enabled, onClick)
            .padding(3.dp)
            .clip(shape)
            .background(
                if (primary) Palette.Text
                else if (focused) Palette.Elevated
                else if (transparent) Color.Transparent else Palette.Surface
            ),
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
) {
    val color =
        if (!enabled) Palette.Muted
        else if (primary) Palette.Background else if (selected) Palette.Gold else Palette.Text
    FocusSurface(
        onClick,
        modifier
            .heightIn(min = if (LocalDeviceLayout.current.television) 46.dp else 48.dp)
            .semantics {
                if (loading) {
                    contentDescription = text
                    stateDescription = loadingLabel
                    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                }
            },
        enabled && (!loading || enabledWhileLoading),
        primary,
        selected,
        quiet,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    .then(if (loading) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier),
                horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) AppIcon(icon, color = color)
                Text(
                    text,
                    color = color,
                    fontSize = 14.sp,
                    fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal,
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
        else AppIcon(glyph, color = if (selected) Palette.Gold else Palette.Text)
    }
}
