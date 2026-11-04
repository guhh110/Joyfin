package dev.joyfin.ui.features.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.ui.components.*
import dev.joyfin.ui.theme.Palette

@Composable
internal fun SettingsGroup(title: String, icon: Glyph, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppIcon(icon, Modifier.size(17.dp), color = Palette.Muted)
            Caption(title, color = Palette.Text)
        }
        Column(
            Modifier.fillMaxWidth()
                .background(Palette.Surface.copy(alpha = .65f), RoundedCornerShape(18.dp))
                .border(.5.dp, Palette.Line, RoundedCornerShape(18.dp))
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

@Composable
internal fun SettingsSwitch(checked: Boolean) {
    Box(
        Modifier.size(38.dp, 22.dp)
            .background(if (checked) Palette.Text else Palette.Elevated, RoundedCornerShape(11.dp))
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(16.dp).background(
            if (checked) Palette.Background else Palette.Muted, RoundedCornerShape(8.dp),
        ))
    }
}
