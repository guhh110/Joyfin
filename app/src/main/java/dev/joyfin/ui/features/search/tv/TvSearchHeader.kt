package dev.joyfin.ui.features.search.tv

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.focusTarget

@Composable
internal fun TvSearchHeader(back: () -> Unit, summary: String,
    field: @Composable () -> Unit, submit: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().testTag("search:toolbar"), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { field() }
            submit()
        }
        Caption(summary, maxLines = 1)
    }
}
