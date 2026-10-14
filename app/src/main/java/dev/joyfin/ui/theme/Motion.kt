package dev.joyfin.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

object MotionTokens {
    const val PressDurationMs: Int = 80
    const val FocusDurationMs: Int = 140
    const val PageEnterDurationMs: Int = 160
    const val SheetEnterDurationMs: Int = 180
    const val SheetExitDurationMs: Int = 140
    const val ControlEnterDurationMs: Int = 180
    const val ControlExitDurationMs: Int = 140

    const val ButtonFocusedScale: Float = 1.02f
    const val CardFocusedScale: Float = 1.03f
    const val FixedScale: Float = 1.00f

    val MediaCardVerticalReserve: Dp = 6.dp
    val PeopleCardVerticalReserve: Dp = 3.dp
    val TvSheetSlideDistance: Dp = 12.dp
    val MobileSheetSlideDistance: Dp = 16.dp

    val Easing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
}

val LocalMotionScale = staticCompositionLocalOf { 1f }

@Composable
@ReadOnlyComposable
fun systemAnimatorScale(): Float {
    val context = LocalContext.current
    return try {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
    } catch (_: Throwable) {
        1f
    }
}

@Composable
fun rememberMotionDuration(baseDurationMs: Int): Int {
    val scale = LocalMotionScale.current
    return remember(baseDurationMs, scale) {
        if (scale <= 0f) 0 else (baseDurationMs * scale).roundToInt()
    }
}

@Composable
fun <T> rememberMotionSpec(
    baseDurationMs: Int,
    easing: Easing = MotionTokens.Easing,
): AnimationSpec<T> {
    val duration = rememberMotionDuration(baseDurationMs)
    return remember(duration, easing) {
        if (duration <= 0) snap()
        else tween(durationMillis = duration, easing = easing)
    }
}

@Composable
fun ProvideMotionEnvironment(content: @Composable () -> Unit) {
    val scale = systemAnimatorScale()
    CompositionLocalProvider(LocalMotionScale provides scale, content = content)
}
