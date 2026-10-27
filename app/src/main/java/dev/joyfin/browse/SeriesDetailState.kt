package dev.joyfin.browse

import dev.joyfin.data.Media

data class SeriesDetailState(
    val seriesId: String,
    val entrySeasonId: String? = null,
    val entryEpisodeId: String? = null,
    val entryError: String? = null,
    val seasons: SeriesResource<List<Media>> = SeriesResource.Idle,
    val recommendation: SeriesResource<Media?> = SeriesResource.Idle,
    val seasonId: String? = null,
    val episodes: SeriesResource<List<Media>> = SeriesResource.Idle,
    val inspectedEpisodeId: String? = null,
    val requestedEpisodeId: String? = null,
    val episodeDetails: SeriesResource<Media> = SeriesResource.Idle,
    val remembered: Map<String, String> = emptyMap(),
    val seasonCache: Map<String, List<Media>> = emptyMap(),
    val playback: EpisodePlaybackState = EpisodePlaybackState.Idle,
    val episodeActionBusy: Boolean = false,
) {
    val inspected: Media?
        get() =
            episodeDetails.data?.takeIf { it.id == inspectedEpisodeId }
                ?: episodes.data?.find { it.id == inspectedEpisodeId }
}
