package dev.joyfin.ui.model

import dev.joyfin.data.Media
import dev.joyfin.data.Person

/** URL construction only. Authentication stays in the host and is attached to image requests. */
data class ArtworkSource(
    val poster: (Media) -> String,
    val backdrop: (Media) -> String,
    val episode: (Media) -> String?,
    val person: (Person) -> String?,
    val authorization: String,
    val logo: (Media) -> String? = { null },
    val cacheNamespace: String = "",
)
