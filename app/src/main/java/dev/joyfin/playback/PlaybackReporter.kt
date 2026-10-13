package dev.joyfin.playback

import dev.joyfin.data.*
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

/** Progress stays ordered, while terminal cleanup has an independent, durable queue. */
class PlaybackReporter(private val api: JellyfinApi, private val store: SessionStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stopped = ConcurrentHashMap.newKeySet<String>()
    private val requestLock = Any()
    private var inFlight: Pair<String, Job>? = null

    private data class Message(val key: String, val event: String, val body: PlaybackReport)

    private val queue = Channel<Message>(64)
    private val owner = api.session

    @Serializable
    private data class LegacyReport(
        val user: String,
        val server: String,
        val path: String,
        val body: PlaybackReport,
    )

    private val stopPersistence =
        PlaybackStopPersistence(
            owner,
            { store.pendingPlaybackStops },
            { store.pendingPlaybackStops = it },
            {
                store.load()?.let { it.userId == owner?.userId && it.server == owner.server } ==
                    true
            },
        )
    private val stops =
        PlaybackStopQueue(cleanupScope, stopPersistence.load(), stopPersistence::save) { stop ->
            if (stop.encoding) api.stopTranscoding(stop.body.playSessionId)
            api.reportSnapshot(stop.body, "Stopped")
        }

    init {
        stops.worker.invokeOnCompletion { cleanupScope.cancel() }
        // Migrate the former single pending report. Never replay stale progress as active playback.
        runCatching {
            store.pendingReport
                ?.let { AppJson.decodeFromString<LegacyReport>(it) }
                ?.takeIf { owner != null && it.user == owner.userId && it.server == owner.server }
                ?.let { old ->
                    if (old.path == "Sessions/Playing/Stopped")
                        stops.enqueue(PlaybackStop(old.body, true))
                }
        }
        store.pendingReport = null
        scope.launch {
            try {
                for (message in queue) {
                    coroutineScope {
                        val request =
                            launch(start = CoroutineStart.LAZY) {
                                withTimeoutOrNull(8_000) {
                                    try {
                                        api.reportSnapshot(message.body, message.event)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
                                        /* Progress is superseded by the next snapshot. */
                                    }
                                }
                            }
                        synchronized(requestLock) {
                            if (message.key in stopped) request.cancel()
                            else inFlight = message.key to request
                        }
                        request.start()
                        request.join()
                        synchronized(requestLock) {
                            if (inFlight?.second === request) inFlight = null
                        }
                    }
                }
            } finally {
                scope.cancel()
            }
        }
    }

    fun send(plan: PlaybackPlan, event: String, ticks: Long, paused: Boolean) {
        val body = api.reportBody(plan, event, ticks, paused)
        val key = plan.sessionId.ifBlank { plan.item.id }
        if (event == "Stopped") {
            synchronized(requestLock) {
                stopped.add(key)
                inFlight?.takeIf { it.first == key }?.second?.cancel()
            }
            stops.enqueue(PlaybackStop(body, plan.method != "DirectPlay"))
        } else if (key !in stopped) {
            queue.trySend(Message(key, event, body))
        }
    }

    fun close() {
        queue.close()
        stops.close()
    }
}
