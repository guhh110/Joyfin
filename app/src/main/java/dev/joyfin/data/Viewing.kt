package dev.joyfin.data

/** Resume the in-progress episode; otherwise prefer numbered seasons over unwatched specials. */
fun preferredEpisode(episodes: List<Media>): Media? {
    val ordered = episodes.sortedWith(compareBy<Media> { it.season }.thenBy { it.episode })
    return ordered.firstOrNull { !it.played && it.resumeTicks > 0 && (it.ticks <= 0 || it.resumeTicks < it.ticks) }
        ?: ordered.firstOrNull { !it.played && it.season > 0 }
        ?: ordered.firstOrNull { !it.played }
        ?: ordered.firstOrNull { it.season > 0 }
        ?: ordered.firstOrNull()
}

fun Media.seasonSummary(): String = listOfNotNull(
    childCount.takeIf { it > 0 }?.let { "$it 集" },
    when {
        played || (childCount > 0 && unplayedCount == 0) -> "已看完"
        childCount > 0 && unplayedCount != null -> "已看 ${(childCount - unplayedCount).coerceIn(0, childCount)} 集"
        else -> null
    },
    year.takeIf { it > 0 }?.toString(),
).joinToString(" · ")
