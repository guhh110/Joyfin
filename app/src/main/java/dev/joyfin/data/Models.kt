package dev.joyfin.data

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.format.DateTimeParseException

@Serializable
data class Session(val server: String, val token: String, val userId: String, val userName: String)

data class Person(
    val id: String,
    val name: String,
    val role: String,
    val type: String,
    val imageTag: String,
)

data class Media(
    val id: String,
    val name: String,
    val type: String,
    val overview: String = "",
    val year: Int = 0,
    val ticks: Long = 0,
    val resumeTicks: Long = 0,
    val imageTag: String = "",
    val imageId: String = id,
    val played: Boolean = false,
    val seriesId: String = "",
    val season: Int = 0,
    val episode: Int = 0,
    val sources: List<MediaSource> = emptyList(),
    val backdropId: String = id,
    val backdropTag: String = "",
    val logoId: String = id,
    val logoTag: String = "",
    val people: List<Person> = emptyList(),
    val genres: List<String> = emptyList(),
    val rating: Double? = null,
    val officialRating: String = "",
    val premiereDate: String = "",
    val seasonId: String = "",
    val seriesName: String = "",
    val childCount: Int = 0,
    val unplayedCount: Int? = null,
    val favorite: Boolean = false,
    val locations: List<String> = emptyList(),
    val language: String = "",
    val collectionType: String = "",
    val parentId: String = "",
    val originalTitle: String = "",
    val providerIds: Map<String, String> = emptyMap(),
    val seriesImageTag: String = "",
    val episodeImageTag: String = "",
    val path: String = "",
    /** Server viewing timestamp in epoch milliseconds; missing or invalid dates stay unknown. */
    val lastPlayedAt: Long? = null,
    val chapters: List<MediaChapter> = emptyList(),
) {
    val isFolder
        get() = type in setOf("Series", "Season", "CollectionFolder", "Folder", "BoxSet")

    val label
        get() = if (type == "Episode") "S${season} E${episode} · $name" else name

    companion object {
        fun from(dto: ItemDto): Media {
            val ownPrimary = dto.imageTags["Primary"].orEmpty().validText()
            val backdrop = dto.backdropTags.firstOrNull().orEmpty().validText()
            val logo = dto.imageTags["Logo"].orEmpty().validText()
            return Media(
                id = dto.id,
                name = dto.name.validText().ifBlank { "未命名" },
                originalTitle = dto.originalTitle.validText(),
                providerIds = dto.providerIds.mapNotNull { (key, value) ->
                    value?.takeIf { it.isNotBlank() }?.let { key.lowercase(java.util.Locale.ROOT) to it }
                }.toMap(),
                type = dto.type,
                path = dto.path,
                overview = dto.overview.validText(),
                year = dto.year,
                ticks = dto.ticks,
                chapters = dto.chapters,
                resumeTicks = dto.userData.positionTicks,
                lastPlayedAt = dto.userData.lastPlayedDate?.let {
                    try { Instant.parse(it).toEpochMilli() }
                    catch (_: DateTimeParseException) { null }
                    catch (_: ArithmeticException) { null }
                },
                imageTag =
                    ownPrimary.ifBlank {
                        dto.parentPrimaryImageTag.validText().ifBlank {
                            dto.seriesImageTag.validText()
                        }
                    },
                imageId =
                    if (ownPrimary.isNotBlank()) dto.id
                    else
                        dto.parentPrimaryImageId.validText().ifBlank {
                            dto.seriesId.validText().ifBlank { dto.id }
                        },
                played = dto.userData.played,
                seriesId = dto.seriesId.validText(),
                season = dto.season,
                episode = dto.episode,
                sources = dto.sources,
                backdropId =
                    if (backdrop.isNotBlank()) dto.id
                    else dto.parentBackdropId.validText().ifBlank { dto.id },
                backdropTag =
                    backdrop.ifBlank { dto.parentBackdropTags.firstOrNull().orEmpty().validText() },
                logoId =
                    if (logo.isNotBlank()) dto.id
                    else dto.parentLogoId.validText().ifBlank { dto.id },
                logoTag = logo.ifBlank { dto.parentLogoTag.validText() },
                people =
                    dto.people.map {
                        Person(
                            it.id.validText(),
                            it.name.validText(),
                            it.role.validText(),
                            it.type.validText(),
                            it.primaryImageTag.validText(),
                        )
                    },
                genres = dto.genres.filterNotNull().filter { it.isNotBlank() && it != "null" },
                rating = dto.rating?.takeIf { it.isFinite() && it > 0 },
                officialRating = dto.officialRating.validText(),
                premiereDate = dto.premiereDate.validText().take(10),
                seasonId = dto.seasonId.validText(),
                seriesName = dto.seriesName.validText(),
                childCount = dto.childCount,
                unplayedCount = dto.userData.unplayedCount,
                favorite = dto.userData.favorite,
                locations =
                    dto.locations.filterNotNull().filter { it.isNotBlank() && it != "null" },
                language = dto.language.validText(),
                collectionType = dto.collectionType.validText(),
                parentId = dto.parentId.validText(),
                seriesImageTag = dto.seriesImageTag.validText(),
                episodeImageTag = ownPrimary.takeIf { dto.type == "Episode" }.orEmpty(),
            )
        }
    }
}

data class MediaPage(val items: List<Media>, val total: Int)

/** One audio-selection rule for capability probes, negotiation and diagnostics. */
fun MediaSource.audioStream(index: Int? = null): MediaStream? =
    streams.firstOrNull { it.type == "Audio" && it.index == (index ?: defaultAudioIndex) }
        ?: streams.firstOrNull { it.type == "Audio" }

/** Transport policy supplied by playback business logic; the API never probes hardware. */
data class PlaybackSourcePolicy(
    val profile: DeviceProfileDto,
    val videoTranscode: Boolean = false,
    val audioTranscode: Boolean = false,
    val discSource: Boolean = false,
    val maxAudioChannels: Int? = null,
)

data class PlaybackPlan(
    val item: Media,
    val source: MediaSource,
    val sessionId: String,
    val url: String,
    val method: String,
    val startTicks: Long,
    val audioIndex: Int?,
    val subtitleIndex: Int,
    val bitrate: Int,
    val subtitles: List<Subtitle>,
    val transcodeReasons: List<String> = emptyList(),
) {
    val mimeType: String? get() = when {
        url.substringBefore('?').endsWith(".m3u8", true) -> "application/x-mpegURL"
        method == "Transcode" && source.transcodingSubProtocol.equals("hls", true) -> "application/x-mpegURL"
        method == "Transcode" && source.transcodingContainer.equals("mp4", true) -> "video/mp4"
        else -> null
    }
    val sourceId
        get() = source.id

    val streams
        get() = source.streams
}

data class Subtitle(
    val url: String,
    val mime: String,
    val language: String,
    val label: String,
    val index: Int = -1,
)

class ApiException(val status: Int, override val message: String) : Exception(message)

private fun String.validText() = takeUnless { it == "null" }.orEmpty()

fun ItemsResponse.toMediaPage() = MediaPage(items.map(Media::from), total)
