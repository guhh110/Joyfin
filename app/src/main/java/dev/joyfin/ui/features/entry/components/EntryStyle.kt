package dev.joyfin.ui.features.entry.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.theme.Palette

@Composable
internal fun EntryTitle(title: String) {
    Text(
        title,
        color = Palette.Text,
        fontFamily = FontFamily.SansSerif,
        fontSize = if (LocalDeviceLayout.current.television) 34.sp else 32.sp,
        lineHeight = if (LocalDeviceLayout.current.television) 46.sp else 44.sp,
    )
}

@Composable
internal fun EntryText(text: String, modifier: Modifier = Modifier, subdued: Boolean = false) {
    Text(
        text,
        modifier,
        color = if (subdued) Palette.Muted.copy(alpha = .8f) else Palette.Muted,
        fontSize = if (LocalDeviceLayout.current.television) 13.sp else 12.sp,
        lineHeight = if (LocalDeviceLayout.current.television) 22.sp else 21.sp,
        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
    )
}
