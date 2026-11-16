package dev.joyfin.ui.cyber

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 45° 斜切角异形装甲多边形 (Chamfered Corner Shape)
 * 可单独控制每个角的切角尺寸，打破普通矩形圆角的刻板印象。
 */
class ChamferCornerShape(
    val topLeft: Dp = 0.dp,
    val topRight: Dp = 0.dp,
    val bottomRight: Dp = 0.dp,
    val bottomLeft: Dp = 0.dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val tl = with(density) { topLeft.toPx() }
        val tr = with(density) { topRight.toPx() }
        val br = with(density) { bottomRight.toPx() }
        val bl = with(density) { bottomLeft.toPx() }

        val path = Path().apply {
            // 从左上开始
            if (tl > 0f) {
                moveTo(0f, tl)
                lineTo(tl, 0f)
            } else {
                moveTo(0f, 0f)
            }

            // 右上
            if (tr > 0f) {
                lineTo(size.width - tr, 0f)
                lineTo(size.width, tr)
            } else {
                lineTo(size.width, 0f)
            }

            // 右下
            if (br > 0f) {
                lineTo(size.width, size.height - br)
                lineTo(size.width - br, size.height)
            } else {
                lineTo(size.width, size.height)
            }

            // 左下
            if (bl > 0f) {
                lineTo(bl, size.height)
                lineTo(0f, size.height - bl)
            } else {
                lineTo(0f, size.height)
            }

            close()
        }
        return Outline.Generic(path)
    }

    companion object {
        /** 常用对角双切角（左上 + 右下），呈现极致战术卡扣感 */
        fun diagonal(cut: Dp = 14.dp) = ChamferCornerShape(topLeft = cut, bottomRight = cut)

        /** 单右下切角，适合装甲按键与卡片 */
        fun singleBottomRight(cut: Dp = 14.dp) = ChamferCornerShape(bottomRight = cut)

        /** 单右上切角，适合海报微标与舱盖 */
        fun singleTopRight(cut: Dp = 14.dp) = ChamferCornerShape(topRight = cut)

        /** 四角微切角，适合紧凑装甲按键 */
        fun all(cut: Dp = 8.dp) = ChamferCornerShape(cut, cut, cut, cut)
    }
}

/**
 * 六边形战术芯片多边形 (Hexagonal Mecha Badge Shape)
 * 两侧为 30°~45° 梯形斜切，常用于战术状态徽章。
 */
class HexagonShape(val chamferWidth: Dp = 8.dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val cw = with(density) { chamferWidth.toPx() }.coerceAtMost(size.width / 2f)
        val path = Path().apply {
            moveTo(cw, 0f)
            lineTo(size.width - cw, 0f)
            lineTo(size.width, size.height / 2f)
            lineTo(size.width - cw, size.height)
            lineTo(cw, size.height)
            lineTo(0f, size.height / 2f)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * 梯形航天舱战术标签 (Trapezoid Tab Shape)
 */
class TrapezoidTabShape(val slantWidth: Dp = 12.dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val sw = with(density) { slantWidth.toPx() }
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width - sw, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}
