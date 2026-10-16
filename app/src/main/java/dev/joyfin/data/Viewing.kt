package dev.joyfin.data

/** Follow the latest viewing activity instead of the earliest leftover resume position. */
fun preferredEpisode(episodes: List<Media>): Media? {
    val episodeOrder = compareBy<Media> { it.season }.thenBy { it.episode }
    val ordered = episodes.sortedWith(episodeOrder)
    val numbered = ordered.any { it.season > 0 }
    val latest = ordered.filter {
        (it.played || it.resumeTicks > 0) &&
            (it.season > 0 || it.inProgress() || !numbered)
    }.maxWithOrNull(compareBy<Media> { it.lastPlayedAt ?: Long.MIN_VALUE }.then(episodeOrder))
    if (latest != null) {
        if (latest.inProgress()) return latest
        ordered.firstOrNull {
            episodeOrder.compare(it, latest) > 0 && !it.finished() && (it.season > 0 || !numbered)
        }?.let { return it }
        // Do not send the user back to old progress when they reached the latest available episode.
        if (ordered.any { !it.finished() && (it.season > 0 || !numbered) }) return latest
    }
    return ordered.firstOrNull { !it.finished() && it.season > 0 }
        ?: ordered.firstOrNull { !it.finished() }
        ?: ordered.firstOrNull { it.season > 0 }
        ?: ordered.firstOrNull()
}

private fun Media.finished() = played || (ticks > 0 && resumeTicks >= ticks)
private fun Media.inProgress() = !finished() && resumeTicks > 0

fun Media.seasonSummary(): String = listOfNotNull(
    childCount.takeIf { it > 0 }?.let { "$it 集" },
    when {
        played || (childCount > 0 && unplayedCount == 0) -> "已看完"
        childCount > 0 && unplayedCount != null -> "已看 ${(childCount - unplayedCount).coerceIn(0, childCount)} 集"
        else -> null
    },
    year.takeIf { it > 0 }?.toString(),
).joinToString(" · ")
