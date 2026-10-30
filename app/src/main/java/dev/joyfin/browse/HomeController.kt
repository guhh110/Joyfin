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
    private var resumeActionJob: Job? = null
    private var resumeActionGeneration = 0L
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

    fun removeResumeItem(media: Media) = updateResumeItem(media, markPlayed = false)

    fun markResumeItemPlayed(media: Media) = updateResumeItem(media, markPlayed = true)

    private fun updateResumeItem(media: Media, markPlayed: Boolean) {
        if (mutable.value.page != Page.HOME || resumeActionJob?.isActive == true) return
        val owner = api.session ?: return
        val token = ++resumeActionGeneration
        val action = ResumeActionState(media.id, markPlayed, busy = true)
        mutable.value = mutable.value.copy(home = mutable.value.home.copy(resumeAction = action))
        resumeActionJob = scope.launch {
            try {
                if (markPlayed) api.setPlayed(media.id, true) else api.removeResume(media.id)
                ensureActive()
                if (token != resumeActionGeneration || api.session != owner || mutable.value.page != Page.HOME) return@launch
                // Discard an older in-flight resume response before removing the confirmed item.
                homeContentLoader.cancel(HomeRegion.RESUME)
                val home = mutable.value.home
                mutable.value = mutable.value.copy(home = home.copy(
                    content = home.content?.let { it.copy(resume = it.resume.filterNot { item -> item.id == media.id },
                        resumeLoad = it.resumeLoad.copy(loading = false)) },
                    resumeAction = action.copy(busy = false, completed = true)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ensureActive()
                if (token != resumeActionGeneration || api.session != owner || mutable.value.page != Page.HOME) return@launch
                if (e is ApiException && e.status == 401) expired()
                else {
                    val message = when {
                        e is ApiException && e.status in setOf(404, 405) && !markPlayed -> "服务器未能隐藏此条目，接口可能不受支持或作品已不存在；未改为已看"
                        e is ApiException && e.status == 403 -> "当前账号无权修改观看状态"
                        markPlayed -> "未能标为已看完，请检查网络后重试"
                        else -> "未能从继续观看中移除，请检查网络后重试"
                    }
                    mutable.value = mutable.value.copy(home = mutable.value.home.copy(
                        resumeAction = action.copy(busy = false, error = message)))
                }
            }
        }
    }

    fun cancelResumeAction(reconcile: Boolean = true) {
        val interrupted = mutable.value.home.resumeAction.busy
        resumeActionGeneration++
        resumeActionJob?.cancel()
        resumeActionJob = null
        mutable.value = mutable.value.copy(home = mutable.value.home.copy(resumeAction = ResumeActionState()))
        // Cancelling HTTP cannot undo a write the server has already accepted.
        if (interrupted && reconcile && mutable.value.page == Page.HOME && api.session != null)
            homeContentLoader.retry(HomeRegion.RESUME)
    }

    fun cancel() {
        cancelResumeAction(reconcile = false)
        homeLoader.cancel()
        homeContentLoader.cancel()
    }
}
