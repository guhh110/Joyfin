package dev.joyfin.ui.features.search.tv

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.focusTarget

@Composable
internal fun TvSearchHeader(back: () -> Unit, summary: String,
    field: @Composable () -> Unit, submit: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconButton("返回", Glyph.Back, back, Modifier.focusTarget("header:back"))
            FilmTitle("搜索媒体", Modifier.weight(1f), maxLines = 1)
        }
        Row(Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f)) { field() }
            submit()
        }
        Caption(summary, maxLines = 2)
    }
}
