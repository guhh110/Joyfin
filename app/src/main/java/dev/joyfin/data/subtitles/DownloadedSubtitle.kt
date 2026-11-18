package dev.joyfin.data.subtitles

/** Files from a player download, kept in memory until applied or dismissed. */
data class DownloadedSubtitle(
    val id: String,
    val owner: String,
    val files: List<DownloadedSubtitleFile>,
)

data class DownloadedSubtitleFile(val fileId: String, val name: String)
