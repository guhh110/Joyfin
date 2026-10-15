package dev.joyfin.ui.features.search.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.focusTarget

@Composable
internal fun MobileSearchHeader(back: () -> Unit, summary: String,
    field: @Composable () -> Unit, submit: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconButton("返回", Glyph.Back, back, Modifier.focusTarget("header:back"))
            FilmTitle("搜索", Modifier.weight(1f), maxLines = 1)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { field() }
            submit()
        }
        Caption(summary, maxLines = 3)
    }
}
