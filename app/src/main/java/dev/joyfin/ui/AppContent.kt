package dev.joyfin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.app.Page
import dev.joyfin.app.UiState
import dev.joyfin.ui.components.RequestStatus
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.detail.DetailRoute
import dev.joyfin.ui.features.entry.ConnectionRoute
import dev.joyfin.ui.features.entry.EntryActions
import dev.joyfin.ui.features.entry.SessionRestoreRoute
import dev.joyfin.ui.features.home.HomeRoute
import dev.joyfin.ui.features.library.LibraryRoute
import dev.joyfin.ui.features.person.PersonRoute
import dev.joyfin.ui.features.search.SearchRoute
import dev.joyfin.ui.features.settings.SettingsRoute
import dev.joyfin.ui.interaction.RememberScreenFocus
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.BrowseActions
import dev.joyfin.ui.theme.Palette

/** UI-only routing. Per-page scroll and focus survive Back without owning the business history. */
@Composable
fun AppContent(
    ui: UiState,
    actions: BrowseActions,
    images: ArtworkSource,
    autoNext: Boolean,
    entryActions: EntryActions,
) {
    val holder = rememberSaveableStateHolder()
    val entry = ui.page in setOf(Page.CONNECT, Page.LOGIN, Page.RESTORE)
    val immersiveSeries =
        ui.page == Page.DETAIL &&
            (ui.detail.item?.type == "Series" ||
                ui.detail.entryType in setOf("Series", "Season", "Episode"))
    val pageKey =
        if (ui.page == Page.DETAIL) "detail:${ui.detail.visit}"
        else "${ui.page}:${ui.collection.parent}:${ui.collection.person?.id}"
    val initial =
        when (ui.page) {
            Page.CONNECT -> if (ui.entry.server.isBlank()) "auth:input" else "auth:submit"
            Page.LOGIN -> if (ui.entry.userName.isBlank()) "auth:input" else "auth:password"
            Page.RESTORE -> "auth:change-server"
            Page.HOME -> "hero:play"
            Page.DETAIL ->
                if (ui.detail.entryType in setOf("Series", "Season", "Episode")) "header:back"
                else "detail:play"
            Page.SEARCH -> "search:input"
            Page.SETTINGS -> "settings:back"
            else -> "header:back"
        }
    Box(
        Modifier.fillMaxSize()
            .background(Palette.Background)
            .then(
                if (LocalDeviceLayout.current.television || entry) Modifier
                else if (immersiveSeries)
                    Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                        )
                    )
                else Modifier.safeDrawingPadding()
            )
            .then(if (entry) Modifier else Modifier.imePadding())
    ) {
        key(pageKey) {
            holder.SaveableStateProvider(pageKey) {
                RememberScreenFocus(initial) {
                    when (ui.page) {
                        Page.CONNECT,
                        Page.LOGIN ->
                            ConnectionRoute(ui.entry, entryActions, login = ui.page == Page.LOGIN)
                        Page.RESTORE -> SessionRestoreRoute(ui.entry, entryActions)
                        Page.HOME -> HomeRoute(ui.home, actions, images)
                        Page.DETAIL -> DetailRoute(ui.detail, actions, images)
                        Page.LIBRARY -> LibraryRoute(ui.collection, actions, images)
                        Page.PERSON -> PersonRoute(ui.collection, actions, images)
                        Page.SEARCH -> SearchRoute(ui.collection, actions, images)
                        Page.SETTINGS -> SettingsRoute(ui.entry, actions, autoNext, ui.settings)
                    }
                }
            }
        }
        if (!entry && ui.page == Page.SETTINGS)
            RequestStatus(
                ui.settings.busy,
                ui.settings.error,
                actions.retry,
                Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
    }
}
