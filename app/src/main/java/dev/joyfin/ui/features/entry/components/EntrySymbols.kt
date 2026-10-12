package dev.joyfin.ui.features.entry.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.joyfin.ui.theme.Palette

@Composable
internal fun EntryServerIcon() {
    Canvas(Modifier.size(34.dp)) {
        val u = size.width / 34f
        val stroke = Stroke(1.dp.toPx())
        drawRoundRect(Palette.Line, cornerRadius = CornerRadius(7.dp.toPx()), style = stroke)
        listOf(9f, 19f).forEach { y ->
            drawRoundRect(
                Palette.Muted,
                Offset(9 * u, y * u),
                Size(16 * u, 6 * u),
                CornerRadius(u),
                style = stroke,
            )
            drawCircle(Palette.Muted, u / 2, Offset(12 * u, (y + 3) * u))
            drawLine(Palette.Muted, Offset(18 * u, (y + 3) * u), Offset(22 * u, (y + 3) * u), u)
        }
    }
}

@Composable
internal fun EntryNetworkSymbol() {
    Canvas(Modifier.size(44.dp).border(1.dp, EntryChampagne.copy(alpha = .3f), CircleShape)) {
        val stroke = Stroke(1.2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        val u = size.width / 44f
        listOf(12f, 8f, 4f).forEach { radius ->
            drawArc(
                EntryChampagne,
                -135f,
                90f,
                false,
                Offset((22 - radius) * u, (28 - radius) * u),
                Size(radius * 2 * u, radius * 2 * u),
                style = stroke,
            )
        }
        drawCircle(EntryChampagne, u, Offset(22 * u, 29 * u))
    }
}
