package dev.joyfin.ui.features.detail

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import dev.joyfin.browse.*
import dev.joyfin.data.Media
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.detail.components.*
import dev.joyfin.ui.features.detail.mobile.MobileEpisodeDetails
import dev.joyfin.ui.features.detail.tv.TvEpisodeDetails
import dev.joyfin.ui.model.*

@Composable
internal fun EpisodeDetails(
    episode: Media,
    details: SeriesResource<Media>,
    images: ArtworkSource,
    mediaInfo: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onPlay: (Media, Boolean, String?) -> Unit,
    onMediaInfo: () -> Unit,
    onDetails: () -> Unit,
    feedback: String?,
    playLabel: (Media, String) -> String,
    canPlay: (Media, String) -> Boolean,
    isPreparing: (Media, String) -> Boolean = { _, _ -> false },
    onTogglePlayed: (Media) -> Unit = {},
    actionBusy: Boolean = false,
) {
    if (LocalDeviceLayout.current.television) {
        TvEpisodeDetails(
            episode,
            details,
            images,
            mediaInfo,
            onDismiss,
            onRetry,
            onPlay,
            onMediaInfo,
            onDetails,
            feedback,
            playLabel,
            canPlay,
            isPreparing,
            onTogglePlayed,
            actionBusy,
        )
    } else {
        MobileEpisodeDetails(
            episode,
            details,
            images,
            mediaInfo,
            onDismiss,
            onRetry,
            onPlay,
            onMediaInfo,
            feedback,
            playLabel,
            canPlay,
            isPreparing,
            onTogglePlayed,
            actionBusy,
        )
    }
}
