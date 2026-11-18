package dev.joyfin.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import dev.joyfin.ui.device.DeviceLayout
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.device.isTelevision

object Palette {
    val Background: Color @Composable @ReadOnlyComposable get() = if (tv) CinemaStyle.Background else Color(0xFF0B0B0D)
    val Surface: Color @Composable @ReadOnlyComposable get() = if (tv) CinemaStyle.Surface else Color(0xFF19191D)
    val Elevated: Color @Composable @ReadOnlyComposable get() = if (tv) Color(0xFF1B252B) else Color(0xFF29292F)
    val Text: Color @Composable @ReadOnlyComposable get() = if (tv) Color(0xFFECEEEB) else Color(0xFFF4F4F6)
    val Muted: Color @Composable @ReadOnlyComposable get() = if (tv) CinemaStyle.Muted else Color(0xFFA6A6AF)
    val Selected: Color @Composable @ReadOnlyComposable get() = if (tv) Color(0xFF102B25) else Color(0xFF2B2B31)
    val Pressed: Color @Composable @ReadOnlyComposable get() = if (tv) Color(0xFF26332F) else Color(0xFF34343A)
    val Line = Color(0x1AFFFFFF)
    val BorderSubtle = Color(0x1FFFFFFF)
    val BorderFocus = Color(0xFFF4F4F6)
    val TrackBackground = Color(0x26FFFFFF)
    val Error = Color(0xFFE2E2E8)
    val Accent: Color @Composable @ReadOnlyComposable get() = if (tv) CinemaStyle.Accent else Text
    private val tv: Boolean @Composable @ReadOnlyComposable get() = LocalDeviceLayout.current.television
}

@Composable
fun BoundlessTheme(content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    CompositionLocalProvider(
        LocalDeviceLayout provides DeviceLayout(configuration.isTelevision(), configuration.screenWidthDp),
    ) {
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
            ProvideMotionEnvironment(content = content)
        }
    }
}
