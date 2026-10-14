package dev.joyfin.data.subtitles

import kotlinx.serialization.Serializable

/** Persisted local workflow only; never a common subtitle-site response model. */
@Serializable
data class SubtitleMapping(
    val itemId: String,
    val itemName: String,
    val versionId: String,
    val versionName: String,
    val versionPath: String,
    val episode: Int = 0,
)

@Serializable
data class ManagedSubtitleFile(
    val fileId: String,
    val name: String,
    val mapping: SubtitleMapping? = null,
    val upload: String = "",
    val uploadError: String? = null,
    val uploadBefore: List<String>? = null,
    val manualMapping: Boolean = false,
)

@Serializable
data class SubtitleReceipt(
    val itemId: String,
    val path: String,
    val fileId: String,
    val mapping: SubtitleMapping,
    val language: String,
    val forced: Boolean,
    val hearingImpaired: Boolean,
)

@Serializable
data class ManagedSubtitle(
    val id: String,
    val owner: String,
    val title: String,
    val origin: String,
    val created: Long,
    val files: List<ManagedSubtitleFile>,
    val workId: String? = null,
    val workName: String = "",
    val seasonId: String? = null,
    val seasonName: String = "",
    val language: String = "chi",
    val forced: Boolean = false,
    val hearingImpaired: Boolean = false,
    val localSaved: Boolean = false,
    val boundFiles: List<ManagedSubtitleFile> = emptyList(),
    val defaults: Map<String, String> = emptyMap(),
    val receipts: List<SubtitleReceipt> = emptyList(),
    val pendingDeletes: List<SubtitleReceipt> = emptyList(),
    val cleanupError: String? = null,
) {
    val matched: Int get() = files.count { it.mapping != null }
    val pending: Boolean get() = !localSaved || matched < files.size
    val failed: Boolean get() = cleanupError != null || files.any { it.upload in setOf("failed", "uncertain", "sending", "identifying") }
    val uploaded: Int get() = files.count { it.mapping != null && it.upload == "done" }
}
