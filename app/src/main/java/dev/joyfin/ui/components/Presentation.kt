package dev.joyfin.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.joyfin.ui.theme.MotionTokens
import dev.joyfin.ui.theme.rememberMotionDuration
import dev.joyfin.ui.theme.rememberMotionSpec
import kotlinx.coroutines.launch

/**
 * 页面级展示宿主：
 * 1. 新页面进入 160ms 仅淡入
 * 2. 返回已有页面直接恢复（不重播进入动画）
 */
@Composable
fun PageFadeHost(
    pageKey: String,
    modifier: Modifier = Modifier,
    isReturn: Boolean = false,
    content: @Composable () -> Unit,
) {
    val duration = rememberMotionDuration(MotionTokens.PageEnterDurationMs)
    val alphaAnim = remember(pageKey) { Animatable(if (isReturn || duration <= 0) 1f else 0f) }

    LaunchedEffect(pageKey, isReturn) {
        if (!isReturn && duration > 0 && alphaAnim.value < 1f) {
            alphaAnim.animateTo(
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = duration,
                    easing = MotionTokens.Easing,
                ),
            )
        } else {
            alphaAnim.snapTo(1f)
        }
    }

    Box(modifier.graphicsLayer { alpha = alphaAnim.value }) {
        content()
    }
}

/**
 * 弹层退场宿主（Exit Host）：
 * 1. 进入 180ms 淡入并位移（TV 侧栏 12dp / 手机底部 16dp）
 * 2. 退出 140ms 平滑淡出与位移
 * 3. 退场期间拦截交互，阻止焦点和按键穿透到底层页面
 * 4. 动画完全结束后才撤销窗口，恢复触发源焦点
 */
@Composable
fun SheetPresentationHost(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    slideDistance: Dp,
    slideHorizontal: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(isExiting: Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isExiting by remember { mutableStateOf(false) }
    val latestDismiss by rememberUpdatedState(onDismissRequest)

    val enterDuration = rememberMotionDuration(MotionTokens.SheetEnterDurationMs)
    val exitDuration = rememberMotionDuration(MotionTokens.SheetExitDurationMs)

    val transitionProgress = remember { Animatable(0f) }

    fun requestExit() {
        if (isExiting) return
        isExiting = true
        scope.launch {
            if (exitDuration > 0) {
                transitionProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = exitDuration,
                        easing = MotionTokens.Easing,
                    ),
                )
            } else {
                transitionProgress.snapTo(0f)
            }
            latestDismiss()
        }
    }

    LaunchedEffect(visible) {
        if (visible) {
            isExiting = false
            if (enterDuration > 0) {
                transitionProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = enterDuration,
                        easing = MotionTokens.Easing,
                    ),
                )
            } else {
                transitionProgress.snapTo(1f)
            }
        } else {
            requestExit()
        }
    }

    val progress = transitionProgress.value
    val offsetProgress = 1f - progress

    val slideOffset = slideDistance * offsetProgress

    Box(
        modifier
            .graphicsLayer { alpha = progress.coerceIn(0f, 1f) }
            .then(
                if (slideHorizontal) Modifier.offset(x = slideOffset)
                else Modifier.offset(y = slideOffset)
            )
            .then(
                if (isExiting) {
                    Modifier
                        .clearAndSetSemantics {}
                        .onPreviewKeyEvent { true }
                        .pointerInput(Unit) { detectTapGestures {} }
                } else Modifier
            )
    ) {
        content(isExiting)
    }
}

/**
 * 播放器控制栏淡入淡出宿主：
 * 1. 进入 180ms，退出 140ms 淡入淡出
 * 2. 隐藏（退出）开始的一瞬间立即停用控制栏所有输入与焦点，使遥控与手势立即交还底层
 */
@Composable
fun PlayerControlsExitHost(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val enterSpec = rememberMotionSpec<Float>(MotionTokens.ControlEnterDurationMs)
    val exitSpec = rememberMotionSpec<Float>(MotionTokens.ControlExitDurationMs)

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (visible) enterSpec else exitSpec,
        label = "playerControlsAlpha",
    )

    if (alpha > 0f) {
        Box(
            modifier
                .graphicsLayer { this.alpha = alpha }
                .then(
                    if (!visible) {
                        Modifier
                            .clearAndSetSemantics {}
                            .onPreviewKeyEvent { false }
                            .pointerInput(Unit) {}
                    } else Modifier
                )
        ) {
            content()
        }
    }
}
