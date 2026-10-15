package dev.joyfin.subtitles

import dev.joyfin.data.Media

/** Optional work facts. A standalone keyword search has no media binding. */
data class SubtitleSearchContext(
    val owner: String,
    val work: Media? = null,
    val seasonMedia: Media? = null,
    val episode: Int? = null,
    val seasonNumber: Int? = null,
) {
    val season: Int? get() = seasonMedia?.episode ?: seasonNumber
}
