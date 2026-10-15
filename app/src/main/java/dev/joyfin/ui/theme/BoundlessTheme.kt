package dev.joyfin.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import dev.joyfin.ui.device.DeviceLayout
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.device.isTelevision

object Palette {
    val Background = Color(0xFF0B0B0D)
    val Surface = Color(0xFF19191D)
    val Elevated = Color(0xFF29292F)
    val Text = Color(0xFFF4F4F6)
    val Muted = Color(0xFFA6A6AF)
    val Selected = Color(0xFF2B2B31)
    val Pressed = Color(0xFF34343A)
    val Line = Color(0x1AFFFFFF)
    val BorderSubtle = Color(0x1FFFFFFF)
    val BorderFocus = Color(0xFFF4F4F6)
    val TrackBackground = Color(0x26FFFFFF)
    val Error = Color(0xFFE2E2E8)
}

@Composable
fun BoundlessTheme(content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    MaterialTheme(
        colorScheme =
            darkColorScheme(
                primary = Palette.Text,
                onPrimary = Palette.Background,
                background = Palette.Background,
                onBackground = Palette.Text,
                surface = Palette.Surface,
                onSurface = Palette.Text,
                error = Palette.Error,
            )
    ) {
        CompositionLocalProvider(
            LocalDeviceLayout provides
                DeviceLayout(configuration.isTelevision(), configuration.screenWidthDp),
        ) {
            ProvideMotionEnvironment(content = content)
        }
    }
}
