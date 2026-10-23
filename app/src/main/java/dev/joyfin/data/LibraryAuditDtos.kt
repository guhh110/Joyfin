package dev.joyfin.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Audit wire data deliberately preserves missing/null versus explicitly empty fields. */
@Serializable data class AuditItemDto(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("Overview") val overview: String? = null,
    @SerialName("ProductionYear") val year: Int? = null,
    @SerialName("RunTimeTicks") val ticks: Long? = null,
    @SerialName("ImageTags") val images: Map<String, String?>? = null,
    @SerialName("ProviderIds") val providers: Map<String, String?>? = null,
    @SerialName("ParentIndexNumber") val season: Int? = null,
    @SerialName("IndexNumber") val episode: Int? = null,
    @SerialName("IndexNumberEnd") val episodeEnd: Int? = null,
    @SerialName("SeriesId") val seriesId: String? = null,
    @SerialName("SeriesName") val seriesName: String? = null,
    @SerialName("PremiereDate") val premiere: String? = null,
    @SerialName("LocationType") val location: String? = null,
    @SerialName("IsPlaceHolder") val placeholder: Boolean? = null,
    @SerialName("MediaSources") val sources: List<AuditSourceDto>? = null,
    @SerialName("MediaStreams") val streams: List<AuditStreamDto>? = null,
)
@Serializable data class AuditSourceDto(
    @SerialName("Id") val id: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("Container") val container: String? = null,
    @SerialName("Size") val size: Long? = null,
    @SerialName("RunTimeTicks") val ticks: Long? = null,
    @SerialName("MediaStreams") val streams: List<AuditStreamDto>? = null,
)
@Serializable data class AuditStreamDto(
    @SerialName("Type") val type: String? = null,
    @SerialName("Codec") val codec: String? = null,
    @SerialName("Language") val language: String? = null,
    @SerialName("Width") val width: Int? = null,
    @SerialName("Height") val height: Int? = null,
)
@Serializable data class AuditPageDto(
    @SerialName("Items") val items: List<AuditItemDto>? = null,
    @SerialName("TotalRecordCount") val total: Int? = null,
)
