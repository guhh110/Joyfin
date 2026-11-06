package dev.joyfin.ui.features.player.components

import dev.joyfin.data.Media

internal data class EpisodeGroup(val start: Int, val end: Int, val label: String)

internal fun episodeGroups(episodes: List<Media>): List<EpisodeGroup> {
    if (episodes.size <= 50) return emptyList()
    return episodes.indices.step(50).map { start ->
        val end = (start + 50).coerceAtMost(episodes.size)
        val first = episodes[start].episode.takeIf { it > 0 } ?: (start + 1)
        val last = episodes[end - 1].episode.takeIf { it > 0 } ?: end
        EpisodeGroup(start, end, if (first == last) "$first 集" else "$first–$last 集")
    }
}
