package dev.joyfin.ui.model

import dev.joyfin.data.Media
import dev.joyfin.data.Person

/** UI issues commands; only the host/ViewModel owns navigation, persistence and networking. */
data class BrowseActions(
    val back: () -> Unit,
    val home: () -> Unit,
    val open: (Media) -> Unit,
    val openPerson: (Person) -> Unit,
    val selectEpisode: (Media) -> Unit,
    val retrySeriesSeasons: () -> Unit = {},
    val retrySeriesSeason: () -> Unit = {},
    val retrySeriesEpisode: () -> Unit = {},
    val retrySeriesRecommendation: () -> Unit = {},
    val prepareEpisode: (Media, Boolean, String?, String) -> Unit = { _, _, _, _ -> },
    val cancelEpisodePlayback: () -> Unit = {},
    val retryEpisodePlayback: () -> Unit = {},
    val retryRelated: () -> Unit = {},
    val retryHome: (dev.joyfin.browse.HomeRegion) -> Unit = {},
    val retryLibrary: (String) -> Unit,
    val selectSeason: (String) -> Unit,
    val library: (String?, String) -> Unit,
    val search: () -> Unit,
    val submitSearch: (String) -> Unit,
    val loadMore: () -> Unit,
    val settings: () -> Unit,
    val favorite: () -> Unit,
    val play: (Media, Boolean, String?) -> Unit,
    val retry: () -> Unit,
    val logout: () -> Unit,
    val setAutoNext: (Boolean) -> Unit,
    val subtitles: () -> Unit = {},
    val subtitleManager: () -> Unit = {},
)
