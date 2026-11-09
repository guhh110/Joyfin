package dev.joyfin.browse

import dev.joyfin.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TrackingUiState(
    val entries: List<TrackingEntry> = emptyList(),
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val removingId: String? = null,
    val actionError: String? = null,
) {
    val carousel: List<TrackingEntry>
        get() = entries.filter { it.progress != null }.sortedWith(trackingOrder)
            .filter { it.progress?.status != TrackingStatus.UNAVAILABLE }.take(6)
    val empty: Boolean get() = loaded && entries.isEmpty()
}

/** One session-owned cache shared by Home and Tracking, never saved in navigation snapshots. */
class TrackingController(
    private val scope: CoroutineScope,
    private val session: () -> Session?,
    private val favorites: suspend (Int) -> MediaPage,
    private val episodes: suspend (String) -> List<Media>,
    private val favorite: suspend (String, Boolean) -> Unit,
    private val expired: () -> Unit,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val mutable = MutableStateFlow(TrackingUiState())
    val state = mutable.asStateFlow()
    private var owner: Session? = null
    private var generation = 0L
    private var job: Job? = null
    private var removal: Job? = null
    private var lastRefresh: Long? = null
    private var loadAll = false
    private val pending = mutableSetOf<String>()

    fun clear() {
        generation++
        job?.cancel()
        removal?.cancel()
        job = null
        removal = null
        owner = null
        lastRefresh = null
        loadAll = false
        pending.clear()
        mutable.value = TrackingUiState()
    }

    fun refresh(all: Boolean = false, force: Boolean = false) {
        val user = session() ?: run { clear(); return }
        if (owner != user) { clear(); owner = user }
        loadAll = all
        if (!force && job?.isActive == true) return
        if (!force && lastRefresh?.let { now() - it < 1_500 } == true && mutable.value.loaded) {
            if (all) loadRemaining(user)
            return
        }
        job?.cancel()
        val version = ++generation
        mutable.value = mutable.value.copy(loading = true, error = null,
            entries = mutable.value.entries.map { it.copy(loading = false) })
        job = scope.launch {
            try {
                val list = mutableListOf<Media>()
                var start = 0
                do {
                    val page = favorites(start)
                    ensureActive()
                    if (!owned(user, version)) return@launch
                    list += page.items
                    start += page.items.size
                } while (page.items.isNotEmpty() && start < page.total)
                val previous = mutable.value.entries.associateBy { it.series.id }
                val entries = list.filter { it.type == "Series" }.distinctBy { it.id }.map {
                    TrackingEntry(it, previous[it.id]?.progress, error = previous[it.id]?.error)
                }.sortedWith(trackingOrder)
                pending.clear()
                pending.addAll(entries.map { it.series.id })
                mutable.value = mutable.value.copy(entries = entries, loaded = true)
                loadProgress(user, version, entries.map { it.series.id })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ensureActive()
                if (owned(user, version) && !auth(e))
                    mutable.value = mutable.value.copy(error = e.message ?: "读取追剧名单失败，请重试")
            } finally {
                if (owned(user, version) && isActive) {
                    lastRefresh = now()
                    mutable.value = mutable.value.copy(loading = false)
                }
            }
        }
    }

    private fun loadRemaining(user: Session) {
        if (job?.isActive == true) return
        val ids = mutable.value.entries.filter { it.series.id in pending || it.progress == null || it.error != null }.map { it.series.id }
        if (ids.isEmpty()) return
        val version = generation
        mutable.value = mutable.value.copy(loading = true)
        job = scope.launch {
            try { loadProgress(user, version, ids) }
            finally {
                if (owned(user, version) && isActive) mutable.value = mutable.value.copy(loading = false)
            }
        }
    }

    private suspend fun loadProgress(user: Session, version: Long, ids: List<String>) {
        val refreshed = mutableSetOf<String>()
        // Waves bound both network concurrency and speculative work for Home's six slots.
        for (batch in ids.chunked(4)) {
            if (!owned(user, version)) return
            mutable.value = mutable.value.copy(entries = mutable.value.entries.map {
                if (it.series.id in batch) it.copy(loading = true, error = null) else it
            })
            coroutineScope {
                batch.map { id -> launch {
                    try {
                        val progress = trackingProgress(episodes(id))
                        ensureActive()
                        if (owned(user, version)) updateEntry(id) { it.copy(progress = progress, loading = false, error = null) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        ensureActive()
                        if (owned(user, version) && !auth(e)) updateEntry(id) {
                            it.copy(loading = false, error = e.message ?: "读取观看进度失败")
                        }
                    }
                } }.joinAll()
            }
            if (!owned(user, version)) return
            pending.removeAll(batch.toSet())
            refreshed += batch
            if (!loadAll && mutable.value.entries.count {
                    it.series.id in refreshed && it.progress?.target != null && it.error == null
                } >= 6) break
        }
    }

    fun retry(id: String) {
        // A full refresh also revalidates that the series is still a server favorite.
        if (mutable.value.entries.any { it.series.id == id }) refresh(loadAll, force = true)
    }

    fun remove(id: String) {
        val user = session() ?: return
        if (owner != user || removal?.isActive == true) return
        mutable.value = mutable.value.copy(removingId = id, actionError = null)
        removal = scope.launch {
            try {
                favorite(id, false)
                ensureActive()
                if (owner != user || session() != user) return@launch
                // Invalidate any list response that might still contain the removed favorite.
                job?.cancel()
                generation++
                mutable.value = mutable.value.copy(entries = mutable.value.entries
                    .filterNot { it.series.id == id }.map { it.copy(loading = false) }, loading = false)
                lastRefresh = null
                refresh(loadAll, force = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ensureActive()
                if (owner == user && session() == user && !auth(e))
                    mutable.value = mutable.value.copy(actionError = e.message ?: "取消追剧失败，请重试")
            } finally {
                if (owner == user && session() == user && isActive)
                    mutable.value = mutable.value.copy(removingId = null)
            }
        }
    }

    private fun updateEntry(id: String, change: (TrackingEntry) -> TrackingEntry) {
        mutable.value = mutable.value.copy(entries = mutable.value.entries.map {
            if (it.series.id == id) change(it) else it
        })
    }

    private fun owned(user: Session, version: Long) = owner == user && session() == user && generation == version
    private fun auth(e: Exception): Boolean {
        if (e is ApiException && e.status == 401) { expired(); return true }
        return false
    }
}
