package dev.joyfin.ui.interaction

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.joyfin.ui.theme.MotionTokens
import dev.joyfin.ui.theme.Palette
import dev.joyfin.ui.theme.rememberMotionSpec

/**
 * 统一交互反馈修饰符：
 * 1. 80ms 按压明暗反馈（控件形状内高亮/加深，快速确认亦可见）
 * 2. 140ms 焦点颜色/边框平滑过渡（白色焦点边框 2dp，浅色主操作增加深色内圈分隔）
 * 3. 140ms 绘图层平滑缩放，获焦提升 zIndex，不改变测量尺寸和焦点槽位
 */
@Composable
fun Modifier.actionVisualFeedback(
    focused: Boolean,
    enabled: Boolean = true,
    selected: Boolean = false,
    primary: Boolean = false,
    transparent: Boolean = false,
    focusedScale: Float = MotionTokens.FixedScale,
    shape: Shape,
    interactionSource: MutableInteractionSource? = null,
    overrideBorderColor: Color? = null,
    overrideBackgroundColor: Color? = null,
): Modifier {
    val pressed = interactionSource?.collectIsPressedAsState()?.value == true

    val scaleSpec = rememberMotionSpec<Float>(MotionTokens.FocusDurationMs)
    val colorSpec = rememberMotionSpec<Color>(MotionTokens.FocusDurationMs)
    val dpSpec = rememberMotionSpec<Dp>(MotionTokens.FocusDurationMs)
    val pressColorSpec = rememberMotionSpec<Color>(MotionTokens.PressDurationMs)

    val targetScale = if (focused && enabled) focusedScale else MotionTokens.FixedScale
    val animatedScale =
        animateFloatAsState(targetValue = targetScale, animationSpec = scaleSpec, label = "actionScale")

    val targetBorderWidth = if (focused) 2.dp else if (selected) 1.dp else 0.dp
    val animatedBorderWidth =
        animateDpAsState(
            targetValue = targetBorderWidth,
            animationSpec = dpSpec,
            label = "actionBorderWidth",
        )

    val targetBorderColor =
        overrideBorderColor
            ?: when {
                focused -> Palette.BorderFocus
                selected -> Palette.Muted
                else -> Color.Transparent
            }
    val animatedBorderColor =
        animateColorAsState(
            targetValue = targetBorderColor,
            animationSpec = colorSpec,
            label = "actionBorderColor",
        )

    val defaultBg =
        when {
            primary -> if (focused) Palette.Text else Palette.Text.copy(alpha = 0.92f)
            selected -> Palette.Selected
            focused -> Palette.Elevated
            transparent -> Color.Transparent
            else -> Palette.Surface
        }
    val baseBg = overrideBackgroundColor ?: defaultBg

    val targetBackgroundColor =
        if (pressed && enabled) {
            when {
                primary -> Palette.Text.copy(alpha = 0.82f)
                focused -> Palette.Elevated.copy(alpha = 0.90f)
                transparent -> Color.White.copy(alpha = 0.12f)
                else -> Palette.Pressed
            }
        } else {
            baseBg
        }
    val animatedBackground =
        animateColorAsState(
            targetValue = targetBackgroundColor,
            animationSpec = if (pressed) pressColorSpec else colorSpec,
            label = "actionBackground",
        )

    val primaryFocusSeparator = animateColorAsState(
        targetValue = if (primary && focused) Palette.Background else Palette.Background.copy(alpha = 0f),
        animationSpec = colorSpec,
        label = "actionPrimaryFocusSeparator",
    )

    return this
        .zIndex(if (focused) 1f else 0f)
        .graphicsLayer {
            scaleX = animatedScale.value
            scaleY = animatedScale.value
        }
        .clip(shape)
        .drawWithContent {
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(outline, animatedBackground.value)
            drawContent()
            val borderWidth = animatedBorderWidth.value.toPx()
            if (borderWidth > 0f)
                drawOutline(outline, animatedBorderColor.value, style = Stroke(borderWidth))
            // Draw inside the existing bounds: white outer frame, dark separator, light fill.
            // This also stays visible on fixed-size player/footer actions without layout changes.
            val inset = 3.dp.toPx()
            if (size.width > inset * 2 && size.height > inset * 2) {
                val separator = primaryFocusSeparator.value
                if (separator.alpha > 0f) {
                    val inner = shape.createOutline(
                        Size(size.width - inset * 2, size.height - inset * 2),
                        layoutDirection,
                        this,
                    )
                    translate(inset, inset) {
                        drawOutline(inner, separator, style = Stroke(2.dp.toPx()))
                    }
                }
            }
        }
}
