package dev.joyfin.playback

import dev.joyfin.data.*
import kotlinx.coroutines.*

internal data class NextEpisodeState(
    val next: Media? = null,
    val loading: Boolean = false,
    val prepared: Boolean = false,
    val error: String? = null,
)

/** Metadata only: never opens a stream or negotiates a server playback session in advance. */
internal class NextEpisodePreparation(
    private val scope: CoroutineScope,
    private val findNext: suspend (Media) -> Media?,
    private val fetchItem: suspend (String) -> Media,
    private val publish: (NextEpisodeState) -> Unit,
    private val expired: () -> Unit,
    private val now: () -> Long,
) {
    private data class Owner(val media: Media, val source: String)
    private var owner: Owner? = null
    private var generation = 0L
    private var discovery: Job? = null
    private var preparation: Job? = null
    private var discovered = false
    private var attempted = false
    private var cached: Media? = null
    private var cachedAt = 0L
    var state = NextEpisodeState()
        private set

    private fun update(value: NextEpisodeState) { state = value; publish(value) }

    fun clear() {
        generation++
        discovery?.cancel(); discovery = null
        preparation?.cancel(); preparation = null
        owner = null; discovered = false; attempted = false; cached = null
        update(NextEpisodeState())
    }

    fun open(media: Media, source: String) {
        if (owner?.let { it.media.id == media.id && it.source == source } == true) return
        clear()
        if (media.type == "Episode" && media.seriesId.isNotBlank()) owner = Owner(media, source)
    }

    fun retain(mediaId: String, source: String?) {
        val current = owner ?: return
        if (current.media.id != mediaId || source != null && current.source != source) clear()
    }

    /** Called after READY, independently of the season currently browsed in the selection panel. */
    fun start() {
        val current = owner ?: return
        if (discovered) return
        discovered = true
        val token = generation
        update(state.copy(loading = true, error = null))
        discovery = scope.launch {
            try {
                val next = findNext(current.media)
                ensureActive()
                if (token != generation) return@launch
                val valid = next?.takeIf { it.id != current.media.id && it.type == "Episode" &&
                    (it.seriesId.isBlank() || it.seriesId == current.media.seriesId) }
                update(NextEpisodeState(next = valid))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                ensureActive()
                if (token != generation) return@launch
                if (e is ApiException && e.status == 401) expired()
                else update(state.copy(loading = false, error = "下一集信息暂时无法读取，可重试；当前播放不受影响"))
            }
        }
    }

    /** Wait for stable playback and the last two minutes to avoid competing with startup. */
    fun tick(positionMs: Long, durationMs: Long, playing: Boolean) {
        if (!playing || durationMs <= 0 || positionMs < 0) return
        if (durationMs - positionMs in 0..120_000 && !attempted && state.next != null && !state.loading)
            prepare()
    }

    private fun prepare() {
        val next = state.next ?: return
        if (preparation?.isActive == true) return
        val token = generation
        attempted = true
        update(state.copy(loading = true, error = null))
        preparation = scope.launch {
            try {
                val fetched = fetchItem(next.id)
                ensureActive()
                if (token != generation) return@launch
                check(fetched.id == next.id && fetched.type == "Episode" &&
                    fetched.seriesId == owner?.media?.seriesId) { "下一集信息与当前剧集不符" }
                cached = fetched; cachedAt = now()
                update(state.copy(next = fetched, loading = false, prepared = true))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                ensureActive()
                if (token != generation) return@launch
                if (e is ApiException && e.status == 401) expired()
                else update(state.copy(loading = false, error = "下一集预准备失败，仍可直接播放或重试准备"))
            }
        }
    }

    fun retry() {
        if (state.loading || owner == null) return
        if (state.next == null) { discovered = false; start() } else prepare()
    }

    fun preparedFor(target: Media): Media? = cached?.takeIf {
        owner != null && state.next?.id == target.id && it.id == target.id && now() - cachedAt in 0..120_000
    }

    suspend fun awaitNext(): Media? {
        val token = generation
        discovery?.join()
        return state.next.takeIf { token == generation }
    }
}

/** Source IDs are item-specific. Carry only an unambiguous named version across episodes. */
internal data class EpisodeVersionHint(val name: String, val container: String) {
    fun match(sources: List<MediaSource>): String? = sources.filter {
        name.isNotBlank() && it.name.trim().equals(name, ignoreCase = true) &&
            it.container.equals(container, ignoreCase = true)
    }.singleOrNull()?.id

    companion object {
        fun from(source: MediaSource?) = source?.takeIf { it.name.isNotBlank() }
            ?.let { EpisodeVersionHint(it.name.trim(), it.container) }
    }
}
