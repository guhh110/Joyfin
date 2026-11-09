package dev.joyfin.ui.features.search.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.focusTarget

@Composable
internal fun MobileSearchHeader(back: () -> Unit, summary: String,
    field: @Composable () -> Unit, submit: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag("page:header").semantics { stateDescription = summary },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton("返回", Glyph.Back, back, Modifier.focusTarget("header:back"))
        Box(Modifier.weight(1f)) { field() }
        submit()
    }
}
