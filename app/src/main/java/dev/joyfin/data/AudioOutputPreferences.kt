package dev.joyfin.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class AudioOutputMode(val label: String) {
    AUTO("自动"), PCM("PCM 兼容"),
}

/** Device-local output policy; deliberately separate from account/series track preferences. */
class AudioOutputPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("playback-output", Context.MODE_PRIVATE)
    var mode: AudioOutputMode
        get() = AudioOutputMode.entries.firstOrNull { it.name == prefs.getString(KEY, null) }
            ?: AudioOutputMode.AUTO
        set(value) { prefs.edit().putString(KEY, value.name).apply() }

    val changes = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY) trySend(mode)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(mode)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    private companion object { const val KEY = "audio-mode" }
}
