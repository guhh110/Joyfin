package dev.joyfin.browse

import dev.joyfin.app.*
import dev.joyfin.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

internal class HomeController(
    private val scope: CoroutineScope,
    private val api: JellyfinApi,
    private val mutable: MutableStateFlow<UiState>,
    private val requests: PageRequests,
    private val expired: () -> Unit,
) {
    private val homeLoader =
        HomeLibraryLoader(
            scope,
            api::latestInLibrary,
            { rail ->
                if (mutable.value.page == Page.HOME)
                    mutable.value =
                        mutable.value.copy(
                            home =
                                mutable.value.home.copy(
                                    content =
                                        mutable.value.home.content?.let { home ->
                                            home.copy(
                                                rails =
                                                    home.rails.map {
                                                        if (it.library.id == rail.library.id) {
                                                            if (rail.loading || rail.error != null)
                                                                rail.copy(items = it.items)
                                                            else rail
                                                        } else it
                                                    }
                                            )
                                        }
                                )
                        )
            },
            expired,
        )
    private val homeContentLoader =
        HomeContentLoader(
            scope,
            { region ->
                when (region) {
                    HomeRegion.RESUME -> api.resumeVideo()
                    HomeRegion.LATEST -> api.recentVideo()
                    HomeRegion.LIBRARIES -> api.videoLibraries()
                }
            },
            { region, items, error ->
                if (mutable.value.page == Page.HOME) {
                    val now =
                        mutable.value.home.content ?: Home(emptyList(), emptyList(), emptyList())
                    val old =
                        when (region) {
                            HomeRegion.RESUME -> now.resumeLoad
                            HomeRegion.LATEST -> now.latestLoad
                            HomeRegion.LIBRARIES -> now.librariesLoad
                        }
                    val load =
                        old.copy(
                            loading = items == null && error == null,
                            error = error,
                            loaded = old.loaded || items != null,
                        )
                    val next =
                        when (region) {
                            HomeRegion.RESUME ->
                                now.copy(resume = items ?: now.resume, resumeLoad = load)
                            HomeRegion.LATEST ->
                                now.copy(latest = items ?: now.latest, latestLoad = load)
                            HomeRegion.LIBRARIES ->
                                now.copy(
                                    libraries = items ?: now.libraries,
                                    librariesLoad = load,
                                    rails =
                                        items?.map { library ->
                                            now.rails
                                                .find { it.library.id == library.id }
                                                ?.copy(library = library)
                                                ?: LibraryRailState(library)
                                        } ?: now.rails,
                                )
                        }
                    mutable.value =
                        mutable.value.copy(home = mutable.value.home.copy(content = next))
                    if (
                        region == HomeRegion.LIBRARIES &&
                            items != null &&
                            items.map { it.id } != now.libraries.map { it.id }
                    )
                        homeLoader.load(items)
                }
            },
            expired,
        )

    fun refreshHome(validate: Boolean = false): Unit =
        requests.run({ refreshHome(validate) }) {
            if (validate) api.validateSession()
            loadHome()
        }

    fun loadHome(enterHome: Boolean = false) {
        mutable.value =
            mutable.value.copy(
                page = if (enterHome) Page.HOME else mutable.value.page,
                home =
                    mutable.value.home.copy(
                        content =
                            mutable.value.home.content
                                ?: Home(emptyList(), emptyList(), emptyList())
                    ),
            )
        // Restart interrupted existing rails independently of a potentially failing directory
        // request.
        homeLoader.load(mutable.value.home.content?.libraries.orEmpty())
        homeContentLoader.load()
    }

    fun retryHome(region: HomeRegion) = homeContentLoader.retry(region)

    fun retryLibrary(id: String) {
        mutable.value.home.content?.libraries?.find { it.id == id }?.let(homeLoader::retry)
    }

    fun removeResumeItem(media: Media) {
        scope.launch {
            try {
                api.removeResume(media.id)
            } catch (e: Exception) {
                runCatching { api.setPlayed(media.id, true) }
            }
            val content = mutable.value.home.content ?: return@launch
            mutable.value = mutable.value.copy(
                home = mutable.value.home.copy(
                    content = content.copy(resume = content.resume.filter { it.id != media.id })
                )
            )
        }
    }

    fun markResumeItemPlayed(media: Media) {
        scope.launch {
            runCatching { api.setPlayed(media.id, true) }
            val content = mutable.value.home.content ?: return@launch
            mutable.value = mutable.value.copy(
                home = mutable.value.home.copy(
                    content = content.copy(resume = content.resume.filter { it.id != media.id })
                )
            )
        }
    }

    fun cancel() {
        homeLoader.cancel()
        homeContentLoader.cancel()
    }
}
