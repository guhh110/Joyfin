package dev.joyfin.data

import android.content.Context
import java.security.MessageDigest
import org.json.JSONObject

/** Device-local preferences, partitioned by normalized server, user and series (or movie). */
class PlaybackPreferenceStore(context: Context) {
    private val prefs =
        context.getSharedPreferences("playback-track-preferences", Context.MODE_PRIVATE)

    fun load(session: Session, media: Media): PlaybackTrackPreferences = runCatching {
        PlaybackTrackPreferences.fromJson(
            JSONObject(prefs.getString(key(session, media), "{}") ?: "{}"),
        )
    }
        .getOrDefault(PlaybackTrackPreferences())

    fun save(session: Session, media: Media, value: PlaybackTrackPreferences) {
        prefs.edit().putString(key(session, media), value.toJson().toString()).apply()
    }

    companion object {
        internal fun key(session: Session, media: Media): String {
            val scope =
                if (media.type == "Episode" && media.seriesId.isNotBlank())
                    "series:${media.seriesId}"
                else "item:${media.id}"
            val identity =
                JSONObject()
                    .put("server", session.server.trimEnd('/'))
                    .put("user", session.userId)
                    .put("scope", scope)
                    .toString()
            return MessageDigest.getInstance("SHA-256")
                .digest(identity.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}
