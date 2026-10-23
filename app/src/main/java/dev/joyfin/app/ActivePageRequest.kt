package dev.joyfin.app

internal data class ActivePageRequest(val busy: Boolean, val error: String?)

internal val UiState.activeRequest: ActivePageRequest
    get() =
        when (page) {
            Page.CONNECT,
            Page.LOGIN,
            Page.RESTORE -> ActivePageRequest(entry.busy, entry.error)
            Page.HOME -> ActivePageRequest(home.busy, home.error)
            Page.LIBRARY,
            Page.SEARCH,
            Page.PERSON -> ActivePageRequest(collection.busy, collection.error)
            Page.DETAIL -> ActivePageRequest(detail.busy, detail.error)
            Page.SETTINGS -> ActivePageRequest(settings.busy, settings.error)
            Page.SUBTITLES, Page.AUDIT -> ActivePageRequest(false, null)
        }

internal fun UiState.withActiveRequest(busy: Boolean, error: String?): UiState =
    when (page) {
        Page.CONNECT,
        Page.LOGIN,
        Page.RESTORE -> copy(entry = entry.copy(busy = busy, error = error))
        Page.HOME -> copy(home = home.copy(busy = busy, error = error))
        Page.LIBRARY,
        Page.SEARCH,
        Page.PERSON -> copy(collection = collection.copy(busy = busy, error = error))
        Page.DETAIL -> copy(detail = detail.copy(busy = busy, error = error))
        Page.SETTINGS -> copy(settings = settings.copy(busy = busy, error = error))
        Page.SUBTITLES, Page.AUDIT -> this
    }
