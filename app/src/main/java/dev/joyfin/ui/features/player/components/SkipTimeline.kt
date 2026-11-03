package dev.joyfin.ui.features.player.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import dev.joyfin.playback.SkipRange
import dev.joyfin.ui.components.timeLabel
import dev.joyfin.ui.theme.Palette

internal fun skipTimelineDescription(ranges: List<SkipRange>): String = ranges.joinToString(" · ") {
    "${it.label} ${timeLabel(it.startMs)}–${timeLabel(it.endMs)}"
}

/** Separate lane keeps skip segments visible even behind played and buffered progress. */
internal fun DrawScope.drawSkipTimeline(
    ranges: List<SkipRange>,
    durationMs: Long,
    positionMs: Long,
    centerY: Float,
) {
    if (durationMs <= 0 || size.width <= 0) return
    val inset = minOf(2.dp.toPx(), size.width / 2)
    val width = (size.width - inset * 2).coerceAtLeast(0f)
    ranges.forEach { range ->
        if (range.startMs < 0 || range.endMs > durationMs || range.endMs <= range.startMs) return@forEach
        val startX = inset + width * (range.startMs.toFloat() / durationMs)
        val endX = inset + width * (range.endMs.toFloat() / durationMs)
        val active = positionMs >= range.startMs && positionMs < range.endMs
        val tint = Palette.Text.copy(alpha = if (active) .85f else .45f)
        drawLine(tint, Offset(startX, centerY), Offset(endX, centerY), 4.dp.toPx(), StrokeCap.Round)
        listOf(startX, endX).forEach { x ->
            drawLine(tint, Offset(x, centerY - 3.dp.toPx()), Offset(x, centerY + 3.dp.toPx()), 1.dp.toPx())
        }
    }
}
