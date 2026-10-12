package dev.joyfin.playback

import dev.joyfin.data.Media
import dev.joyfin.data.PlaybackPlan

/** Presentation data contains no player, API or authenticated streaming URL. */
data class TrackChoice(val index: Int, val title: String, val detail: String)

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
    val method: String = "",
    val loading: Boolean = false,
    val hasRenderedFrame: Boolean = false,
    val error: String? = null,
    val audio: List<TrackChoice> = emptyList(),
    val subtitles: List<TrackChoice> = emptyList(),
    val audioIndex: Int? = null,
    val subtitleIndex: Int = -1,
    val bitrate: Int = 80_000_000,
    val episodes: List<Media> = emptyList(),
    val seasons: List<Media> = emptyList(),
    val selectedSeasonId: String? = null,
    val itemId: String? = null,
    val versions: List<VersionChoice> = emptyList(),
    val sourceId: String? = null,
    val nextItem: Media? = null,
    val nextSeconds: Int = 0,
    val episodeError: String? = null,
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
    val retry: () -> Unit,
    val cancelNext: () -> Unit,
    val nextNow: () -> Unit,
    val close: () -> Unit,
)

fun PlaybackPlan.trackChoices(type: String): List<TrackChoice> =
    streams
        .filter { it.optString("Type") == type }
        .map { stream ->
            TrackChoice(
                stream.optInt("Index"),
                stream.optString("DisplayTitle").ifBlank {
                    stream.optString("Language").ifBlank {
                        if (type == "Audio") "音轨 ${stream.optInt("Index")}"
                        else "字幕 ${stream.optInt("Index")}"
                    }
                },
                listOf(
                        stream.optString("Codec").uppercase(),
                        stream.optInt("Channels").takeIf { it > 0 }?.let { "$it 声道" }.orEmpty(),
                    )
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
            )
        }
