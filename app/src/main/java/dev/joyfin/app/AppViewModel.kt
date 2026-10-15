package dev.joyfin.app

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.joyfin.browse.*
import dev.joyfin.data.*
import dev.joyfin.session.EntryState
import dev.joyfin.session.SessionController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppViewModel @JvmOverloads constructor(
    application: Application,
    val store: SessionStore = SessionStore(application),
    val api: JellyfinApi = JellyfinApi(store.deviceId),
    storageContext: Context = application,
) : AndroidViewModel(application) {
    private val mutable =
        MutableStateFlow(
            UiState(entry = EntryState(server = store.server, serverName = store.serverName))
        )
    val state = mutable.asStateFlow()
    private val requests: PageRequests =
        PageRequests(viewModelScope, mutable, { api.session != null }, ::expireSession)
    private val homeController =
        HomeController(viewModelScope, api, mutable, requests, ::expireSession)
    private val collections = CollectionController(api, mutable, requests, ::push)
    private val details =
        DetailController(viewModelScope, api, mutable, requests, ::push, ::library, ::expireSession)
    private val session: SessionController =
        SessionController(
            api,
            store,
            mutable,
            requests,
            JellyfinApi::normalizeServer,
            {
                cancelSections()
                navigation.clearHistory()
            },
            { homeController.loadHome(enterHome = true) },
        )

    private val navigation: NavigationController =
        NavigationController(
            mutable,
            requests,
            ::cancelSections,
            { refreshHome() },
            ::retryRelated,
            details::restore,
            ::cancelEntry,
            session::backToConnection,
        )

    val subtitlePage = dev.joyfin.subtitles.SubtitlePageController(storageContext, viewModelScope, api, ::expireSession)

    init {
        session.start()
    }

    fun connect(address: String) = session.connect(address)

    fun login(name: String, password: String) = session.login(name, password)

    fun cancelEntry() = session.cancelEntry()

    fun changeServer() = session.changeServer()

    fun logout() = session.logout()

    fun expireSession(): Unit = session.expireSession()

    fun refreshHome(validate: Boolean = false) = homeController.refreshHome(validate)

    fun retryHome(region: HomeRegion) = homeController.retryHome(region)

    fun retryLibrary(id: String) = homeController.retryLibrary(id)

    fun library(id: String? = null, title: String = "媒体库") = collections.library(id, title)

    fun search() = collections.search()

    fun submitSearch(query: String) = collections.submitSearch(query)

    fun loadItems(more: Boolean = false) = collections.loadItems(more)

    fun openPerson(person: Person) = collections.openPerson(person)

    fun open(media: Media) = details.open(media)

    fun season(id: String) = details.season(id)

    fun selectEpisode(media: Media) = details.selectEpisode(media)

    fun retrySeriesSeasons() = details.retrySeriesSeasons()

    fun retrySeriesSeason() = details.retrySeriesSeason()

    fun retrySeriesEpisode() = details.retrySeriesEpisode()

    fun retrySeriesRecommendation() = details.retrySeriesRecommendation()

    fun retryRelated() = details.retryRelated()

    fun toggleFavorite() = details.toggleFavorite()

    fun prepareEpisode(media: Media, fromStart: Boolean, sourceId: String?, trigger: String) =
        details.prepareEpisode(media, fromStart, sourceId, trigger)

    fun cancelEpisodePlayback() = details.cancelEpisodePlayback()

    fun retryEpisodePlayback() = details.retryEpisodePlayback()

    fun consumeEpisodePlayback(sequence: Long) = details.consumeEpisodePlayback(sequence)

    fun retry() {
        requests.retry?.invoke()
    }

    private fun cancelSections() {
        homeController.cancel()
        details.cancel()
        subtitlePage.close()
    }

    fun home() = navigation.home()

    private fun push(page: Page): Unit = navigation.push(page)


    fun openSubtitles() {
        val detail = mutable.value.detail
        push(Page.SUBTITLES)
        subtitlePage.open(detail)
    }

    fun openSubtitleManager() {
        push(Page.SUBTITLES)
        subtitlePage.open()
    }

    fun settings() = navigation.settings()

    fun back(): Boolean {
        if (mutable.value.page == Page.SUBTITLES && subtitlePage.back()) return true
        return navigation.back()
    }

    fun refreshAfterPlayback() {
        if (api.session == null) return
        when (mutable.value.page) {
            Page.HOME -> refreshHome()
            Page.DETAIL -> details.refreshAfterPlayback()
            else -> Unit
        }
    }
}
