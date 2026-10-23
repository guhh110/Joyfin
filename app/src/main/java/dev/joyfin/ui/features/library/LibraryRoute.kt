package dev.joyfin.ui.features.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import dev.joyfin.browse.CollectionUiState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.features.collection.MediaCollection
import dev.joyfin.ui.features.collection.directoryGrid
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.BrowseActions
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.LocalScreenFocus
import dev.joyfin.ui.features.library.mobile.MobileLibraryControls
import dev.joyfin.ui.features.library.tv.TvLibraryControls
import kotlinx.coroutines.flow.first

@Composable
fun LibraryRoute(ui: CollectionUiState, actions: BrowseActions, images: ArtworkSource) {
    var panel by remember(ui.parent, ui.worksOnly) { mutableStateOf<LibraryOptionsPanel?>(null) }
    var restoreTarget by remember { mutableStateOf<String?>(null) }
    val tv = LocalDeviceLayout.current.television
    val focus = LocalScreenFocus.current
    val window = LocalWindowInfo.current
    LaunchedEffect(panel) {
        val target = restoreTarget
        if (panel == null && target != null && tv) {
            snapshotFlow { window.isWindowFocused }.first { it }
            withFrameNanos {}
            focus?.rememberTarget?.invoke(target)
            focus?.requesters?.get(target)?.requestFocus()
            restoreTarget = null
        }
    }
    MediaCollection(ui, actions, images) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppHeader(actions, true, horizontalInset = 0.dp)
            FilmTitle(ui.title.ifBlank { "媒体库" })
            Caption(if ((ui.busy || ui.error != null) && ui.media.isEmpty()) " "
                else if (ui.directoryGrid) "${ui.total} 个媒体库" else "${ui.total} 部作品")
            if (ui.canArrangeLibrary) {
                val sort = { restoreTarget = "library:sort"; panel = LibraryOptionsPanel.SORT }
                val watched = { restoreTarget = "library:watched"; panel = LibraryOptionsPanel.WATCHED }
                if (tv) TvLibraryControls(ui.libraryOptions, sort, watched)
                else MobileLibraryControls(ui.libraryOptions, sort, watched)
            }
        }
    }
    panel?.let { LibraryOptionsSheet(it, ui.libraryOptions, actions.arrangeLibrary) { panel = null } }
}
