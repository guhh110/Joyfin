package dev.joyfin.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest

/** Indeterminate progress lives inside the operation's existing button. */
@Composable
fun LoadingSpinner(
    modifier: Modifier = Modifier,
    diameter: Dp = 20.dp,
    tint: Color = dev.joyfin.ui.theme.Palette.Text,
) {
    val angle = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val scale = coroutineContext[MotionDurationScale]
        snapshotFlow { scale?.scaleFactor ?: 1f }
            .collectLatest { durationScale ->
                angle.snapTo(0f)
                if (durationScale > 0f)
                    angle.animateTo(360f, infiniteRepeatable(tween(1200, easing = LinearEasing)))
            }
    }
    Canvas(
        modifier.size(diameter).semantics {
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        }
    ) {
        val stroke = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(
            tint.copy(alpha = .2f),
            radius = (size.minDimension - stroke.width) / 2f,
            style = stroke,
        )
        drawArc(
            tint,
            angle.value - 90f,
            90f,
            false,
            topLeft = androidx.compose.ui.geometry.Offset(stroke.width / 2, stroke.width / 2),
            size =
                androidx.compose.ui.geometry.Size(
                    size.width - stroke.width,
                    size.height - stroke.width,
                ),
            style = stroke,
        )
    }
}
