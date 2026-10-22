package dev.joyfin.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class HomeLayoutPreferences(val order: List<String> = emptyList(), val hidden: Set<String> = emptySet()) {
    fun orderedKeys(libraryIds: List<String>): List<String> {
        val known = listOf("resume", "latest") + libraryIds.map { "library:$it" }
        return (order.filter { it in known } + known).distinct()
    }
    fun visibleKeys(libraryIds: List<String>) = orderedKeys(libraryIds).filterNot { it in hidden }
    fun move(key: String, delta: Int, libraryIds: List<String>): HomeLayoutPreferences {
        val keys = orderedKeys(libraryIds).toMutableList()
        val from = keys.indexOf(key)
        if (from < 0) return this
        val to = (from + delta).coerceIn(0, keys.lastIndex)
        keys.add(to, keys.removeAt(from))
        // Preserve temporarily unavailable library IDs for when the server returns them again.
        val remaining = keys.iterator()
        val merged = order.distinct().mapNotNull { saved ->
            if (saved !in keys) saved else if (remaining.hasNext()) remaining.next() else null
        } + remaining.asSequence().toList()
        return copy(order = merged)
    }
    fun toggle(key: String) = copy(hidden = if (key in hidden) hidden - key else hidden + key)
}

internal class HomeLayoutStore(context: Context) {
    private val prefs = context.getSharedPreferences("home-layout", Context.MODE_PRIVATE)
    private fun key(session: Session) = PlaybackPreferenceStore.key(session, Media("home-layout", "", "Folder"))
    private fun load(session: Session?): HomeLayoutPreferences = session?.let { account -> runCatching {
        AppJson.decodeFromString<HomeLayoutPreferences>(prefs.getString(key(account), "{}") ?: "{}")
    }.getOrDefault(HomeLayoutPreferences()) } ?: HomeLayoutPreferences()
    fun save(session: Session?, value: HomeLayoutPreferences) {
        session ?: return
        prefs.edit().putString(key(session), AppJson.encodeToString(value)).apply()
    }
    fun changes(session: Session?) = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (session != null && changed == key(session)) trySend(load(session))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(load(session))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
}
