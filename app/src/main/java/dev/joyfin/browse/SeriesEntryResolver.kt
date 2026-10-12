package dev.joyfin.browse

import dev.joyfin.data.Media

/** A season/episode preview must contain only explicitly known parent-series information. */
fun Media.detailPreview(): Media? {
    if (type !in setOf("Season", "Episode")) return this
    if (seriesId.isBlank()) return null
    return Media(
        id = seriesId,
        name = seriesName,
        type = "Series",
        imageId = seriesId,
        imageTag = seriesImageTag,
        backdropId = seriesId,
        backdropTag = backdropTag.takeIf { backdropId == seriesId }.orEmpty(),
    )
}

/** Resolve incomplete search/resume items without ever guessing a different series. */
suspend fun resolveSeriesEntry(entry: Media, item: suspend (String) -> Media): Media {
    val resolved = item(entry.id)
    if (resolved.seriesId.isNotBlank()) return resolved
    if (entry.seriesId.isNotBlank()) return resolved.copy(seriesId = entry.seriesId)
    val parentId = resolved.seasonId.ifBlank { resolved.parentId }.ifBlank { entry.seasonId }
    require(parentId.isNotBlank() && parentId != entry.id) { "服务器未提供所属剧集，请重试" }
    val parent = item(parentId)
    val seriesId =
        when (parent.type) {
            "Series" -> parent.id
            "Season" -> parent.seriesId.ifBlank { parent.parentId }
            else -> ""
        }
    require(seriesId.isNotBlank()) { "服务器未提供所属剧集，请重试" }
    return resolved.copy(
        seriesId = seriesId,
        seasonId =
            resolved.seasonId.ifBlank { parent.id.takeIf { parent.type == "Season" }.orEmpty() },
    )
}
