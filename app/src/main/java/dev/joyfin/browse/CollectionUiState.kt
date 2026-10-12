package dev.joyfin.browse

import dev.joyfin.data.Media
import dev.joyfin.data.Person

enum class CollectionKind {
    LIBRARY,
    SEARCH,
    PERSON,
}

/** Library, search and person filmography share paging, but never detail-page state. */
data class CollectionUiState(
    val kind: CollectionKind = CollectionKind.LIBRARY,
    val title: String = "",
    val parent: String? = null,
    val query: String = "",
    val media: List<Media> = emptyList(),
    val total: Int = 0,
    val nextOffset: Int = 0,
    val appending: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val person: Person? = null,
    val personDetail: Media? = null,
)
