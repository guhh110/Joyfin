package dev.joyfin.playback

/** Media3 prefixes merged track IDs with a source index; libass removes that prefix. */
object SubtitleTrackId {
    fun external(index: Int): String = "joyfin-subtitle-$index"

    fun isExternal(formatId: String?): Boolean =
        formatId?.substringAfter(':')?.startsWith("joyfin-subtitle-") == true

    fun matches(formatId: String?, index: Int): Boolean =
        formatId?.substringAfter(':') == external(index)
}
