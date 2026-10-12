package dev.joyfin.app

import dev.joyfin.browse.CollectionUiState
import dev.joyfin.browse.DetailUiState
import dev.joyfin.browse.HomeUiState
import dev.joyfin.session.EntryState

enum class Page {
    CONNECT,
    LOGIN,
    RESTORE,
    HOME,
    LIBRARY,
    SEARCH,
    DETAIL,
    PERSON,
    SETTINGS,
}

/** Application routing and immutable per-feature snapshots for Back restoration. */
data class UiState(
    val page: Page = Page.CONNECT,
    val entry: EntryState = EntryState(),
    val home: HomeUiState = HomeUiState(),
    val collection: CollectionUiState = CollectionUiState(),
    val detail: DetailUiState = DetailUiState(),
    val settings: SettingsUiState = SettingsUiState(),
)

internal val Page.isEntry: Boolean
    get() = this in setOf(Page.CONNECT, Page.LOGIN, Page.RESTORE)
