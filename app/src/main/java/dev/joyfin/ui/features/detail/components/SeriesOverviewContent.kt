package dev.joyfin.ui.features.detail.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import dev.joyfin.browse.*
import dev.joyfin.browse.DetailUiState
import dev.joyfin.data.Media
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.SeriesFocusCoordinator
import dev.joyfin.ui.model.*

internal data class SeriesOverviewContent(
    val ui: DetailUiState,
    val images: ArtworkSource,
    val actions: BrowseActions,
    val focus: SeriesFocusCoordinator,
    val scroll: ScrollState,
    val previewList: LazyListState,
    val peopleList: LazyListState,
    val relatedList: LazyListState,
    val onSeason: () -> Unit,
    val onPreview: (Media) -> Unit,
    val playLabel: (Media?, String) -> String,
    val canPlay: (Media?, String) -> Boolean,
    val onPlay: (Media, Boolean, String?, String) -> Unit,
) {
    val target: Media?
        get() {
            val intent =
                when (val playback = ui.series?.playback) {
                    is EpisodePlaybackState.Preparing -> playback.intent
                    is EpisodePlaybackState.Failed -> playback.intent
                    is EpisodePlaybackState.Ready -> playback.intent
                    else -> null
                }
            return intent?.takeIf { it.trigger == "detail:play" }?.target
                ?: ui.series?.recommendation?.data
        }

    fun preparing(media: Media?, trigger: String): Boolean =
        (ui.series?.playback as? EpisodePlaybackState.Preparing)?.intent?.let {
            it.episodeId == media?.id && it.trigger == trigger
        } == true

    val feedback: String
        get() =
            when (val playback = ui.series?.playback) {
                is EpisodePlaybackState.Failed -> "${playback.intent.label} · ${playback.message}"
                else -> null
            }
                ?: ui.error
                ?: ui.series?.recommendation?.error
                ?: when {
                    ui.series?.recommendation is SeriesResource.Ready && target == null -> "暂无可播放内容"
                    else -> ""
                }
}
