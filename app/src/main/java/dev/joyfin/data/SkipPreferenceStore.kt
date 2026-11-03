package dev.joyfin.data

import android.content.Context
import java.security.MessageDigest
import kotlinx.serialization.encodeToString

/** Marks are partitioned by account, season and identifiable version; broad legacy data stays intact. */
internal class SkipPreferenceStore(context: Context) {
    private val prefs = context.getSharedPreferences("playback-skip", Context.MODE_PRIVATE)
    fun load(session: Session, target: SkipPreferenceTarget): SkipPreferences = decode(key(session, target))

    fun legacy(session: Session, target: SkipPreferenceTarget): SkipPreferences? {
        if (prefs.contains(key(session, target))) return null
        val oldKey = PlaybackPreferenceStore.key(session, target.media)
        return if (prefs.contains(oldKey)) decode(oldKey).takeIf { it != SkipPreferences() } else null
    }

    fun save(session: Session, target: SkipPreferenceTarget, value: SkipPreferences) {
        prefs.edit().putString(key(session, target), AppJson.encodeToString(value.validated())).apply()
    }

    private fun decode(key: String): SkipPreferences = runCatching {
        AppJson.decodeFromString<SkipPreferences>(prefs.getString(key, "{}") ?: "{}").validated()
    }.getOrDefault(SkipPreferences())

    private fun key(session: Session, target: SkipPreferenceTarget): String {
        val scope = AppJson.encodeToString(target.identity)
        val digest = MessageDigest.getInstance("SHA-256").digest(scope.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "${PlaybackPreferenceStore.key(session, target.media)}:season-version:$digest"
    }
}
