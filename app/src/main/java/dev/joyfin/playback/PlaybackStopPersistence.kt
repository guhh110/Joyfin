package dev.joyfin.playback

import dev.joyfin.data.AppJson
import dev.joyfin.data.Session
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Serializable
private data class PendingStops(
    val user: String,
    val server: String,
    val requests: List<PlaybackStop>,
)

/** Player activities can overlap while their final network requests drain. Merge by session. */
internal class PlaybackStopPersistence(
    private val owner: Session?,
    private val read: () -> String?,
    private val write: (String?) -> Unit,
    private val isCurrentOwner: () -> Boolean = { true },
) {
    companion object {
        private val lock = Any()
    }

    private var previousKeys = emptySet<String>()

    private fun records(): List<PlaybackStop> =
        runCatching {
                val json = read()?.let { AppJson.decodeFromString<PendingStops>(it) }
                if (owner == null || json?.user != owner.userId || json.server != owner.server)
                    emptyList()
                else json.requests
            }
            .getOrDefault(emptyList())

    fun load(): List<PlaybackStop> =
        synchronized(lock) { records().also { previousKeys = it.map { stop -> stop.key }.toSet() } }

    fun save(pending: List<PlaybackStop>) =
        synchronized(lock) {
            if (owner == null || !isCurrentOwner()) return@synchronized
            val current = records().associateByTo(linkedMapOf()) { it.key }
            val keys = pending.map { it.key }.toSet()
            (previousKeys - keys).forEach { current.remove(it) }
            pending.forEach { current[it.key] = it }
            previousKeys = keys
            write(
                if (current.isEmpty()) null
                else
                    AppJson.encodeToString(
                        PendingStops(owner.userId, owner.server, current.values.toList())
                    )
            )
        }
}
