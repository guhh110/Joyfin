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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest

class AppViewModel @JvmOverloads constructor(
    application: Application,
    val store: SessionStore = SessionStore(application),
    val api: JellyfinApi = JellyfinApi(store.deviceId),
    storageContext: Context = application,
) : AndroidViewModel(application) {
    private val outputPreferences = AudioOutputPreferences(storageContext)
    val audioOutputMode = outputPreferences.changes.stateIn(viewModelScope, SharingStarted.Eagerly, outputPreferences.mode)
    private val mutable =
        MutableStateFlow(
            UiState(entry = EntryState(server = store.server, serverName = store.serverName))
        )
    val state = mutable.asStateFlow()
    private val homeLayoutStore = HomeLayoutStore(storageContext)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val homeLayout = state.map { api.session }.distinctUntilChanged()
        .flatMapLatest(homeLayoutStore::changes)
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeLayoutPreferences())
    fun setHomeLayout(value: HomeLayoutPreferences) = homeLayoutStore.save(api.session, value)
    private val requests: PageRequests =
        PageRequests(viewModelScope, mutable, { api.session != null }, ::expireSession)
    private val homeController =
        HomeController(viewModelScope, api, mutable, requests, ::expireSession)
    private val libraryBrowseStore = LibraryBrowseStore(storageContext)
    private val collections = CollectionController(api, mutable, requests, ::push,
        { libraryBrowseStore.load(api.session, it) },
        { parent, value -> libraryBrowseStore.save(api.session, parent, value) })
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
    val libraryAudit = dev.joyfin.audit.createLibraryAuditController(storageContext, viewModelScope, api, ::expireSession)
    val appUpdates = dev.joyfin.update.AppUpdateController(storageContext, viewModelScope)

    init {
        session.start()
    }

    fun connect(address: String) = session.connect(address)

    fun login(name: String, password: String) = session.login(name, password)

    fun quickLogin() = session.quickLogin()

    fun cancelEntry() = session.cancelEntry()

    fun setAudioOutputMode(mode: AudioOutputMode) { outputPreferences.mode = mode }

    fun changeServer() {
        dev.joyfin.playback.PlaybackDiagnostics.clear()
        session.changeServer()
    }

    fun logout() {
        dev.joyfin.playback.PlaybackDiagnostics.clear()
        session.logout()
    }

    fun expireSession(): Unit = session.expireSession()

    fun refreshHome(validate: Boolean = false) = homeController.refreshHome(validate)

    fun retryHome(region: HomeRegion) = homeController.retryHome(region)

    fun retryLibrary(id: String) = homeController.retryLibrary(id)

    fun library(id: String? = null, title: String = "媒体库") = collections.library(id, title)

    fun allMedia() = collections.library(title = "全部媒体", worksOnly = true)

    fun libraryWorks(id: String, title: String) = collections.library(id, title, worksOnly = true)

    fun arrangeLibrary(value: LibraryBrowseOptions) = collections.arrangeLibrary(value)

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

    fun toggleEpisodePlayed(media: Media) = details.toggleEpisodePlayed(media)

    fun removeResumeItem(media: Media) = homeController.removeResumeItem(media)

    fun markResumeItemPlayed(media: Media) = homeController.markResumeItemPlayed(media)

    fun cancelResumeAction() = homeController.cancelResumeAction()

    fun retry() {
        requests.retry?.invoke()
    }

    private fun cancelSections() {
        homeController.cancel()
        details.cancel()
        subtitlePage.close()
        libraryAudit.close()
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

    fun openLibraryAudit(id: String?, title: String) {
        push(Page.AUDIT)
        libraryAudit.open(dev.joyfin.audit.AuditScope(id, title))
    }

    fun openAuditItem(finding: dev.joyfin.audit.AuditFinding) {
        libraryAudit.media(finding)?.let(details::open)
    }

    fun auditSubtitles(finding: dev.joyfin.audit.AuditFinding) {
        val media = libraryAudit.media(finding) ?: return
        push(Page.SUBTITLES)
        subtitlePage.openForAudit(media)
    }

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
