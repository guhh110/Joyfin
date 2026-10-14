package dev.joyfin.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Jellyfin UploadSubtitleDto: Data is the base64 of the unshifted UTF-8 file. */
@Serializable
data class UploadSubtitleRequest(
    @SerialName("Language") val language: String,
    @SerialName("Format") val format: String,
    @SerialName("IsForced") val forced: Boolean,
    @SerialName("IsHearingImpaired") val hearingImpaired: Boolean,
    @SerialName("Data") val data: String,
)
