package dev.joyfin.ui.features.entry.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.entry.mobile.MobileEntryLayout
import dev.joyfin.ui.features.entry.tv.TvEntryLayout

/** Forms and session state are shared; each device owns its artwork and form placement. */
@Composable
fun EntryScaffold(
    step: String,
    login: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalDeviceLayout.current.television) TvEntryLayout(step, content)
    else MobileEntryLayout(step, login, content)
}
