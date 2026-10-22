package dev.joyfin.browse

import dev.joyfin.data.*
import kotlinx.coroutines.*

/** Independent resources; only visit/season/episode owners can publish their own response. */
class SeriesDetailController(
    private val scope: CoroutineScope,
    private val episodes: suspend (String, String?) -> List<Media>,
    private val item: suspend (String) -> Media,
    private val current: () -> SeriesDetailState?,
    private val publish: (SeriesDetailState) -> Unit,
    private val expired: () -> Unit,
    private val seasons: suspend (String) -> List<Media> = { emptyList() },
    private val progress: suspend (String) -> List<Media> = { episodes(it, null) },
) {
    private var visit = 0L
    private var seasonVersion = 0L
    private var episodeVersion = 0L
    private var seasonsJob: Job? = null
    private var recommendationJob: Job? = null
    private var seasonJob: Job? = null
    private var episodeJob: Job? = null

    fun cancel() {
        visit++
        seasonVersion++
        episodeVersion++
        seasonsJob?.cancel()
        recommendationJob?.cancel()
        seasonJob?.cancel()
        episodeJob?.cancel()
    }

    fun initialize(
        seriesId: String,
        entrySeasonId: String? = null,
        entryEpisodeId: String? = null,
    ) {
        cancel()
        publish(SeriesDetailState(seriesId, entrySeasonId, entryEpisodeId))
        loadSeasons()
        recommend()
    }

    fun loadSeasons() {
        val before = current() ?: return
        val owner = visit
        seasonsJob?.cancel()
        publish(before.copy(seasons = before.seasons.refreshing()))
        seasonsJob =
            scope.launch {
                try {
                    val list = seasons(before.seriesId)
                    ensureActive()
                    val now = owned(owner, before.seriesId) ?: return@launch
                    publish(now.copy(seasons = SeriesResource.Ready(list)))
                    // A user-selected season always wins over late initialization.
                    if (now.seasonId != null) return@launch
                    val entrySeason =
                        try {
                            before.entrySeasonId ?: before.entryEpisodeId?.let { item(it).seasonId }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            ensureActive()
                            val latest = owned(owner, before.seriesId) ?: return@launch
                            if (auth(e)) return@launch
                            if (latest.seasonId == null)
                                publish(latest.copy(entryError = e.message ?: "入口指定的集无法定位"))
                            return@launch
                        }
                    ensureActive()
                    val latest = owned(owner, before.seriesId) ?: return@launch
                    if (latest.seasonId != null) return@launch
                    val target =
                        if (entrySeason != null) list.find { it.id == entrySeason }
                        else
                            list.find { it.id == latest.recommendation.data?.seasonId }
                                ?: list.firstOrNull()
                    if (entrySeason != null && target == null)
                        publish(latest.copy(entryError = "入口指定的季已不存在，请选择其他季"))
                    else target?.let { season(it.id, before.entryEpisodeId) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ensureActive()
                    val now = owned(owner, before.seriesId) ?: return@launch
                    if (!auth(e))
                        publish(now.copy(seasons = now.seasons.failed(e.message ?: "读取季列表失败")))
                }
            }
    }

    fun recommend() {
        val before = current() ?: return
        val owner = visit
        recommendationJob?.cancel()
        publish(before.copy(recommendation = before.recommendation.refreshing()))
        recommendationJob =
            scope.launch {
                try {
                    // Use the whole series' viewing history, independently of the season being browsed.
                    val all = progress(before.seriesId)
                    ensureActive()
                    val now = owned(owner, before.seriesId) ?: return@launch
                    val byId = all.associateBy { it.id }
                    fun Media.refreshed(): Media = byId[id]?.let { fresh ->
                        // Progress responses deliberately omit artwork, sources and descriptive metadata.
                        copy(resumeTicks = fresh.resumeTicks, played = fresh.played,
                            lastPlayedAt = fresh.lastPlayedAt, favorite = fresh.favorite)
                    } ?: this
                    val updated = now.episodes.data?.map { it.refreshed() }
                    publish(
                        now.copy(
                            recommendation = SeriesResource.Ready(preferredEpisode(all)),
                            episodes =
                                updated?.let {
                                    (now.episodes as SeriesResource.Ready).copy(value = it)
                                } ?: now.episodes,
                            episodeDetails =
                                now.episodeDetails.data?.let { detail ->
                                    (now.episodeDetails as SeriesResource.Ready).copy(
                                        value = detail.refreshed()
                                    )
                                } ?: now.episodeDetails,
                            seasonCache =
                                now.seasonCache.mapValues { (_, list) ->
                                    list.map { it.refreshed() }
                                },
                        )
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ensureActive()
                    val now = owned(owner, before.seriesId) ?: return@launch
                    if (!auth(e))
                        publish(
                            now.copy(
                                recommendation = now.recommendation.failed(e.message ?: "定位续看失败")
                            )
                        )
                }
            }
    }

    fun season(id: String, preferredId: String? = null) {
        val before = current() ?: return
        seasonJob?.cancel()
        episodeJob?.cancel()
        val owner = visit
        val version = ++seasonVersion
        episodeVersion++
        val remembered = preferredId ?: before.remembered[id]
        val cached = before.seasonCache[id]
        val selected =
            cached?.find { it.id == remembered }
                ?: cached?.firstOrNull()?.takeIf { preferredId == null }
        publish(
            before.copy(
                seasonId = id,
                episodes =
                    cached?.let { SeriesResource.Ready(it, SeriesResource.Refresh.Running) }
                        ?: SeriesResource.Loading,
                inspectedEpisodeId = selected?.id,
                requestedEpisodeId = preferredId,
                episodeDetails = SeriesResource.Idle,
                entryError = null,
            )
        )
        selected?.let(::episode)
        seasonJob =
            scope.launch {
                try {
                    val list = episodes(before.seriesId, id).sortedBy { it.episode }
                    ensureActive()
                    val now =
                        owned(owner, before.seriesId)?.takeIf {
                            it.seasonId == id && version == seasonVersion
                        } ?: return@launch
                    // Preserve browsing performed while cached data was refreshing.
                    val targetId = now.inspectedEpisodeId ?: remembered
                    val target =
                        list.find { it.id == targetId }
                            ?: if (preferredId == null) list.firstOrNull() else null
                    publish(
                        now.copy(
                            episodes = SeriesResource.Ready(list),
                            inspectedEpisodeId = target?.id,
                            episodeDetails =
                                if (target?.id == now.inspectedEpisodeId) now.episodeDetails
                                else SeriesResource.Idle,
                            entryError =
                                if (preferredId != null && list.none { it.id == preferredId })
                                    "入口指定的集已不存在，请选择其他集"
                                else now.entryError,
                            seasonCache =
                                (now.seasonCache - id + (id to list))
                                    .entries
                                    .toList()
                                    .takeLast(8)
                                    .associate { it.toPair() },
                        )
                    )
                    if (
                        target != null &&
                            (target.id != now.inspectedEpisodeId ||
                                now.episodeDetails is SeriesResource.Idle)
                    )
                        episode(target)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ensureActive()
                    val now =
                        owned(owner, before.seriesId)?.takeIf {
                            it.seasonId == id && version == seasonVersion
                        } ?: return@launch
                    if (!auth(e))
                        publish(now.copy(episodes = now.episodes.failed(e.message ?: "读取本季失败")))
                }
            }
    }

    fun reloadEpisode() {
        val now = current() ?: return
        val media = now.inspected ?: return
        episode(media, force = true)
    }

    fun episode(media: Media) = episode(media, force = false)

    private fun episode(media: Media, force: Boolean) {
        val before = current() ?: return
        if (before.episodes.data.orEmpty().none { it.id == media.id }) return
        if (before.inspectedEpisodeId == media.id && before.episodeDetails.loading) return
        if (
            !force &&
                before.inspectedEpisodeId == media.id &&
                before.episodeDetails is SeriesResource.Ready &&
                before.episodeDetails.error == null
        )
            return
        episodeJob?.cancel()
        val owner = visit
        val version = ++episodeVersion
        publish(
            before.copy(
                inspectedEpisodeId = media.id,
                requestedEpisodeId = null,
                entryError = null,
                episodeDetails =
                    if (before.inspectedEpisodeId == media.id) before.episodeDetails.refreshing()
                    else SeriesResource.Loading,
                remembered = before.remembered + (before.seasonId.orEmpty() to media.id),
            )
        )
        episodeJob =
            scope.launch {
                try {
                    delay(150) // Direction-key repeat should not fetch every passing row.
                    val detail = item(media.id)
                    ensureActive()
                    val now =
                        owned(owner, before.seriesId)?.takeIf {
                            it.inspectedEpisodeId == media.id && version == episodeVersion
                        } ?: return@launch
                    require(detail.id == media.id && detail.type == "Episode") { "本集信息不匹配" }
                    publish(now.copy(episodeDetails = SeriesResource.Ready(detail)))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ensureActive()
                    val now =
                        owned(owner, before.seriesId)?.takeIf {
                            it.inspectedEpisodeId == media.id && version == episodeVersion
                        } ?: return@launch
                    if (!auth(e))
                        publish(
                            now.copy(
                                episodeDetails = now.episodeDetails.failed(e.message ?: "读取本集信息失败")
                            )
                        )
                }
            }
    }

    fun restore() {
        val now = current() ?: return
        if (now.seasons.loading) loadSeasons()
        if (now.recommendation.loading) recommend()
        if (now.episodes.loading) now.seasonId?.let { season(it, now.requestedEpisodeId) }
        else if (now.episodeDetails.loading)
            now.inspected?.let {
                publish(now.copy(episodeDetails = SeriesResource.Idle))
                episode(it)
            }
    }

    private fun owned(owner: Long, id: String) =
        current()?.takeIf { owner == visit && it.seriesId == id }

    private fun auth(e: Exception): Boolean {
        if (e is ApiException && e.status == 401) {
            expired()
            return true
        }
        return false
    }
}
