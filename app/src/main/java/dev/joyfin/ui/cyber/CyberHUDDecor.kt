package dev.joyfin.ui.cyber

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 战术 HUD 四角瞄准框 (Reticle Brackets: ┌ ┐ └ ┘)
 * 当控件获焦时，在异形轮廓四角绘制动态发光瞄准准星。
 */
fun DrawScope.drawReticleBrackets(
    color: Color = CyberPalette.LaserCyan,
    armLength: Float = 14f,
    thickness: Float = 3f,
    padding: Float = 4f,
) {
    val left = -padding
    val top = -padding
    val right = size.width + padding
    val bottom = size.height + padding

    val stroke = Stroke(width = thickness)

    // 左上角 ┌
    val tl = Path().apply {
        moveTo(left, top + armLength)
        lineTo(left, top)
        lineTo(left + armLength, top)
    }
    drawPath(tl, color, style = stroke)

    // 右上角 ┐
    val tr = Path().apply {
        moveTo(right - armLength, top)
        lineTo(right, top)
        lineTo(right, top + armLength)
    }
    drawPath(tr, color, style = stroke)

    // 右下角 ┘
    val br = Path().apply {
        moveTo(right, bottom - armLength)
        lineTo(right, bottom)
        lineTo(right - armLength, bottom)
    }
    drawPath(br, color, style = stroke)

    // 左下角 └
    val bl = Path().apply {
        moveTo(left + armLength, bottom)
        lineTo(left, bottom)
        lineTo(left, bottom - armLength)
    }
    drawPath(bl, color, style = stroke)
}

/**
 * 分段反应堆等离子能量刻度柱 (Segmented Reactor Gauge)
 * 彻底颠覆普通连续线性进度条，化身为由多个发光能量块组成的高能反应堆柱。
 */
fun DrawScope.drawSegmentedGauge(
    progress: Float, // 0f ~ 1f
    segments: Int = 12,
    activeColor: Color = CyberPalette.LaserCyan,
    inactiveColor: Color = CyberPalette.PanelElevated,
    gap: Float = 3f,
) {
    val totalGaps = (segments - 1) * gap
    val segmentWidth = (size.width - totalGaps) / segments
    val activeCount = (progress * segments).toInt().coerceIn(0, segments)

    for (i in 0 until segments) {
        val x = i * (segmentWidth + gap)
        val color = if (i < activeCount) activeColor else inactiveColor
        drawRect(
            color = color,
            topLeft = Offset(x, 0f),
            size = Size(segmentWidth, size.height),
        )
    }
}

/**
 * 战术警示斜纹绘制 (Hazard Stripes: 45° 交替工业条纹)
 */
fun DrawScope.drawHazardStripes(
    stripeColor: Color = CyberPalette.WarningAmber.copy(alpha = 0.25f),
    stripeWidth: Float = 8f,
    gap: Float = 8f,
) {
    val step = stripeWidth + gap
    var startX = -size.height
    while (startX < size.width) {
        drawLine(
            color = stripeColor,
            start = Offset(startX, 0f),
            end = Offset(startX + size.height, size.height),
            strokeWidth = stripeWidth,
        )
        startX += step
    }
}

/**
 * 全息扫描线暗纹覆盖层 (Cyber Scanlines)
 */
@Composable
fun CyberScanlines(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val lineHeight = 3.dp.toPx()
        val spacing = 4.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawLine(
                color = CyberPalette.Scanline,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = lineHeight,
            )
            y += spacing + lineHeight
        }
    }
}
