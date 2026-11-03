package dev.joyfin.data

import java.util.Locale
import kotlinx.serialization.Serializable

/** Source IDs belong to individual items; only a unique named version may span episodes. */
internal data class SkipPreferenceTarget(val media: Media, val sourceId: String) {
    private val source = media.sources.firstOrNull { it.id == sourceId }
    private val name = source?.name.orEmpty().trim().lowercase(Locale.ROOT)
    private val container = source?.container.orEmpty().trim().lowercase(Locale.ROOT)
    private val namedVersion = name.isNotBlank() && media.sources.count {
        it.name.trim().lowercase(Locale.ROOT) == name &&
            it.container.trim().lowercase(Locale.ROOT) == container
    } == 1
    private val season = when {
        media.type != "Episode" || media.seriesId.isBlank() -> "item:${media.id}"
        media.seasonId.isNotBlank() -> "season:${media.seasonId}"
        media.season > 0 -> "season-index:${media.season}"
        else -> "item:${media.id}"
    }
    private val shared = media.type == "Episode" && !season.startsWith("item:") && namedVersion
    val identity = SkipScopeIdentity(
        season = season,
        item = if (shared) "" else media.id,
        source = if (shared) "" else sourceId,
        version = if (shared) name else "",
        container = if (shared) container else "",
    )
    val label: String = listOf(
        when {
            media.type != "Episode" -> "当前电影"
            !shared -> "当前集"
            media.season == 0 -> "特别篇"
            else -> "第 ${media.season} 季"
        },
        source?.name?.takeIf { it.isNotBlank() } ?: "当前版本",
    ).joinToString(" · ")
}

@Serializable
internal data class SkipScopeIdentity(
    val season: String,
    val item: String,
    val source: String,
    val version: String,
    val container: String,
)
