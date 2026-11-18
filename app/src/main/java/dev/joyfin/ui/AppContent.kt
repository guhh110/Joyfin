package dev.joyfin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.app.Page
import dev.joyfin.app.UiState
import dev.joyfin.app.isEntry
import dev.joyfin.ui.components.CinemaHeader
import dev.joyfin.ui.components.MobileBrowseNavigation
import dev.joyfin.ui.components.PageFadeHost
import dev.joyfin.ui.components.RequestStatus
import dev.joyfin.ui.components.cinemaBackground
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
    audioOutputMode: dev.joyfin.data.AudioOutputMode = dev.joyfin.data.AudioOutputMode.AUTO,
    homeLayout: dev.joyfin.data.HomeLayoutPreferences = dev.joyfin.data.HomeLayoutPreferences(),
    audit: dev.joyfin.audit.LibraryAuditController? = null,
    updates: dev.joyfin.update.AppUpdateController? = null,
    tracking: dev.joyfin.browse.TrackingUiState? = null,
    discovery: dev.joyfin.browse.TvHomeDiscoveryUiState = dev.joyfin.browse.TvHomeDiscoveryUiState(),
    interactionBlocked: Boolean = false,
) {
    val holder = rememberSaveableStateHolder()
    val tv = LocalDeviceLayout.current.television
    val entry = ui.page.isEntry
    val immersiveBrowse = ui.page in setOf(Page.HOME, Page.DETAIL)
    val showNavigation = !tv &&
        ui.page in setOf(Page.HOME, Page.TRACKING, Page.DETAIL, Page.LIBRARY, Page.SEARCH, Page.PERSON, Page.AUDIT)
    val pageKey =
        if (ui.page == Page.DETAIL) "detail:${ui.detail.visit}"
        else if (ui.page == Page.TRACKING) "tracking"
        else "${ui.page}:${ui.collection.parent}:${ui.collection.person?.id}"
    val initial =
        when (ui.page) {
            Page.CONNECT -> if (ui.entry.server.isBlank()) "auth:input" else "auth:submit"
            Page.LOGIN -> if (ui.entry.userName.isBlank()) "auth:input" else "auth:password"
            Page.RESTORE -> "auth:change-server"
            Page.HOME -> "hero:play"
            Page.TRACKING -> if (tv) "tracking:initial" else "header:tracking"
            Page.DETAIL -> if (!tv && ui.detail.entryType in setOf("Series", "Season", "Episode"))
                "header:back" else "detail:play"
            Page.LIBRARY, Page.PERSON -> if (tv) "collection:initial" else "header:back"
            Page.SEARCH -> "search:input"
            Page.SETTINGS -> if (tv) "settings:category:account" else "settings:back"
            Page.AUDIT -> "audit:back"
        }
    val visitedPages = remember { mutableSetOf<String>() }
    val isReturn = visitedPages.contains(pageKey)
    LaunchedEffect(pageKey) {
        visitedPages.add(pageKey)
    }

    val activeTab = when (ui.page) {
        Page.HOME -> "home"
        Page.TRACKING -> "tracking"
        Page.LIBRARY, Page.PERSON -> "library"
        Page.SEARCH -> "search"
        Page.SETTINGS -> "settings"
        else -> ""
    }

    val tvPageTitle = when (ui.page) {
        Page.HOME -> "私人放映厅"
        Page.TRACKING -> "我的追剧"
        Page.LIBRARY -> ui.collection.title.ifBlank { "媒体库" }
        Page.SEARCH -> "搜索"
        Page.DETAIL -> if (ui.detail.entryType == "Series") "剧集详情" else "电影详情"
        Page.PERSON -> ui.collection.person?.name?.ifBlank { "演职人员" } ?: "演职人员"
        Page.SETTINGS -> "设置"
        Page.AUDIT -> "片库体检"
        else -> "JOYFIN"
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .then(if (tv && !immersiveBrowse) Modifier.cinemaBackground() else Modifier)
            .then(
                if (LocalDeviceLayout.current.television || entry) Modifier
                else Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                )
            )
            .then(if (entry) Modifier else Modifier.imePadding())
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            key(pageKey) {
                holder.SaveableStateProvider(pageKey) {
                    RememberScreenFocus(initial) {
                        Column(Modifier.fillMaxSize()) {
                            if (tv && !entry && ui.page != Page.HOME) {
                                CinemaHeader(
                                    title = tvPageTitle,
                                    actions = actions,
                                    modifier = Modifier.padding(horizontal = LocalDeviceLayout.current.gutter, vertical = 6.dp),
                                    active = activeTab,
                                )
                            }
                            PageFadeHost(pageKey = pageKey, isReturn = isReturn) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .then(
                                            if (tv || entry || immersiveBrowse) Modifier
                                            else Modifier.statusBarsPadding()
                                        )
                                        .then(
                                            if (!showNavigation && !tv && !entry)
                                                Modifier.navigationBarsPadding()
                                            else Modifier
                                        )
                                ) {
                                    when (ui.page) {
                                        Page.CONNECT,
                                        Page.LOGIN ->
                                            ConnectionRoute(ui.entry, entryActions, login = ui.page == Page.LOGIN)
                                        Page.RESTORE -> SessionRestoreRoute(ui.entry, entryActions)
                                        Page.HOME -> HomeRoute(ui.home, actions, images, homeLayout, tracking, discovery, interactionBlocked)
                                        Page.TRACKING -> dev.joyfin.ui.features.tracking.TrackingRoute(
                                            tracking ?: dev.joyfin.browse.TrackingUiState(), actions, images)
                                        Page.DETAIL -> DetailRoute(ui.detail, actions, images)
                                        Page.LIBRARY -> LibraryRoute(ui.collection, actions, images)
                                        Page.PERSON -> PersonRoute(ui.collection, actions, images)
                                        Page.SEARCH -> SearchRoute(ui.collection, actions, images)
                                        Page.SETTINGS -> SettingsRoute(ui.entry, actions, autoNext, ui.settings, audioOutputMode,
                                            homeLayout, ui.home.content?.libraries.orEmpty(), updates)
                                        Page.AUDIT -> audit?.let {
                                            dev.joyfin.ui.features.audit.LibraryAuditRoute(it, actions, ui.home.content?.libraries.orEmpty())
                                        }
                                    }
                                }
                            }
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
        if (showNavigation) {
            MobileBrowseNavigation(actions, activeTab)
        }
    }
}
