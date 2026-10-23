package dev.joyfin.playback

import dev.joyfin.data.Media
import dev.joyfin.data.PlaybackPlan

/** Presentation data contains no player, API or authenticated streaming URL. */
data class TrackChoice(val index: Int, val title: String, val detail: String)

data class LocalSubtitleSelection(val label: String, val offsetMs: Long, val id: String = "")
data class LocalSubtitleOption(val id: String, val label: String)

data class VersionChoice(val id: String, val title: String)

data class PlayerUiState(
    val title: String = "",
    val subtitle: String = "",
    val playing: Boolean = false,
    val canSeek: Boolean = false,
    val seekPreview: SeekPreviewState? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val speed: Float = 1f,
    val speedBoosting: Boolean = false,
    val videoScaleMode: dev.joyfin.data.VideoScaleMode = dev.joyfin.data.VideoScaleMode.FIT,
    val method: String = "",
    val loading: Boolean = false,
    val hasRenderedFrame: Boolean = false,
    val error: String? = null,
    val audio: List<TrackChoice> = emptyList(),
    val subtitles: List<TrackChoice> = emptyList(),
    val audioIndex: Int? = null,
    val subtitleIndex: Int = -1,
    val bitrate: Int = dev.joyfin.data.PlaybackQuality.DEFAULT,
    val compatible: Boolean = false,
    val episodes: List<Media> = emptyList(),
    val seasons: List<Media> = emptyList(),
    val selectedSeasonId: String? = null,
    val itemId: String? = null,
    val versions: List<VersionChoice> = emptyList(),
    val sourceId: String? = null,
    val nextItem: Media? = null,
    val nextSeconds: Int = 0,
    val nextPreparing: Boolean = false,
    val nextPreparationError: String? = null,
    val episodeError: String? = null,
    val localSubtitle: LocalSubtitleSelection? = null,
    val localSubtitleError: String? = null,
    val subtitleApplying: Boolean = false,
    val localSubtitles: List<LocalSubtitleOption> = emptyList(),
    val subtitleSearchOpen: Boolean = false,
    val subtitleSearchApplied: Int = 0,
    val audioOutputMode: dev.joyfin.data.AudioOutputMode = dev.joyfin.data.AudioOutputMode.AUTO,
    val playbackNotice: String? = null,
    val navigation: PlaybackNavigationState = PlaybackNavigationState(),
)

data class PlayerActions(
    val toggle: () -> Unit,
    val preview: SeekPreviewActions,
    val seek: (Long) -> Unit,
    val selectEpisode: (Media) -> Unit,
    val selectSeason: (String) -> Unit,
    val selectAudio: (Int) -> Unit,
    val selectSubtitle: (Int) -> Unit,
    val selectBitrate: (Int) -> Unit,
    val speed: (Float) -> Unit,
    val selectVersion: (String) -> Unit,
    val compatible: () -> Unit,
    val selectCompatibility: (Boolean) -> Unit,
    val retry: () -> Unit,
    val cancelNext: () -> Unit,
    val nextNow: () -> Unit,
    val close: () -> Unit,
    val subtitleOffset: (Long) -> Unit = {},
    val selectLocalFile: (String) -> Unit = {},
    val searchSubtitles: () -> Unit = {},
    val setAudioOutputMode: (dev.joyfin.data.AudioOutputMode) -> Unit = {},
    val selectChapter: (Long) -> Unit = {},
    val setSkipPreferences: (dev.joyfin.data.SkipPreferences) -> Unit = {},
    val skipSegment: () -> Unit = {},
    val dismissSkip: () -> Unit = {},
    val undoSkip: () -> Unit = {},
    val retrySegments: () -> Unit = {},
    val setNavigationBlocked: (Boolean) -> Unit = {},
    val retryNext: () -> Unit = {},
    val speedBoost: SpeedBoostActions = SpeedBoostActions(),
    val setVideoScaleMode: (dev.joyfin.data.VideoScaleMode) -> Unit = {},
)

fun PlaybackPlan.trackChoices(type: String): List<TrackChoice> =
    streams
        .filter { it.type == type }
        .map { stream ->
            TrackChoice(
                stream.index,
                stream.displayTitle.ifBlank {
                    stream.language.ifBlank {
                        if (type == "Audio") "音轨 ${stream.index}" else "字幕 ${stream.index}"
                    }
                },
                listOf(
                        stream.codec.uppercase(),
                        stream.channels.takeIf { it > 0 }?.let { "$it 声道" }.orEmpty(),
                    )
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
            )
        }
