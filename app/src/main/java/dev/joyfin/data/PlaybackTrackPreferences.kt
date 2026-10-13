package dev.joyfin.data

import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

/** Semantic choices only: stream indices belong to one source, never to a series. */
@Serializable
data class TrackPreference(
    val language: String,
    val forced: Boolean = false,
    val hearingImpaired: Boolean = false,
    val commentary: Boolean = false,
    val variant: String = "",
    val codec: String = "",
) {
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

        fun fromTrack(track: MediaStream): TrackPreference? {
            val lang = language(track.language)
            if (lang.isBlank()) return null
            val title = track.title.lowercase(Locale.ROOT)
            val variant =
                when {
                    lang.contains("hans") || title.contains("简体") || title.contains("簡體") -> "hans"
                    lang.contains("hant") || title.contains("繁体") || title.contains("繁體") -> "hant"
                    else -> ""
                }
            return TrackPreference(
                lang.split('-').filterNot { it == "hans" || it == "hant" }.joinToString("-"),
                track.forced,
                track.hearingImpaired,
                track.commentary || title.contains("commentary") || title.contains("评论音轨"),
                variant,
                track.codec.lowercase(Locale.ROOT),
            )
        }
    }

    fun match(type: String, streams: List<MediaStream>): Int? =
        streams
            .filter { it.type == type && it.index >= 0 }
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
            ?.index
}

@Serializable
data class PlaybackTrackPreferences(
    val audio: TrackPreference? = null,
    val subtitle: TrackPreference? = null,
    val subtitlesOff: Boolean = false,
    val bitrate: Int? = null,
    val compatible: Boolean = false,
) {
    companion object {
        fun decode(value: String): PlaybackTrackPreferences =
            runCatching {
                    AppJson.decodeFromString<PlaybackTrackPreferences>(value).let {
                        it.copy(bitrate = it.bitrate?.takeIf { bitrate -> bitrate > 0 })
                    }
                }
                .getOrDefault(PlaybackTrackPreferences())
    }

    fun subtitleIndex(streams: List<MediaStream>): Int? =
        if (subtitlesOff) -1 else subtitle?.match("Subtitle", streams)

    /** Unknown language clears the old choice rather than guessing from an index or name. */
    fun selected(type: String, index: Int, streams: List<MediaStream>): PlaybackTrackPreferences {
        val track = streams.firstOrNull { it.type == type && it.index == index }
        val choice = track?.let(TrackPreference::fromTrack)
        return when (type) {
            "Audio" -> copy(audio = choice)
            "Subtitle" -> copy(subtitle = choice, subtitlesOff = index == -1)
            else -> this
        }
    }
}
