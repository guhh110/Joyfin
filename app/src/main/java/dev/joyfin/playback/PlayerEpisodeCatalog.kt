package dev.joyfin.playback

import dev.joyfin.data.ApiException
import dev.joyfin.data.Media
import kotlinx.coroutines.*

internal data class PlayerEpisodeCatalogState(
    val seasons: List<Media> = emptyList(),
    val episodes: List<Media> = emptyList(),
    val seasonId: String? = null,
    val loading: Boolean = false,
    val seasonsLoading: Boolean = false,
    val error: String? = null,
    val seasonsError: String? = null,
)

/** Player-session cache: bounded by season, with stale data visible during refresh. */
internal class PlayerEpisodeCatalog(
    private val scope: CoroutineScope,
    private val loadSeasons: suspend (String) -> List<Media>,
    private val loadEpisodes: suspend (String, String?) -> List<Media>,
    private val publish: (PlayerEpisodeCatalogState) -> Unit,
    private val expired: () -> Unit,
    private val now: () -> Long,
) {
    private data class Key(val series: String, val season: String?)
    private data class Entry(val items: List<Media>, val time: Long)
    private val cache = LinkedHashMap<Key, Entry>(8, .75f, true)
    private var seasonsCache: Entry? = null
    private var series = ""
    private var generation = 0L
    private var selection = 0L
    private var seasonsJob: Job? = null
    private var episodesJob: Job? = null
    var state = PlayerEpisodeCatalogState()
        private set

    private fun update(value: PlayerEpisodeCatalogState) { state = value; publish(value) }
    private fun Entry.fresh() = now() - time in 0 until 60_000L

    fun suspendRequests() {
        generation++; selection++
        seasonsJob?.cancel(); episodesJob?.cancel()
        update(state.copy(loading = false, seasonsLoading = false))
    }

    fun open(media: Media) {
        suspendRequests()
        val nextSeries = media.seriesId.takeIf { media.type == "Episode" }.orEmpty()
        if (series != nextSeries) { cache.clear(); seasonsCache = null }
        series = nextSeries
        if (series.isBlank()) { update(PlayerEpisodeCatalogState()); return }
        update(PlayerEpisodeCatalogState(seasons = seasonsCache?.items.orEmpty()))
        refreshSeasons()
        select(media.seasonId.takeIf { it.isNotBlank() })
    }

    fun select(seasonId: String?, force: Boolean = false) {
        if (series.isBlank()) return
        if (!force && state.seasonId == seasonId && episodesJob?.isActive == true) return
        episodesJob?.cancel()
        val ticket = ++selection
        val owner = generation
        val key = Key(series, seasonId)
        val saved = cache[key]
        val fetch = force || saved?.fresh() != true
        update(state.copy(seasonId = seasonId, episodes = saved?.items.orEmpty(), loading = fetch, error = null))
        if (!fetch) return
        episodesJob = scope.launch {
            try {
                val rows = loadEpisodes(key.series, key.season)
                ensureActive()
                if (owner != generation || ticket != selection) return@launch
                cache[key] = Entry(rows, now())
                while (cache.size > 6) cache.remove(cache.keys.first())
                update(state.copy(episodes = rows, loading = false))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                ensureActive()
                if (owner != generation || ticket != selection) return@launch
                if (e is ApiException && e.status == 401) expired()
                else update(state.copy(loading = false, error = if (saved == null)
                    "暂时无法读取剧集，请重试" else "更新失败，仍可选择已加载剧集"))
            }
        }
    }

    fun retry() {
        select(state.seasonId, force = true)
        if (state.seasonsError != null) refreshSeasons(force = true)
    }

    fun markWatched(id: String) {
        cache.entries.forEach { entry ->
            if (entry.value.items.any { it.id == id }) entry.setValue(entry.value.copy(
                items = entry.value.items.map { if (it.id == id) it.copy(played = true) else it }))
        }
        update(state.copy(episodes = state.episodes.map { if (it.id == id) it.copy(played = true) else it }))
    }

    private fun refreshSeasons(force: Boolean = false) {
        val saved = seasonsCache
        if (!force && saved?.fresh() == true) return
        val owner = generation
        val id = series
        seasonsJob?.cancel()
        update(state.copy(seasonsLoading = true, seasonsError = null))
        seasonsJob = scope.launch {
            try {
                val rows = loadSeasons(id)
                ensureActive()
                if (owner != generation) return@launch
                seasonsCache = Entry(rows, now())
                update(state.copy(seasons = rows, seasonsLoading = false))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                ensureActive()
                if (owner != generation) return@launch
                if (e is ApiException && e.status == 401) expired()
                else update(state.copy(seasonsLoading = false, seasonsError = "暂时无法读取季信息，请重试"))
            }
        }
    }
}
