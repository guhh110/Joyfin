package dev.joyfin.ui.features.library.tv

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.data.LibraryBrowseOptions
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.focusTarget

@Composable
internal fun TvLibraryControls(value: LibraryBrowseOptions, sort: () -> Unit, watched: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionButton("${value.sort.label} · ${if (value.descending) "降序" else "升序"}", sort,
            Modifier.focusTarget("library:sort"), Glyph.List)
        ActionButton(value.watched.label, watched, Modifier.focusTarget("library:watched"), Glyph.CheckCircle)
    }
}
