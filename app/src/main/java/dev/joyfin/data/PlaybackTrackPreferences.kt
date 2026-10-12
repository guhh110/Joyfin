package dev.joyfin.data

import java.util.Locale
import org.json.JSONObject

/** Semantic choices only: stream indices belong to one source, never to a series. */
data class TrackPreference(
    val language: String,
    val forced: Boolean,
    val hearingImpaired: Boolean,
    val commentary: Boolean,
    val variant: String,
    val codec: String,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("language", language)
            .put("forced", forced)
            .put("hearingImpaired", hearingImpaired)
            .put("commentary", commentary)
            .put("variant", variant)
            .put("codec", codec)

    companion object {
        private fun language(value: String): String {
            val normalized = value.trim().lowercase(Locale.ROOT).replace('_', '-')
            val parts = normalized.split('-')
            val base =
                when (parts.first()) {
                    "eng" -> "en"
                    "jpn" -> "ja"
                    "zho",
                    "chi" -> "zh"
                    "fra",
                    "fre" -> "fr"
                    "deu",
                    "ger" -> "de"
                    "kor" -> "ko"
                    "spa" -> "es"
                    "und",
                    "unknown",
                    "null",
                    "" -> return ""
                    else -> parts.first()
                }
            return (listOf(base) + parts.drop(1)).joinToString("-")
        }

        fun fromTrack(track: JSONObject): TrackPreference? {
            val lang = language(track.optString("Language"))
            if (lang.isBlank()) return null
            val title = track.optString("Title").lowercase(Locale.ROOT)
            val variant =
                when {
                    lang.contains("hans") || title.contains("简体") || title.contains("簡體") -> "hans"
                    lang.contains("hant") || title.contains("繁体") || title.contains("繁體") -> "hant"
                    else -> ""
                }
            return TrackPreference(
                lang.split('-').filterNot { it == "hans" || it == "hant" }.joinToString("-"),
                track.optBoolean("IsForced"),
                track.optBoolean("IsHearingImpaired"),
                track.optBoolean("IsCommentary") ||
                    title.contains("commentary") ||
                    title.contains("评论音轨"),
                variant,
                track.optString("Codec").lowercase(Locale.ROOT),
            )
        }

        fun fromJson(json: JSONObject): TrackPreference? =
            json
                .optString("language")
                .takeIf { it.isNotBlank() }
                ?.let {
                    TrackPreference(
                        it,
                        json.optBoolean("forced"),
                        json.optBoolean("hearingImpaired"),
                        json.optBoolean("commentary"),
                        json.optString("variant"),
                        json.optString("codec"),
                    )
                }
    }

    fun match(type: String, streams: List<JSONObject>): Int? =
        streams
            .filter { it.optString("Type") == type && it.optInt("Index", -1) >= 0 }
            .mapNotNull { track -> fromTrack(track)?.let { track to it } }
            .filter { (_, choice) ->
                language == choice.language &&
                    forced == choice.forced &&
                    hearingImpaired == choice.hearingImpaired &&
                    commentary == choice.commentary &&
                    variant == choice.variant
            }
            .sortedByDescending { (_, choice) -> choice.codec == codec }
            .firstOrNull()
            ?.first
            ?.optInt("Index")
}

data class PlaybackTrackPreferences(
    val audio: TrackPreference? = null,
    val subtitle: TrackPreference? = null,
    val subtitlesOff: Boolean = false,
) {
    fun subtitleIndex(streams: List<JSONObject>): Int? =
        if (subtitlesOff) -1 else subtitle?.match("Subtitle", streams)

    fun toJson(): JSONObject =
        JSONObject()
            .put("audio", audio?.toJson())
            .put("subtitle", subtitle?.toJson())
            .put("subtitlesOff", subtitlesOff)

    /** Unknown language clears the old choice rather than guessing from an index or name. */
    fun selected(type: String, index: Int, streams: List<JSONObject>): PlaybackTrackPreferences {
        val track = streams.firstOrNull {
            it.optString("Type") == type && it.optInt("Index", -1) == index
        }
        val choice = track?.let(TrackPreference::fromTrack)
        return when (type) {
            "Audio" -> copy(audio = choice)
            "Subtitle" -> copy(subtitle = choice, subtitlesOff = index == -1)
            else -> this
        }
    }

    companion object {
        fun fromJson(json: JSONObject): PlaybackTrackPreferences =
            PlaybackTrackPreferences(
                json.optJSONObject("audio")?.let(TrackPreference::fromJson),
                json.optJSONObject("subtitle")?.let(TrackPreference::fromJson),
                json.optBoolean("subtitlesOff"),
            )
    }
}
