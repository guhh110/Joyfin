package dev.joyfin.ui.features.player.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import dev.joyfin.playback.SkipRange
import dev.joyfin.ui.components.timeLabel

internal fun skipTimelineDescription(ranges: List<SkipRange>): String = ranges.joinToString(" · ") {
    "${it.label} ${timeLabel(it.startMs)}–${timeLabel(it.endMs)}"
}

/** Inline tint is drawn over played/buffered progress, within the main track's height. */
internal fun DrawScope.drawSkipTimeline(
    ranges: List<SkipRange>,
    durationMs: Long,
    positionMs: Long,
    centerY: Float,
    trackHeight: Float,
) {
    if (durationMs <= 0 || size.width <= 0 || trackHeight <= 0) return
    val inset = minOf(trackHeight / 2, size.width / 2)
    ranges.forEach { range ->
        if (range.startMs < 0 || range.endMs > durationMs || range.endMs <= range.startMs) return@forEach
        val startX = (size.width * (range.startMs.toFloat() / durationMs)).coerceIn(inset, size.width - inset)
        val endX = (size.width * (range.endMs.toFloat() / durationMs)).coerceIn(inset, size.width - inset)
        val active = positionMs >= range.startMs && positionMs < range.endMs
        val tint = Color(0xFFD6BE8A).copy(alpha = if (active) .95f else .65f)
        drawLine(tint, Offset(startX, centerY), Offset(endX, centerY), trackHeight, StrokeCap.Round)
    }
}
