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
    PageHeader(summary, onBack = back, scrollActions = false) {
        Box(Modifier.weight(1f).padding(end = 8.dp)) { field() }
        submit()
    }
}
