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
    val Background = Color(0xFF0A0C0F)
    val Surface = Color(0xFF15181D)
    val Elevated = Color(0xFF22262E)
    val Text = Color(0xFFF5F3ED)
    val Muted = Color(0xFF9EA3A9)
    val Gold = Color(0xFFD4AF37)
    val Line = Color(0x1AFFFFFF)
    val BorderSubtle = Color(0x1FFFFFFF)
    val BorderFocus = Color(0xFFFAF8F5)
    val GlowFocus = Color(0x33F5F3ED)
    val TrackBackground = Color(0x26FFFFFF)
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
        ) {
            ProvideMotionEnvironment(content = content)
        }
    }
}
