package dev.joyfin.playback

import dev.joyfin.data.PlaybackReport
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Serializable

/** Stops have a dedicated worker; a slow progress report cannot hold a transcoder open. */
@Serializable
internal data class PlaybackStop(val body: PlaybackReport, val encoding: Boolean) {
    val key: String
        get() = body.playSessionId.ifBlank { body.itemId }
}

internal class PlaybackStopQueue(
    scope: CoroutineScope,
    pending: List<PlaybackStop>,
    private val save: (List<PlaybackStop>) -> Unit,
    private val deliver: suspend (PlaybackStop) -> Unit,
) {
    private val lock = Any()
    private val stops =
        linkedMapOf<String, PlaybackStop>().apply { pending.forEach { put(it.key, it) } }
    private val signal = Channel<Unit>(Channel.CONFLATED)
    val worker =
        scope.launch {
            for (ignored in signal) {
                val snapshot = synchronized(lock) { stops.values.toList() }
                for (stop in snapshot) {
                    var done = false
                    for (attempt in 0..2) {
                        try {
                            withTimeout(8_000) { deliver(stop) }
                            done = true
                            break
                        } catch (e: TimeoutCancellationException) {
                            // A request deadline retries this stop; parent cancellation must
                            // propagate.
                            currentCoroutineContext().ensureActive()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            // The durable record remains available to the next player instance.
                        }
                        if (attempt < 2) delay((attempt + 1) * 1_000L)
                    }
                    if (done)
                        synchronized(lock) {
                            if (stops[stop.key] === stop) stops.remove(stop.key)
                            save(stops.values.toList())
                        }
                }
            }
        }

    init {
        signal.trySend(Unit)
    }

    fun enqueue(stop: PlaybackStop) {
        synchronized(lock) {
            stops[stop.key] = stop
            save(stops.values.toList())
        }
        signal.trySend(Unit)
    }

    fun close() {
        signal.close()
    }
}
