package dev.joyfin.browse

import dev.joyfin.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Independently loaded rails, with one shared concurrency budget including retries. */
class HomeLibraryLoader(
    private val scope: CoroutineScope,
    private val latest: suspend (String) -> List<Media>,
    private val publish: (LibraryRailState) -> Unit,
    private val expired: () -> Unit,
) {
    private val budget = Semaphore(3)
    private val jobs = mutableMapOf<String, Job>()
    private var generation = 0

    fun cancel() {
        generation++
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }

    fun load(libraries: List<Media>) {
        cancel()
        libraries.forEach(::retry)
    }

    fun retry(library: Media) {
        jobs.remove(library.id)?.cancel()
        val token = generation
        jobs[library.id] = scope.launch {
            publish(LibraryRailState(library))
            try {
                val items = budget.withPermit { latest(library.id) }
                ensureActive()
                if (token == generation) publish(LibraryRailState(library, items, loading = false))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ensureActive()
                if (token != generation) return@launch
                if (e is ApiException && e.status == 401) expired()
                else
                    publish(LibraryRailState(library, loading = false, error = e.message ?: "加载失败"))
            }
        }
    }
}
