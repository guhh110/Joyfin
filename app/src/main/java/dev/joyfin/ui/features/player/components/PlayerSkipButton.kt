package dev.joyfin.ui.features.player.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.ui.device.LocalDeviceLayout

@Composable
fun PlayerSkipButton(
    forward: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tv = LocalDeviceLayout.current.television
    PlayerTransportButton(
        if (forward) "快进 10 秒" else "快退 10 秒",
        if (tv) 56.dp else 48.dp,
        enabled,
        onClick,
        modifier,
    ) {
        val tint = LocalTransportTint.current
        Box(Modifier.size(if (tv) 30.dp else 26.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val unit = size.width / 24f
                val stroke = Stroke(1.3f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawArc(
                    tint,
                    -90f,
                    if (forward) 300f else -300f,
                    false,
                    Offset(2f * unit, 2f * unit),
                    Size(20f * unit, 20f * unit),
                    style = stroke,
                )
                val arrow =
                    Path().apply {
                        val direction = if (forward) 1f else -1f
                        // Arrow tip meets the arc at twelve o'clock and follows its tangent.
                        moveTo((12f - direction * 3.5f) * unit, 0f)
                        lineTo(12f * unit, 2f * unit)
                        lineTo((12f - direction * 3.5f) * unit, 5f * unit)
                    }
                drawPath(arrow, tint, style = stroke)
            }
            Text(
                "10",
                color = tint,
                fontSize = if (tv) 10.sp else 9.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
