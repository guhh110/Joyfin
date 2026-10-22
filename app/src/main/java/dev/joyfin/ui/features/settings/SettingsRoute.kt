package dev.joyfin.ui.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import dev.joyfin.app.SettingsUiState
import dev.joyfin.session.EntryState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.settings.mobile.MobileSettingsLayout
import dev.joyfin.ui.features.settings.tv.TvSettingsLayout
import dev.joyfin.ui.model.BrowseActions

@Composable
fun SettingsRoute(
    ui: EntryState,
    actions: BrowseActions,
    autoNext: Boolean,
    request: SettingsUiState = SettingsUiState(),
    audioOutputMode: dev.joyfin.data.AudioOutputMode = dev.joyfin.data.AudioOutputMode.AUTO,
    homeLayout: dev.joyfin.data.HomeLayoutPreferences = dev.joyfin.data.HomeLayoutPreferences(),
    homeLibraries: List<dev.joyfin.data.Media> = emptyList(),
) {
    if (LocalDeviceLayout.current.television) TvSettingsLayout(ui, actions, autoNext, request, audioOutputMode, homeLayout, homeLibraries)
    else MobileSettingsLayout(ui, actions, autoNext, request, audioOutputMode, homeLayout, homeLibraries)
}
