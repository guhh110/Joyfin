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
import androidx.compose.ui.graphics.StrokeCap
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
            val trackHeight = 4.dp.toPx()
            val radius = trackHeight / 2

            // 底槽
            drawLine(
                Palette.TrackBackground,
                Offset(radius, y),
                Offset(end - radius, y),
                trackHeight,
                cap = StrokeCap.Round,
            )

            if (!knownDuration) return@Canvas

            // 缓冲进度
            val bufferedEnd = (end * (bufferedMs.toFloat() / durationMs.coerceAtLeast(1))).coerceIn(radius, end - radius)
            if (bufferedEnd > radius) {
                drawLine(
                    Palette.Muted.copy(alpha = 0.40f),
                    Offset(radius, y),
                    Offset(bufferedEnd, y),
                    trackHeight,
                    cap = StrokeCap.Round,
                )
            }

            // 当前播放/预览进度
            val currentFrac = (draft ?: fraction).coerceIn(0f, 1f)
            val currentX = (end * currentFrac).coerceIn(radius, end - radius)
            if (currentX > radius) {
                drawLine(
                    Palette.Gold,
                    Offset(radius, y),
                    Offset(currentX, y),
                    trackHeight,
                    cap = StrokeCap.Round,
                )
            }

            // 微光滑块
            if (focused) {
                // 外层发光晕环
                drawCircle(Palette.Gold.copy(alpha = 0.32f), 9.dp.toPx(), Offset(currentX, y))
                // 中层光圈
                drawCircle(Palette.Gold, 6.dp.toPx(), Offset(currentX, y))
                // 核心纯白实心
                drawCircle(Palette.Text, 3.5.dp.toPx(), Offset(currentX, y))
            } else if (preview != null) {
                drawCircle(Palette.Gold.copy(alpha = 0.40f), 7.dp.toPx(), Offset(currentX, y))
                drawCircle(Palette.Gold, 4.5.dp.toPx(), Offset(currentX, y))
            } else {
                drawCircle(Palette.Text, 4.dp.toPx(), Offset(currentX, y))
            }
        }
    }
}
