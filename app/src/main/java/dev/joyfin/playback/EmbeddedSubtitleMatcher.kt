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
        val uniqueSource =
            peers.count {
                (target.language.isBlank() || target.language.equals(it.language, true)) &&
                    (target.title.isBlank() || target.title == it.title)
            } == 1
        val matches =
            tracks.indices.filter { i ->
                val track = tracks[i]
                SubtitleDelivery.mime(target.codec) == track.mime &&
                    (target.language.isBlank() || target.language.equals(track.language, true)) &&
                    (target.title.isBlank() || target.title == track.label)
            }
        if (uniqueSource && matches.size == 1) return matches.single()
        // When runtime metadata is absent, retain the existing within-format ordinal mapping.
        if (peers.size == runtimePeers.size) return runtimePeers.getOrNull(peers.indexOf(target))
        return null
    }
}
