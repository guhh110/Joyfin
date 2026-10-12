package dev.joyfin.data

import org.json.JSONArray
import org.json.JSONObject

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
    val sources: List<JSONObject> = emptyList(),
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
    val seriesImageTag: String = "",
    val episodeImageTag: String = "",
) {
    val isFolder
        get() = type in setOf("Series", "Season", "CollectionFolder", "Folder", "BoxSet")

    val label
        get() = if (type == "Episode") "S${season} E${episode} · $name" else name

    companion object {
        fun from(json: JSONObject): Media {
            val user = json.optJSONObject("UserData") ?: JSONObject()
            val tags = json.optJSONObject("ImageTags")
            val id = json.getString("Id")
            val ownPrimary = tags?.text("Primary").orEmpty()
            val imageId =
                if (ownPrimary.isNotBlank()) id
                else
                    json.text("ParentPrimaryImageItemId").ifBlank {
                        json.text("SeriesId").ifBlank { id }
                    }
            val backdrop = json.optJSONArray("BackdropImageTags").strings().firstOrNull().orEmpty()
            val logo = tags?.text("Logo").orEmpty()
            return Media(
                seriesImageTag = json.text("SeriesPrimaryImageTag"),
                episodeImageTag = ownPrimary.takeIf { json.text("Type") == "Episode" }.orEmpty(),
                parentId = json.text("ParentId"),
                collectionType = json.text("CollectionType"),
                id = id,
                name = json.text("Name").ifBlank { "未命名" },
                type = json.text("Type"),
                overview = json.text("Overview"),
                year = json.optInt("ProductionYear"),
                ticks = json.optLong("RunTimeTicks"),
                resumeTicks = user.optLong("PlaybackPositionTicks"),
                imageTag =
                    ownPrimary.ifBlank {
                        json.text("ParentPrimaryImageTag").ifBlank {
                            json.text("SeriesPrimaryImageTag")
                        }
                    },
                imageId = imageId,
                played = user.optBoolean("Played"),
                seriesId = json.text("SeriesId"),
                season = json.optInt("ParentIndexNumber"),
                episode = json.optInt("IndexNumber"),
                sources = json.optJSONArray("MediaSources").objects(),
                backdropId =
                    if (backdrop.isNotBlank()) id
                    else json.text("ParentBackdropItemId").ifBlank { id },
                backdropTag =
                    backdrop.ifBlank {
                        json
                            .optJSONArray("ParentBackdropImageTags")
                            .strings()
                            .firstOrNull()
                            .orEmpty()
                    },
                logoId =
                    if (logo.isNotBlank()) id else json.text("ParentLogoItemId").ifBlank { id },
                logoTag = logo.ifBlank { json.text("ParentLogoImageTag") },
                people =
                    json.optJSONArray("People").objects().map {
                        Person(
                            it.text("Id"),
                            it.text("Name"),
                            it.text("Role"),
                            it.text("Type"),
                            it.text("PrimaryImageTag"),
                        )
                    },
                genres = json.optJSONArray("Genres").strings(),
                rating = json.optDouble("CommunityRating").takeIf { it.isFinite() && it > 0 },
                officialRating = json.text("OfficialRating"),
                premiereDate = json.text("PremiereDate").take(10),
                seasonId = json.text("SeasonId"),
                seriesName = json.text("SeriesName"),
                childCount = json.optInt("ChildCount"),
                unplayedCount =
                    user.optInt("UnplayedItemCount").takeIf {
                        user.has("UnplayedItemCount") && !user.isNull("UnplayedItemCount")
                    },
                favorite = user.optBoolean("IsFavorite"),
                locations = json.optJSONArray("ProductionLocations").strings(),
                language = json.text("OriginalLanguage"),
            )
        }
    }
}

data class MediaPage(val items: List<Media>, val total: Int)

data class PlaybackPlan(
    val item: Media,
    val source: JSONObject,
    val sessionId: String,
    val url: String,
    val method: String,
    val startTicks: Long,
    val audioIndex: Int?,
    val subtitleIndex: Int,
    val bitrate: Int,
    val subtitles: List<Subtitle>,
) {
    val sourceId
        get() = source.optString("Id")

    val streams
        get() = source.optJSONArray("MediaStreams").objects()
}

data class Subtitle(
    val url: String,
    val mime: String,
    val language: String,
    val label: String,
    val index: Int = -1,
)

class ApiException(val status: Int, override val message: String) : Exception(message)

fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

fun JSONObject.text(key: String): String = optString(key).takeUnless { it == "null" }.orEmpty()

fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList()
    else
        (0 until length()).mapNotNull {
            optString(it).takeIf { s -> s.isNotBlank() && s != "null" }
        }

fun JSONObject.mediaPage() =
    MediaPage(optJSONArray("Items").objects().map(Media::from), optInt("TotalRecordCount"))
