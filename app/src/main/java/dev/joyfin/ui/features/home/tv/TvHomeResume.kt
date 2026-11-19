package dev.joyfin.ui.features.home.tv

import dev.joyfin.data.Media

internal fun tvHomeResumeIdentity(item: Media): String =
    if (item.type == "Episode" && item.seriesId.isNotBlank()) "series:${item.seriesId}" else "item:${item.id}"

internal fun tvHomeResumeItems(items: List<Media>): List<Media> =
    items.sortedByDescending { it.lastPlayedAt ?: Long.MIN_VALUE }.distinctBy(::tvHomeResumeIdentity)
