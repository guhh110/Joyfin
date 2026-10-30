package dev.joyfin.browse

import dev.joyfin.data.ApiException
import dev.joyfin.data.Media
import kotlinx.coroutines.*

/** Base homepage regions publish independently and retry without restarting their neighbours. */
enum class HomeRegion {
    RESUME,
    LATEST,
    LIBRARIES,
}

class HomeContentLoader(
    private val scope: CoroutineScope,
    private val fetch: suspend (HomeRegion) -> List<Media>,
    private val publish: (HomeRegion, List<Media>?, String?) -> Unit,
    private val expired: () -> Unit,
) {
    private val jobs = mutableMapOf<HomeRegion, Job>()
    private var generation = 0

    fun cancel() {
        generation++
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }

    fun load() {
        cancel()
        HomeRegion.entries.forEach(::retry)
    }

    fun cancel(region: HomeRegion) { jobs.remove(region)?.cancel() }

    fun retry(region: HomeRegion) {
        jobs.remove(region)?.cancel()
        val token = generation
        publish(region, null, null)
        jobs[region] = scope.launch {
            try {
                val items = fetch(region)
                ensureActive()
                if (token == generation) publish(region, items, null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ensureActive()
                if (token != generation) return@launch
                if (e is ApiException && e.status == 401) expired()
                else publish(region, null, e.message ?: "读取失败，请重试")
            }
        }
    }
}
