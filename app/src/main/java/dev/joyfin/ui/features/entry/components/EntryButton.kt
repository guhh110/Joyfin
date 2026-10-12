package dev.joyfin.ui.features.entry.components

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
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.ui.components.LoadingSpinner
import dev.joyfin.ui.components.stableAction
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.theme.Palette

@Composable
internal fun EntryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    quiet: Boolean = false,
    enabled: Boolean = true,
    enabledWhileBusy: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(
                min =
                    if (quiet) 48.dp else if (LocalDeviceLayout.current.television) 56.dp else 54.dp
            )
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(
                if (quiet)
                    if (focused) Palette.Surface else androidx.compose.ui.graphics.Color.Transparent
                else if (busy && !enabledWhileBusy) Palette.Text.copy(alpha = .6f) else Palette.Text
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) EntryChampagne else androidx.compose.ui.graphics.Color.Transparent,
                shape,
            )
            .semantics {
                if (busy) {
                    contentDescription = label
                    stateDescription = if (enabledWhileBusy) "正在加载，点击取消" else "正在加载"
                    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                }
            }
            .stableAction(enabled && (!busy || enabledWhileBusy), onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.then(if (busy) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!quiet)
                Box(Modifier.size(17.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(Modifier.size(17.dp)) {
                        val u = size.width / 24f
                        val path =
                            androidx.compose.ui.graphics.Path().apply {
                                moveTo(4 * u, 12 * u)
                                lineTo(20 * u, 12 * u)
                                moveTo(13 * u, 5 * u)
                                lineTo(20 * u, 12 * u)
                                lineTo(13 * u, 19 * u)
                            }
                        drawPath(
                            path,
                            Palette.Background,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(1.3.dp.toPx()),
                        )
                    }
                }
            Text(
                label,
                color = if (quiet) Palette.Muted else Palette.Background,
                fontSize = if (LocalDeviceLayout.current.television) 15.sp else 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
            )
        }
        if (busy)
            LoadingSpinner(diameter = 20.dp, tint = if (quiet) Palette.Text else Palette.Background)
    }
}
