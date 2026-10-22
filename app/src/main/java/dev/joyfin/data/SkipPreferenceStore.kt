package dev.joyfin.data

import android.content.Context
import kotlinx.serialization.encodeToString

/** Separate from audio/subtitle preferences; manual timings follow the series on this account. */
internal class SkipPreferenceStore(context: Context) {
    private val prefs = context.getSharedPreferences("playback-skip", Context.MODE_PRIVATE)
    fun load(session: Session, media: Media): SkipPreferences = runCatching {
        AppJson.decodeFromString<SkipPreferences>(prefs.getString(PlaybackPreferenceStore.key(session, media), "{}") ?: "{}")
            .validated()
    }.getOrDefault(SkipPreferences())
    fun save(session: Session, media: Media, value: SkipPreferences) {
        prefs.edit().putString(PlaybackPreferenceStore.key(session, media), AppJson.encodeToString(value.validated())).apply()
    }
}
