package dev.joyfin.app

import dev.joyfin.browse.*
import kotlinx.coroutines.flow.MutableStateFlow

/** Saves page snapshots and safe retry commands, independently of Android lifecycle and APIs. */
internal class NavigationController(
    private val mutable: MutableStateFlow<UiState>,
    private val requests: PageRequests,
    private val cancelSections: () -> Unit,
    private val refreshHome: () -> Unit,
    private val retryRelated: () -> Unit,
    private val restoreDetail: () -> Unit,
    private val cancelEntry: () -> Unit,
    private val backToConnection: () -> Unit,
) {
    private val history = ArrayDeque<Pair<UiState, (() -> Unit)?>>()

    fun clearHistory() = history.clear()

    fun home() {
        requests.cancel()
        cancelSections()
        history.clear()
        mutable.value =
            mutable.value.copy(
                page = Page.HOME,
                home = mutable.value.home.copy(busy = false, error = null),
                collection = CollectionUiState(),
                detail = DetailUiState(),
            )
        refreshHome()
    }

    fun push(page: Page) {
        requests.cancel()
        cancelSections()
        history.addLast(mutable.value to requests.retry)
        val next = mutable.value.copy(page = page)
        mutable.value =
            when (page) {
                Page.LIBRARY ->
                    next.copy(collection = CollectionUiState(kind = CollectionKind.LIBRARY))
                Page.SEARCH ->
                    next.copy(collection = CollectionUiState(kind = CollectionKind.SEARCH))
                Page.PERSON ->
                    next.copy(collection = CollectionUiState(kind = CollectionKind.PERSON))
                Page.DETAIL -> next.copy(detail = DetailUiState())
                Page.SETTINGS -> next.copy(settings = SettingsUiState())
                else -> next.withActiveRequest(busy = false, error = null)
            }
    }

    fun settings() = push(Page.SETTINGS)

    fun back(): Boolean {
        if (mutable.value.page.isEntry && mutable.value.entry.busy) {
            cancelEntry()
            return true
        }
        requests.cancel()
        cancelSections()
        if (history.isNotEmpty()) {
            val (restored, restoredRetry) = history.removeLast()
            requests.retry = restoredRetry
            mutable.value =
                restored.withActiveRequest(busy = false, error = restored.activeRequest.error)
            if (restored.page == Page.HOME) refreshHome()
            else if (
                restored.activeRequest.busy &&
                    (restored.page != Page.DETAIL || !restored.detail.ready)
            )
                restoredRetry?.invoke()
            else if (restored.page == Page.DETAIL) {
                if (restored.detail.relatedLoad.loading) retryRelated()
                restoreDetail()
            } else if (restored.activeRequest.busy) restoredRetry?.invoke()
            return true
        }
        if (mutable.value.page == Page.LOGIN) {
            backToConnection()
            return true
        }
        return false
    }
}
