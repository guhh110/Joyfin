package dev.joyfin.ui.features.home.tv

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.joyfin.ui.components.CinemaHeader
import dev.joyfin.ui.features.entry.components.JoyfinLogo
import dev.joyfin.ui.model.BrowseActions

@Composable
internal fun TvHomeHeader(actions: BrowseActions, onHome: () -> Unit, modifier: Modifier = Modifier) {
    CinemaHeader(
        title = "",
        actions = actions.copy(home = onHome),
        modifier = modifier,
        back = false,
        active = "home",
        titleContent = {
            JoyfinLogo(Modifier.size(56.dp).testTag("home:app-logo")
                .semantics { contentDescription = "Joyfin" })
        },
    )
}
