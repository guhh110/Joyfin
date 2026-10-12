package dev.joyfin.data

import org.json.JSONObject

/** Shared subtitle negotiation policy. Null means automatic; -1 explicitly disables subtitles. */
object SubtitleDelivery {
    data class Format(val extension: String, val mime: String)

    val externalCodecs = listOf("srt", "subrip", "vtt", "webvtt", "ass", "ssa")
    val bitmapCodecs = listOf("pgssub", "dvdsub", "dvbsub")

    fun format(codec: String): Format? =
        when (codec.lowercase()) {
            "srt",
            "subrip" -> Format("srt", "application/x-subrip")
            "vtt",
            "webvtt" -> Format("vtt", "text/vtt")
            "ass",
            "ssa" -> Format(codec.lowercase(), "text/x-ssa")
            else -> null
        }

    fun select(requested: Int?, streams: List<JSONObject>): Int =
        requested
            ?: streams
                .firstOrNull { it.optString("Type") == "Subtitle" && it.optInt("Index", -1) >= 0 }
                ?.optInt("Index")
            ?: -1

    fun requiresBurnIn(index: Int, streams: List<JSONObject>): Boolean =
        if (index < 0) false
        else
            streams
                .firstOrNull {
                    it.optString("Type") == "Subtitle" && it.optInt("Index", -1) == index
                }
                ?.let { format(it.optString("Codec")) == null } ?: false
}
