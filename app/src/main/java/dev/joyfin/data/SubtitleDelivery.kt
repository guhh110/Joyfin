package dev.joyfin.data

/** Shared subtitle negotiation policy. Null means automatic; -1 explicitly disables subtitles. */
object SubtitleDelivery {
    data class Format(val extension: String, val mime: String)

    val externalCodecs = listOf("srt", "subrip", "vtt", "webvtt", "ass", "ssa", "ttml")
    val bitmapCodecs = listOf("pgssub", "dvdsub", "dvbsub")

    // These demuxers deliver bitmap samples and required codec initialization data to Media3.
    private val embeddedContainers =
        mapOf(
            "pgssub" to listOf("mkv", "matroska"),
            "dvdsub" to listOf("mkv", "matroska"),
            "dvbsub" to listOf("mkv", "matroska", "ts", "mpegts", "m2ts"),
            "mov_text" to listOf("mp4", "m4v", "mov"),
            "tx3g" to listOf("mp4", "m4v", "mov"),
        )

    fun profiles(): List<SubtitleProfile> =
        externalCodecs.map { SubtitleProfile(it, "External") } +
            embeddedContainers.map { (codec, containers) ->
                SubtitleProfile(codec, "Embed", containers.joinToString(","))
            } +
            bitmapCodecs.map { SubtitleProfile(it, "Encode") }

    fun embedded(track: MediaStream, container: String): Boolean =
        !track.external &&
            container.lowercase().split(',').any {
                it.trim() in embeddedContainers[track.codec.lowercase()].orEmpty()
            }

    fun mime(codec: String): String? =
        format(codec)?.mime
            ?: when (codec.lowercase()) {
                "pgssub" -> "application/pgs"
                "dvdsub" -> "application/vobsub"
                "dvbsub" -> "application/dvbsubs"
                "mov_text",
                "tx3g" -> "application/x-quicktime-tx3g"
                else -> null
            }

    fun format(codec: String): Format? =
        when (codec.lowercase()) {
            "srt",
            "subrip" -> Format("srt", "application/x-subrip")
            "vtt",
            "webvtt" -> Format("vtt", "text/vtt")
            "ass",
            "ssa" -> Format(codec.lowercase(), "text/x-ssa")
            "ttml",
            "dfxp" -> Format("ttml", "application/ttml+xml")
            else -> null
        }

    fun externalFormat(track: MediaStream): Format? =
        format(track.deliveryUrl.substringBefore('?').substringAfterLast('.'))
            ?: format(track.codec)
            ?: if (track.textSubtitle) format("srt") else null

    fun select(requested: Int?, streams: List<MediaStream>): Int =
        requested ?: streams.firstOrNull { it.type == "Subtitle" && it.index >= 0 }?.index ?: -1

    fun requiresBurnIn(index: Int, streams: List<MediaStream>, container: String = ""): Boolean =
        if (index < 0) false
        else
            streams
                .firstOrNull { it.type == "Subtitle" && it.index == index }
                ?.let { format(it.codec) == null && !it.textSubtitle && !embedded(it, container) }
                ?: false
}
