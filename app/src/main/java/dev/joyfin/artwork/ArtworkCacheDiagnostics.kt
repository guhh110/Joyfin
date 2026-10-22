package dev.joyfin.artwork

import coil.decode.DataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ArtworkCacheSnapshot(
    val memory: Long = 0,
    val disk: Long = 0,
    val network: Long = 0,
    val local: Long = 0,
    val failed: Long = 0,
)

/** Aggregate process-local counters only: no image URLs, names, headers or account identifiers. */
object ArtworkCacheDiagnostics {
    private val mutable = MutableStateFlow(ArtworkCacheSnapshot())
    val state = mutable.asStateFlow()

    internal fun success(source: DataSource, remote: Boolean) = mutable.update {
        when {
            source == DataSource.MEMORY_CACHE -> it.copy(memory = it.memory + 1)
            !remote -> it.copy(local = it.local + 1)
            source == DataSource.DISK -> it.copy(disk = it.disk + 1)
            source == DataSource.NETWORK -> it.copy(network = it.network + 1)
            else -> it.copy(local = it.local + 1)
        }
    }

    internal fun failure() = mutable.update { it.copy(failed = it.failed + 1) }
}
