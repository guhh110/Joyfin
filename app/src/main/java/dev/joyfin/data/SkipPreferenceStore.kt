package dev.joyfin.data

import android.content.Context
import java.security.MessageDigest
import kotlinx.serialization.encodeToString

/** Account/season sharing is the default; version-specific marks remain available by choice. */
internal class SkipPreferenceStore(context: Context) {
    private val prefs = context.getSharedPreferences("playback-skip", Context.MODE_PRIVATE)
    fun separateVersions(session: Session, media: Media): Boolean =
        prefs.getBoolean(scopeKey(session, SkipPreferenceTarget(media, "")), false)

    fun setSeparateVersions(session: Session, target: SkipPreferenceTarget) {
        prefs.edit().putBoolean(scopeKey(session, target), target.separateVersions).apply()
    }

    fun load(session: Session, target: SkipPreferenceTarget): SkipPreferences {
        val currentKey = key(session, target)
        if (prefs.contains(currentKey)) return decode(currentKey)
        if (target.canShare && !target.separateVersions) {
            // Adopt only this episode's former mark, never guess among other episodes' marks.
            val previousKey = key(session, target, target.versionIdentity)
            if (prefs.contains(previousKey)) return decode(previousKey).also { save(session, target, it) }
        }
        if (target.canShare && target.separateVersions)
            return decode(key(session, target.copy(separateVersions = false)))
        return SkipPreferences()
    }

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

    private fun scopeKey(session: Session, target: SkipPreferenceTarget): String =
        "${key(session, target.copy(separateVersions = false))}:separate-versions"

    private fun key(session: Session, target: SkipPreferenceTarget,
        identity: SkipScopeIdentity = target.identity): String {
        val scope = AppJson.encodeToString(identity)
        val digest = MessageDigest.getInstance("SHA-256").digest(scope.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "${PlaybackPreferenceStore.key(session, target.media)}:season-version:$digest"
    }
}
