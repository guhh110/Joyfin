package dev.joyfin.ui.features.home.tv

import dev.joyfin.browse.TrackingUiState
import dev.joyfin.data.*

internal enum class TvHomeFeaturedReason(val label: String) {
    RESUME("继续观看"), NEXT_EPISODE("接着看下一集"), DISCOVER("最近加入"),
}

internal data class TvHomeFeaturedItem(
    val key: String,
    val media: Media,
    val details: Media,
    val playback: Media?,
    val reason: TvHomeFeaturedReason,
    val activityAt: Long? = null,
) {
    val title: String get() = media.seriesName.ifBlank { media.name }
    val playLabel: String get() = when {
        playback == null -> "查看剧集"
        playback.type == "Episode" -> "继续 S${playback.season} E${playback.episode}"
        reason == TvHomeFeaturedReason.RESUME -> "继续观看"
        else -> "播放"
    }
    val progress: Float? get() = playback?.takeIf { it.ticks > 0 && it.resumeTicks > 0 }
        ?.let { (it.resumeTicks.toDouble() / it.ticks).toFloat().coerceIn(0f, 1f) }
}

internal fun tvHomeFeaturedItems(
    resume: List<Media>,
    tracking: TrackingUiState?,
    discovery: List<Media>,
    selectedKey: String? = null,
): List<TvHomeFeaturedItem> {
    fun identity(media: Media) = if (media.type == "Episode") media.seriesId.ifBlank { media.id } else media.id
    val tracked = tracking?.entries.orEmpty().associateBy { it.series.id }
    val continuing = resume.filter {
        it.type in setOf("Movie", "Episode") && !it.virtual && !it.played &&
            it.resumeTicks > 0 && (it.ticks <= 0 || it.resumeTicks < it.ticks)
    }.map { item ->
        val key = identity(item)
        val series = tracked[key]?.series ?: discovery.firstOrNull { it.id == key && it.type == "Series" }
        val details = if (item.type == "Episode" && item.seriesId.isNotBlank())
            series ?: Media(item.seriesId, item.seriesName.ifBlank { item.name }, "Series",
                imageId = item.seriesId, imageTag = item.seriesImageTag,
                backdropId = item.backdropId, backdropTag = item.backdropTag)
        else item
        TvHomeFeaturedItem(key, series ?: item, details, item, TvHomeFeaturedReason.RESUME, item.lastPlayedAt)
    } + tracked.values.mapNotNull { entry ->
        val progress = entry.progress ?: return@mapNotNull null
        val target = progress.target ?: return@mapNotNull null
        if (progress.status != TrackingStatus.WATCHING || target.virtual || target.played ||
            target.ticks > 0 && target.resumeTicks >= target.ticks) return@mapNotNull null
        TvHomeFeaturedItem(entry.series.id, entry.series, entry.series, target,
            if (target.resumeTicks > 0) TvHomeFeaturedReason.RESUME else TvHomeFeaturedReason.NEXT_EPISODE,
            progress.lastPlayedAt ?: entry.series.lastPlayedAt)
    }
    val continuation = continuing.sortedByDescending { it.activityAt ?: Long.MIN_VALUE }
        .distinctBy { it.key }
    val occupied = continuation.mapTo(hashSetOf()) { it.key }
    val discoveries = discovery.filter {
        it.type in setOf("Movie", "Series") && !it.virtual && !it.played &&
            it.id !in occupied && (it.type != "Movie" || it.ticks <= 0 || it.resumeTicks < it.ticks)
    }.distinctBy { it.id }.map {
        TvHomeFeaturedItem(it.id, it, it, it.takeUnless { media -> media.isFolder }, TvHomeFeaturedReason.DISCOVER)
    }
    val resumeCount = minOf(continuation.size, 3 + (3 - discoveries.size).coerceAtLeast(0))
    val discoverCount = minOf(discoveries.size, 6 - resumeCount)
    fun retainSelection(pool: List<TvHomeFeaturedItem>, count: Int): List<TvHomeFeaturedItem> {
        val chosen = pool.take(count).toMutableList()
        val selected = pool.firstOrNull { it.key == selectedKey }
        if (selected != null && chosen.isNotEmpty() && chosen.none { it.key == selectedKey })
            chosen[chosen.lastIndex] = selected
        return chosen
    }
    val chosenResume = retainSelection(continuation, resumeCount)
    val chosenDiscovery = retainSelection(discoveries, discoverCount)
    return buildList {
        repeat(maxOf(resumeCount, discoverCount)) { index ->
            if (index < resumeCount) add(chosenResume[index])
            if (index < discoverCount) add(chosenDiscovery[index])
        }
    }
}
