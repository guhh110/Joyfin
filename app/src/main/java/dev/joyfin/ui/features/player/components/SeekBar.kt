package dev.joyfin.ui.features.player.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.joyfin.playback.SeekPreviewActions
import dev.joyfin.playback.SeekPreviewState
import dev.joyfin.ui.components.Caption
import dev.joyfin.ui.components.timeLabel
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.theme.Palette

@Composable
fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    preview: SeekPreviewState?,
    actions: SeekPreviewActions,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val tv = LocalDeviceLayout.current.television
    var width by remember { mutableIntStateOf(1) }
    var focused by remember { mutableStateOf(false) }
    val currentActions by rememberUpdatedState(actions)
    val knownDuration = durationMs > 0
    val draft =
        if (knownDuration) preview?.targetMs?.toFloat()?.div(durationMs)?.coerceIn(0f, 1f) else null
    val fraction = if (knownDuration) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Caption(timeLabel(preview?.targetMs ?: positionMs), color = Palette.Text)
            Caption(if (knownDuration) timeLabel(durationMs) else "--:--")
        }
        Canvas(
            Modifier.fillMaxWidth()
                .height(48.dp)
                .focusTarget("player:progress", tv || (enabled && durationMs > 0))
                .onSizeChanged { width = it.width.coerceAtLeast(1) }
                .semantics {
                    contentDescription = "播放进度"
                    if (!enabled || !knownDuration) disabled()
                    progressBarRangeInfo =
                        if (knownDuration) ProgressBarRangeInfo(fraction, 0f..1f)
                        else ProgressBarRangeInfo.Indeterminate
                    if (enabled && durationMs > 0)
                        setProgress {
                            currentActions.absolute((it.coerceIn(0f, 1f) * durationMs).toLong())
                            currentActions.commit()
                            true
                        }
                }
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { e ->
                    if (
                        e.type == KeyEventType.KeyDown &&
                            enabled &&
                            durationMs > 0 &&
                            (e.key == Key.DirectionLeft || e.key == Key.DirectionRight)
                    ) {
                        currentActions.adjust(if (e.key == Key.DirectionLeft) -10_000 else 10_000)
                        true
                    } else false
                }
                .focusable(enabled = tv || (enabled && durationMs > 0))
                .pointerInput(durationMs, enabled) {
                    detectTapGestures {
                        if (enabled && durationMs > 0) {
                            currentActions.absolute(
                                ((it.x / width).coerceIn(0f, 1f) * durationMs).toLong()
                            )
                            currentActions.commit()
                        }
                    }
                }
                .pointerInput(durationMs, enabled) {
                    if (enabled && durationMs > 0)
                        detectHorizontalDragGestures(
                            onDragStart = {
                                currentActions.absolute(
                                    ((it.x / width).coerceIn(0f, 1f) * durationMs).toLong()
                                )
                            },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                currentActions.absolute(
                                    ((change.position.x / width).coerceIn(0f, 1f) * durationMs)
                                        .toLong()
                                )
                            },
                            onDragCancel = { currentActions.cancel() },
                            onDragEnd = { currentActions.commit() },
                        )
                }
        ) {
            val y = size.height / 2
            val end = size.width
            drawLine(Palette.Line, Offset(0f, y), Offset(end, y), 3.dp.toPx())
            // A saved resume position can arrive before duration; it must not look 100% complete.
            if (!knownDuration) return@Canvas
            drawLine(
                Palette.Muted.copy(alpha = .4f),
                Offset(0f, y),
                Offset(
                    end * (bufferedMs.toFloat() / durationMs.coerceAtLeast(1)).coerceIn(0f, 1f),
                    y,
                ),
                3.dp.toPx(),
            )
            val current = end * (draft ?: fraction)
            drawLine(
                if (focused) Palette.Gold else Palette.Text,
                Offset(0f, y),
                Offset(current, y),
                3.dp.toPx(),
            )
            drawCircle(
                if (focused) Palette.Gold else Palette.Text,
                if (focused) 7.dp.toPx() else 5.dp.toPx(),
                Offset(current, y),
            )
        }
    }
}
