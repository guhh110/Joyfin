package dev.joyfin.data

import android.content.Context
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
private data class PreferenceIdentity(val server: String, val user: String, val scope: String)

/** Device-local preferences, partitioned by normalized server, user and series (or movie). */
class PlaybackPreferenceStore(context: Context) {
    private val prefs =
        context.getSharedPreferences("playback-track-preferences", Context.MODE_PRIVATE)

    fun load(session: Session, media: Media): PlaybackTrackPreferences =
        runCatching {
                PlaybackTrackPreferences.decode(prefs.getString(key(session, media), "{}") ?: "{}")
            }
            .getOrDefault(PlaybackTrackPreferences())

    fun save(session: Session, media: Media, value: PlaybackTrackPreferences) {
        prefs.edit().putString(key(session, media), AppJson.encodeToString(value)).apply()
    }

    companion object {
        internal fun key(session: Session, media: Media): String {
            val scope =
                if (media.type == "Episode" && media.seriesId.isNotBlank())
                    "series:${media.seriesId}"
                else "item:${media.id}"
            // Android's former JSONObject writer escaped slashes. Preserve the existing hash
            // identity so installing this migration keeps each series' saved preferences.
            val identity =
                AppJson.encodeToString(
                        PreferenceIdentity(session.server.trimEnd('/'), session.userId, scope)
                    )
                    .replace("/", "\\/")
            return MessageDigest.getInstance("SHA-256")
                .digest(identity.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}
