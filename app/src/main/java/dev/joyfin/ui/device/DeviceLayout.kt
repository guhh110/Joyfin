package dev.joyfin.ui.device

import android.content.res.Configuration
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

internal fun Configuration.isTelevision() =
    (uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION

@Immutable
data class DeviceLayout(val television: Boolean = true, val widthDp: Int = 960) {
    val compact
        get() = !television && widthDp < 600

    val gutter
        get() = if (television) 48.dp else 20.dp
}

val LocalDeviceLayout = staticCompositionLocalOf { DeviceLayout() }
