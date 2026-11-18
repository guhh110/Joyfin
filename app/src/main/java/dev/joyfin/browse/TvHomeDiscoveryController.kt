package dev.joyfin.browse

import dev.joyfin.data.ApiException
import dev.joyfin.data.Media
import dev.joyfin.data.Session
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TvHomeDiscoveryUiState(
    val items: List<Media> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
)

class TvHomeDiscoveryController(
    private val scope: CoroutineScope,
    private val session: () -> Session?,
    private val fetch: suspend () -> List<Media>,
    private val expired: () -> Unit,
) {
    private val mutable = MutableStateFlow(TvHomeDiscoveryUiState())
    val state = mutable.asStateFlow()
    private var owner: Session? = null
    private var active = false
    private var generation = 0L
    private var job: Job? = null

    fun setActive(value: Boolean) {
        val user = session()
        if (owner != user) clear()
        owner = user
        if (active == value) return
        active = value
        if (value) refresh() else cancel()
    }

    fun refresh() {
        if (!active) return
        val user = session() ?: return
        if (owner != user) {
            clear()
            owner = user
            active = true
        }
        cancel()
        val version = generation
        mutable.value = mutable.value.copy(loading = true)
        job = scope.launch {
            try {
                val items = fetch()
                ensureActive()
                if (active && owner == user && session() == user && version == generation)
                    mutable.value = TvHomeDiscoveryUiState(items, loaded = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                ensureActive()
                if (active && owner == user && session() == user && version == generation) {
                    if (error is ApiException && error.status == 401) expired()
                    else mutable.value = mutable.value.copy(loading = false, error = error.message ?: "读取最近加入失败")
                }
            }
        }
    }

    private fun cancel() {
        generation++
        job?.cancel()
        job = null
        mutable.value = mutable.value.copy(loading = false)
    }

    fun clear() {
        cancel()
        owner = null
        active = false
        mutable.value = TvHomeDiscoveryUiState()
    }
}
