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
    val Background = Color(0xFF0B0D10)
    val Surface = Color(0xFF191D21)
    val Elevated = Color(0xFF23282C)
    val Text = Color(0xFFF1EFE9)
    val Muted = Color(0xFF9DA3A8)
    val Gold = Color(0xFFCE9C6D)
    val Line = Color(0x1AFFFFFF)
    val Error = Color(0xFFFFB4AB)
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
            content = content,
        )
    }
}
