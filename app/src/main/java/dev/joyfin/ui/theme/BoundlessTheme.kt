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
    val Background = dev.joyfin.ui.cyber.CyberPalette.VoidBlack
    val Surface = dev.joyfin.ui.cyber.CyberPalette.SlateMetal
    val Elevated = dev.joyfin.ui.cyber.CyberPalette.PanelElevated
    val Text = dev.joyfin.ui.cyber.CyberPalette.TextBright
    val Muted = dev.joyfin.ui.cyber.CyberPalette.TextSecondary
    val Selected = dev.joyfin.ui.cyber.CyberPalette.PanelElevated
    val Pressed = dev.joyfin.ui.cyber.CyberPalette.PanelPressed
    val Line = dev.joyfin.ui.cyber.CyberPalette.ArmorSeam
    val BorderSubtle = dev.joyfin.ui.cyber.CyberPalette.ArmorBorder
    val BorderFocus = dev.joyfin.ui.cyber.CyberPalette.LaserCyan
    val TrackBackground = Color(0x3300F0FF)
    val Error = dev.joyfin.ui.cyber.CyberPalette.AlertCrimson
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
