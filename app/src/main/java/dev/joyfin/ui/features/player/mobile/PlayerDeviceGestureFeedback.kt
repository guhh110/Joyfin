package dev.joyfin.ui.features.player.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.joyfin.playback.PlayerDeviceControl
import dev.joyfin.playback.PlayerDeviceControls
import dev.joyfin.playback.PlayerDeviceLevel
import dev.joyfin.ui.components.AppIcon
import dev.joyfin.ui.components.Caption
import dev.joyfin.ui.components.Glyph
import dev.joyfin.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Stable
internal class PlayerDeviceGestureState(private val controls: PlayerDeviceControls?) {
    var level by mutableStateOf<PlayerDeviceLevel?>(null)
        private set
    var active by mutableStateOf(false)
        private set
    private var requestedFraction = 0f

    fun begin(control: PlayerDeviceControl) {
        level = controls?.read(control)
        requestedFraction = level?.fraction ?: 0f
        active = level != null
    }

    fun dragBy(fraction: Float) {
        val current = level ?: return
        if (!active || current.unavailableReason != null || !fraction.isFinite()) return
        // Retain sub-step motion instead of rounding every event back to a volume index.
        requestedFraction = (requestedFraction + fraction).coerceIn(0f, 1f)
        level = controls?.set(current.control, requestedFraction)
    }

    fun end() { active = false }

    fun clear() {
        active = false
        level = null
    }
}

@Composable
internal fun rememberPlayerDeviceGestures(
    controls: PlayerDeviceControls?,
    enabled: Boolean,
): PlayerDeviceGestureState {
    val state = remember(controls) { PlayerDeviceGestureState(controls) }
    LaunchedEffect(enabled, state.active, state.level) {
        if (!enabled) state.clear()
        else if (!state.active && state.level != null) {
            delay(900)
            state.clear()
        }
    }
    return state
}

@Composable
internal fun PlayerDeviceGestureFeedback(level: PlayerDeviceLevel, modifier: Modifier = Modifier) {
    val brightness = level.control == PlayerDeviceControl.BRIGHTNESS
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.testTag("player:device-adjustment")
            .widthIn(min = 156.dp, max = 280.dp)
            .background(Palette.Background.copy(alpha = .94f), shape)
            .border(.5.dp, Palette.Line, shape)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppIcon(if (brightness) Glyph.Brightness else Glyph.Audio, Modifier.size(24.dp))
            Caption(
                (if (brightness) "亮度" else "音量") +
                    if (level.unavailableReason == null) " ${(level.fraction * 100).roundToInt()}%" else "",
                color = Palette.Text,
            )
        }
        if (level.unavailableReason != null) Caption(level.unavailableReason)
        else Box(
            Modifier.width(120.dp).height(4.dp)
                .background(Palette.Line, RoundedCornerShape(2.dp))
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(level.fraction, 0f..1f) }
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(level.fraction).background(Palette.Text, RoundedCornerShape(2.dp)))
        }
    }
}
