package dev.joyfin.playback

import dev.joyfin.data.Session
import dev.joyfin.data.objects
import org.json.JSONArray
import org.json.JSONObject

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
                val json = read()?.let(::JSONObject)
                if (
                    owner == null ||
                        json?.optString("user") != owner.userId ||
                        json.optString("server") != owner.server
                )
                    emptyList()
                else
                    json.optJSONArray("requests").objects().map {
                        PlaybackStop(it.getJSONObject("body"), it.optBoolean("encoding"))
                    }
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
                    JSONObject()
                        .put("user", owner.userId)
                        .put("server", owner.server)
                        .put(
                            "requests",
                            JSONArray().apply {
                                current.values.forEach {
                                    put(
                                        JSONObject()
                                            .put("body", it.body)
                                            .put("encoding", it.encoding)
                                    )
                                }
                            },
                        )
                        .toString()
            )
        }
}
