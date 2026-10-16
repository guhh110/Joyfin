package dev.joyfin.ui.features.settings.tv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.app.SettingsUiState
import dev.joyfin.session.EntryState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.features.settings.components.SettingsContent
import dev.joyfin.ui.model.BrowseActions

@Composable
internal fun TvSettingsLayout(
    ui: EntryState,
    actions: BrowseActions,
    autoNext: Boolean,
    request: SettingsUiState = SettingsUiState(),
    audioOutputMode: dev.joyfin.data.AudioOutputMode = dev.joyfin.data.AudioOutputMode.AUTO,
) {
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 48.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        SettingsContent(ui, actions, autoNext, request, audioOutputMode)
    }
}
