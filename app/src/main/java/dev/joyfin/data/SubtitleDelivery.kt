package dev.joyfin.data

/** Shared subtitle negotiation policy. Null means automatic; -1 explicitly disables subtitles. */
object SubtitleDelivery {
    data class Format(val extension: String, val mime: String)

    val externalCodecs = listOf("srt", "subrip", "vtt", "webvtt", "ass", "ssa", "ttml")
    val bitmapCodecs = listOf("pgssub", "dvdsub", "dvbsub")

    // MKV text samples are parsed by Media3/libass, including the file's embedded ASS fonts.
    private val embeddedContainers =
        mapOf(
            "ass" to listOf("mkv", "matroska"),
            "ssa" to listOf("mkv", "matroska"),
            "srt" to listOf("mkv", "matroska"),
            "subrip" to listOf("mkv", "matroska"),
            "vtt" to listOf("mkv", "matroska"),
            "webvtt" to listOf("mkv", "matroska"),
            "pgssub" to listOf("mkv", "matroska"),
            "dvdsub" to listOf("mkv", "matroska"),
            "dvbsub" to listOf("mkv", "matroska", "ts", "mpegts", "m2ts"),
            "mov_text" to listOf("mp4", "m4v", "mov"),
            "tx3g" to listOf("mp4", "m4v", "mov"),
        )

    fun profiles(): List<SubtitleProfile> =
        embeddedContainers.map { (codec, containers) ->
            SubtitleProfile(codec, "Embed", containers.joinToString(","))
        } +
            externalCodecs.map { SubtitleProfile(it, "External") } +
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

    /** Attach only the selected independent source. Direct playback reads native tracks in place. */
    fun needsExternalSource(track: MediaStream, selected: Int, direct: Boolean, container: String): Boolean =
        selected >= 0 && track.type == "Subtitle" && track.index == selected &&
            !track.deliveryMethod.equals("Encode", true) && !(direct && embedded(track, container))

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
