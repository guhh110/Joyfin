package dev.joyfin.ui.features.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.ui.components.*
import dev.joyfin.ui.theme.Palette

@Composable
internal fun PlayerPanelIcon(glyph: Glyph) {
    Box(Modifier.size(34.dp).background(Palette.Text.copy(alpha = .06f), RoundedCornerShape(10.dp)),
        contentAlignment = androidx.compose.ui.Alignment.Center) {
        AppIcon(glyph, Modifier.size(18.dp), color = Palette.Muted)
    }
}
