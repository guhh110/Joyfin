package dev.joyfin.ui.cyber

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.joyfin.ui.theme.MotionTokens
import dev.joyfin.ui.theme.rememberMotionSpec

/**
 * 赛博战术 HUD 专属交互反馈修饰符 (Cyber-Mecha Visual Feedback)
 * 1. 获焦时：触发激光锁定框、四角准星瞄准、微弱霓虹发光光晕与微缩放提升；
 * 2. 按压时：触发高频全息能量电弧过载闪烁；
 * 3. 完美兼容 ChamferCornerShape 等异形装甲多边形。
 */
@Composable
fun Modifier.cyberVisualFeedback(
    focused: Boolean,
    enabled: Boolean = true,
    selected: Boolean = false,
    primary: Boolean = false,
    shape: Shape = ChamferCornerShape.diagonal(12.dp),
    interactionSource: MutableInteractionSource? = null,
    focusedScale: Float = 1.04f,
    showBrackets: Boolean = true,
    customBackground: Color? = null,
): Modifier {
    val pressed = interactionSource?.collectIsPressedAsState()?.value == true

    val scaleSpec = rememberMotionSpec<Float>(MotionTokens.FocusDurationMs)
    val colorSpec = rememberMotionSpec<Color>(MotionTokens.FocusDurationMs)

    val targetScale = if (focused && enabled) focusedScale else 1f
    val animatedScale = animateFloatAsState(targetValue = targetScale, animationSpec = scaleSpec, label = "cyberScale")

    val targetBorderColor = when {
        focused -> CyberPalette.LaserCyan
        selected -> CyberPalette.WarningAmber
        else -> CyberPalette.ArmorBorder
    }
    val animatedBorderColor = animateColorAsState(targetValue = targetBorderColor, animationSpec = colorSpec, label = "cyberBorder")

    val baseBg = customBackground ?: when {
        primary -> if (focused) CyberPalette.LaserCyan else CyberPalette.PanelElevated
        selected -> CyberPalette.PanelElevated
        focused -> CyberPalette.PanelElevated
        else -> CyberPalette.PanelSurface
    }

    val targetBg = if (pressed && enabled) {
        CyberPalette.PanelPressed
    } else {
        baseBg
    }
    val animatedBg = animateColorAsState(targetValue = targetBg, animationSpec = colorSpec, label = "cyberBg")

    return this
        .zIndex(if (focused) 2f else 0f)
        .graphicsLayer {
            scaleX = animatedScale.value
            scaleY = animatedScale.value
        }
        .clip(shape)
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val borderWidth = if (focused) 2.dp.toPx() else 1.dp.toPx()

            onDrawWithContent {
                // 1. 绘制装甲底色
                drawOutline(outline, animatedBg.value)
                // 2. 绘制内容
                drawContent()
                // 3. 绘制外框装甲激光线
                drawOutline(outline, animatedBorderColor.value, style = Stroke(borderWidth))
                // 4. 若获焦，绘制四角 HUD 战术瞄准准星
                if (focused && showBrackets) {
                    drawReticleBrackets(
                        color = CyberPalette.LaserCyan,
                        armLength = 12f,
                        thickness = 2.5f,
                        padding = 2f,
                    )
                }
            }
        }
}
