package dev.joyfin.data

/** Jellyfin's bitrate fields use an integer; this removes the old implicit 80 Mbps ceiling. */
object PlaybackQuality {
    const val ORIGINAL = Int.MAX_VALUE
    const val DEFAULT = ORIGINAL
}
