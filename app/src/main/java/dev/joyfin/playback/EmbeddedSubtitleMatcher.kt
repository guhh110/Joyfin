package dev.joyfin.playback

import dev.joyfin.data.MediaStream
import dev.joyfin.data.SubtitleDelivery

/** Jellyfin indexes all streams; Media3 groups subtitles separately. Never treat them as equal. */
object EmbeddedSubtitleMatcher {
    data class Track(val mime: String?, val language: String?, val label: String?)

    fun find(index: Int, streams: List<MediaStream>, tracks: List<Track>): Int? {
        val embedded = streams.filter { it.type == "Subtitle" && !it.external }
        val ordinal = embedded.indexOfFirst { it.index == index }
        if (ordinal < 0) return null
        // Unsupported streams can be omitted by the demuxer. Only use an unambiguous metadata
        // match.
        val target = embedded[ordinal]
        val mime = SubtitleDelivery.mime(target.codec) ?: return null
        val peers = embedded.filter { SubtitleDelivery.mime(it.codec) == mime }
        val runtimePeers = tracks.indices.filter { tracks[it].mime == mime }
        // Renderer assignment can reorder formats. Preserve order within a format only.
        if (peers.size == runtimePeers.size) return runtimePeers.getOrNull(peers.indexOf(target))
        if (
            embedded.count {
                SubtitleDelivery.mime(it.codec) == SubtitleDelivery.mime(target.codec) &&
                    (target.language.isBlank() || target.language.equals(it.language, true)) &&
                    (target.title.isBlank() || target.title == it.title)
            } != 1
        )
            return null
        val matches =
            tracks.indices.filter { i ->
                val track = tracks[i]
                SubtitleDelivery.mime(target.codec) == track.mime &&
                    (target.language.isBlank() || target.language.equals(track.language, true)) &&
                    (target.title.isBlank() || target.title == track.label)
            }
        return matches.singleOrNull()
    }
}
