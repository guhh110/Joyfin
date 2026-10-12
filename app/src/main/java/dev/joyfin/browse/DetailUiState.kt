package dev.joyfin.browse

import dev.joyfin.data.Media
import dev.joyfin.data.RegionLoad

/** One movie/series visit, including episode state and independent related-content loading. */
data class DetailUiState(
    val item: Media? = null,
    val entryType: String = "",
    val visit: Long = 0,
    val ready: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val favoriteBusy: Boolean = false,
    val series: SeriesDetailState? = null,
    val related: List<Media> = emptyList(),
    val relatedLoad: RegionLoad = RegionLoad(),
)
