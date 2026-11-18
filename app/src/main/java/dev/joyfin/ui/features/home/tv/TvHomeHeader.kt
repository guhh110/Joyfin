package dev.joyfin.ui.features.home.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.joyfin.ui.components.CinemaHeader
import dev.joyfin.ui.model.BrowseActions

@Composable
internal fun TvHomeHeader(actions: BrowseActions, onHome: () -> Unit, modifier: Modifier = Modifier) {
    CinemaHeader(
        title = "私人放映厅",
        actions = actions.copy(home = onHome),
        modifier = modifier,
        back = false,
        active = "home",
    )
}
