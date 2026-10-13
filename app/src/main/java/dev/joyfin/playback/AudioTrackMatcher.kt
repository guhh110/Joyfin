package dev.joyfin.playback

import dev.joyfin.data.MediaStream

/** System/FFmpeg renderer assignment can change the global audio group order. */
object AudioTrackMatcher {
    data class Track(
        val mime: String?,
        val language: String?,
        val label: String?,
        val channels: Int,
    )

    fun find(index: Int, streams: List<MediaStream>, tracks: List<Track>): Int? {
        val source = streams.filter { it.type == "Audio" && !it.external }
        val target = source.firstOrNull { it.index == index } ?: return null
        val mimes = AudioPlaybackCapabilities.mimesFor(target)
        if (mimes.isEmpty()) return null
        val peers =
            source.filter { AudioPlaybackCapabilities.mimesFor(it).any { mime -> mime in mimes } }
        val runtimePeers = tracks.indices.filter { tracks[it].mime in mimes }
        val exact =
            runtimePeers.filter { i ->
                val track = tracks[i]
                (target.language.isBlank() || target.language.equals(track.language, true)) &&
                    (target.title.isBlank() || target.title == track.label) &&
                    (target.channels <= 0 || target.channels == track.channels)
            }
        val uniqueSource =
            peers.count {
                (target.language.isBlank() || target.language.equals(it.language, true)) &&
                    (target.title.isBlank() || target.title == it.title) &&
                    (target.channels <= 0 || target.channels == it.channels)
            } == 1
        if (uniqueSource && exact.size == 1) return exact.single()
        if (peers.size == runtimePeers.size) return runtimePeers.getOrNull(peers.indexOf(target))
        return null
    }
}
