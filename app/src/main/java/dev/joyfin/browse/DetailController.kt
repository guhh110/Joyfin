package dev.joyfin.browse

import dev.joyfin.app.*
import dev.joyfin.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

internal class DetailController(
    private val scope: CoroutineScope,
    private val api: JellyfinApi,
    private val mutable: MutableStateFlow<UiState>,
    private val requests: PageRequests,
    private val push: (Page) -> Unit,
    private val library: (String?, String) -> Unit,
    private val expired: () -> Unit,
) {
    private var detailSequence = 0L
    private var favoriteJob: Job? = null
    private var relatedJob: Job? = null
    private val seriesController =
        SeriesDetailController(
            scope,
            api::episodes,
            api::item,
            { mutable.value.detail.series.takeIf { mutable.value.page == Page.DETAIL } },
            { mutable.value = mutable.value.copy(detail = mutable.value.detail.copy(series = it)) },
            expired,
            api::seasons,
            api::episodeProgress,
            api::setPlayed,
        )

    private val episodePlayback =
        EpisodePlaybackController(
            scope,
            api::item,
            { playback ->
                mutable.value.detail.series?.let { series ->
                    mutable.value =
                        mutable.value.copy(
                            detail =
                                mutable.value.detail.copy(series = series.copy(playback = playback))
                        )
                }
            },
            expired,
        )

    fun prepareEpisode(media: Media, fromStart: Boolean, sourceId: String?, trigger: String) =
        episodePlayback.play(media, fromStart, sourceId, trigger)

    fun cancelEpisodePlayback() = episodePlayback.cancel()

    fun retryEpisodePlayback() = episodePlayback.retry()

    fun consumeEpisodePlayback(sequence: Long) = episodePlayback.consumed(sequence)

    fun selectEpisode(media: Media) = seriesController.episode(media)

    fun retrySeriesSeasons() = seriesController.loadSeasons()

    fun retrySeriesSeason() {
        mutable.value.detail.series?.seasonId?.let { seriesController.season(it) }
    }

    fun retrySeriesEpisode() {
        seriesController.reloadEpisode()
    }

    fun retrySeriesRecommendation() = seriesController.recommend()

    fun open(media: Media) {
        if (media.isFolder && media.type !in setOf("Series", "Season")) {
            library(media.id, media.name)
            return
        }
        push(Page.DETAIL)
        mutable.value =
            mutable.value.copy(
                detail =
                    mutable.value.detail.copy(
                        item = media.detailPreview(),
                        entryType = media.type,
                        visit = ++detailSequence,
                        ready = false,
                        relatedLoad = RegionLoad(),
                        series = null,
                        related = emptyList(),
                    )
            )
        resolveDetail(media)
    }

    private fun resolveDetail(entry: Media): Unit =
        requests.run({ resolveDetail(entry) }) {
            val resolved =
                if (entry.type in setOf("Episode", "Season")) resolveSeriesEntry(entry, api::item)
                else entry
            val seriesId = resolved.seriesId.ifBlank { entry.seriesId }
            if (entry.type in setOf("Episode", "Season"))
                require(seriesId.isNotBlank()) { "服务器未提供所属剧集，请重试" }
            currentCoroutineContext().ensureActive()
            val detailId = if (entry.type in setOf("Episode", "Season")) seriesId else entry.id
            val seriesEntry = entry.type in setOf("Series", "Season", "Episode")
            // The known series identity is sufficient for progress and seasons. Do not wait for
            // the full hero/people metadata before making the primary play action available.
            if (seriesEntry) initializeSeries(entry, resolved, detailId)
            val detail = api.item(detailId)
            if (entry.type in setOf("Episode", "Season"))
                require(detail.type == "Series") { "所属剧集信息异常，请重试" }
            currentCoroutineContext().ensureActive()
            mutable.value =
                mutable.value.copy(detail = mutable.value.detail.copy(item = detail, ready = true))
            if (detail.type == "Series" && !seriesEntry) initializeSeries(entry, resolved, detail.id)
            loadRelated(detail.id)
        }

    private fun initializeSeries(entry: Media, resolved: Media, seriesId: String) {
        seriesController.initialize(
            seriesId,
            if (entry.type == "Season") entry.id
            else resolved.seasonId.takeIf { entry.type == "Episode" && it.isNotBlank() },
            entry.id.takeIf { entry.type == "Episode" },
        )
    }

    fun retryRelated() {
        mutable.value.detail.item?.let { loadRelated(it.id) }
    }

    private fun loadRelated(id: String) {
        relatedJob?.cancel()
        mutable.value =
            mutable.value.copy(
                detail = mutable.value.detail.copy(relatedLoad = RegionLoad(loading = true))
            )
        relatedJob =
            scope.launch {
                try {
                    val items = api.similar(id)
                    currentCoroutineContext().ensureActive()
                    if (mutable.value.detail.item?.id == id)
                        mutable.value =
                            mutable.value.copy(
                                detail =
                                    mutable.value.detail.copy(
                                        related = items,
                                        relatedLoad = RegionLoad(loaded = true),
                                    )
                            )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (e is ApiException && e.status == 401) expired()
                    else if (mutable.value.detail.item?.id == id)
                        mutable.value =
                            mutable.value.copy(
                                detail =
                                    mutable.value.detail.copy(
                                        relatedLoad = RegionLoad(error = e.message ?: "读取相似内容失败")
                                    )
                            )
                }
            }
    }

    fun season(id: String) = seriesController.season(id)

    fun toggleEpisodePlayed(media: Media) = seriesController.togglePlayed(media)

    fun toggleFavorite() {
        val item = mutable.value.detail.item ?: return
        if (favoriteJob?.isActive == true) return
        val visit = mutable.value.detail.visit
        mutable.value =
            mutable.value.copy(
                detail = mutable.value.detail.copy(favoriteBusy = true, error = null)
            )
        favoriteJob =
            scope.launch {
                try {
                    api.favorite(item.id, !item.favorite)
                    currentCoroutineContext().ensureActive()
                    if (mutable.value.detail.item?.id == item.id)
                        mutable.value =
                            mutable.value.copy(
                                detail =
                                    mutable.value.detail.copy(
                                        item =
                                            mutable.value.detail.item?.copy(
                                                favorite = !item.favorite
                                            )
                                    )
                            )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (e is ApiException && e.status == 401) expired()
                    else {
                        requests.retry = ::toggleFavorite
                        mutable.value =
                            mutable.value.copy(
                                detail = mutable.value.detail.copy(error = e.message ?: "收藏失败，请重试")
                            )
                    }
                } finally {
                    if (isActive && mutable.value.detail.visit == visit)
                        mutable.value =
                            mutable.value.copy(
                                detail = mutable.value.detail.copy(favoriteBusy = false)
                            )
                }
            }
    }

    fun cancel() {
        episodePlayback.cancel()
        seriesController.cancel()
        favoriteJob?.cancel()
        mutable.value = mutable.value.copy(detail = mutable.value.detail.copy(favoriteBusy = false))
        relatedJob?.cancel()
    }

    fun restore() = seriesController.restore()

    fun refreshAfterPlayback() {
        val current = mutable.value.detail
        if (current.series != null) seriesController.recommend()
        else current.item?.let { resolveDetail(it) }
    }
}
