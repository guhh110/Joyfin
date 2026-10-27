package dev.joyfin.ui.layout.mobile

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.joyfin.ui.components.SheetPanel
import dev.joyfin.ui.components.SheetPresentationHost
import dev.joyfin.ui.theme.MotionTokens
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun MobileSheetLayout(
    title: String,
    onDismiss: () -> Unit,
    side: Boolean,
    modifier: Modifier,
    actions: (@Composable ColumnScope.() -> Unit)?,
    scrollContent: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val config = LocalConfiguration.current
    val width = if (side) (config.screenWidthDp.dp * .48f).coerceAtMost(380.dp) else 518.dp
    val height =
        if (side) Modifier.fillMaxHeight()
        else Modifier.heightIn(max = config.screenHeightDp.dp * .85f)

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dragOffsetY = remember { Animatable(0f) }
    val dismissThresholdPx = with(density) { 72.dp.toPx() }

    val dragModifier = if (!side) {
        Modifier.pointerInput(onDismiss) {
            detectVerticalDragGestures(
                onVerticalDrag = { change, dragAmount ->
                    change.consume()
                    val next = (dragOffsetY.value + dragAmount).coerceAtLeast(0f)
                    scope.launch { dragOffsetY.snapTo(next) }
                },
                onDragEnd = {
                    scope.launch {
                        if (dragOffsetY.value > dismissThresholdPx) {
                            dragOffsetY.animateTo(
                                targetValue = dragOffsetY.value + with(density) { 320.dp.toPx() },
                                animationSpec = tween(MotionTokens.SheetExitDurationMs, easing = MotionTokens.Easing),
                            )
                            onDismiss()
                        } else {
                            dragOffsetY.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(MotionTokens.SheetExitDurationMs, easing = MotionTokens.Easing),
                            )
                        }
                    }
                },
                onDragCancel = {
                    scope.launch {
                        dragOffsetY.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(MotionTokens.SheetExitDurationMs, easing = MotionTokens.Easing),
                        )
                    }
                },
            )
        }
    } else Modifier

    val dragHandleModifier = if (!side) {
        dragModifier.pointerInput(onDismiss) {
            detectTapGestures { onDismiss() }
        }
    } else Modifier

    SheetPresentationHost(
        visible = true,
        onDismissRequest = onDismiss,
        slideDistance = if (side) MotionTokens.TvSheetSlideDistance else MotionTokens.MobileSheetSlideDistance,
        slideHorizontal = side,
    ) { isExiting ->
        Box(
            Modifier.fillMaxSize().navigationBarsPadding().imePadding(),
            contentAlignment = if (side) Alignment.CenterEnd else Alignment.BottomCenter,
        ) {
            Box(
                Modifier.matchParentSize().pointerInput(onDismiss) {
                    detectTapGestures { if (!isExiting) onDismiss() }
                }
            )
            SheetPanel(
                title,
                onDismiss,
                modifier.offset { IntOffset(0, dragOffsetY.value.roundToInt()) },
                width,
                height,
                RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = if (side) 0.dp else 18.dp,
                    bottomStart = 0.dp,
                    bottomEnd = 0.dp,
                ),
                actions,
                scrollContent,
                isMobileBottom = !side,
                dragHandleModifier = dragHandleModifier,
                dragModifier = dragModifier,
                content = content,
            )
        }
    }
}
