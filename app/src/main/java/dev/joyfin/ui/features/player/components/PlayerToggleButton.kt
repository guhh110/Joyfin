package dev.joyfin.ui.features.player.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.joyfin.ui.device.LocalDeviceLayout

@Composable
fun PlayerToggleButton(
    playing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    val tv = LocalDeviceLayout.current.television
    PlayerTransportButton(
        if (loading) "正在加载" else if (playing) "暂停" else "继续播放",
        if (tv) 80.dp else 64.dp,
        enabled && !loading,
        onClick,
        modifier,
        primary = true,
    ) {
        if (loading) {
            PlayerLoadingIndicator()
            return@PlayerTransportButton
        }
        val tint = LocalTransportTint.current
        Canvas(Modifier.size(if (tv) 32.dp else 28.dp)) {
            val unit = size.width / 24f
            val stroke = 1.6f * unit
            if (playing) {
                listOf(9f, 15f).forEach { x ->
                    drawLine(
                        tint,
                        Offset(x * unit, 5f * unit),
                        Offset(x * unit, 19f * unit),
                        stroke,
                        StrokeCap.Round,
                    )
                }
            } else {
                // Triangle centroid is at the button center, including its optical rightward
                // weight.
                val triangle =
                    Path().apply {
                        moveTo(8.5f * unit, 5f * unit)
                        lineTo(19f * unit, 12f * unit)
                        lineTo(8.5f * unit, 19f * unit)
                        close()
                    }
                drawPath(
                    triangle,
                    tint,
                    style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}
