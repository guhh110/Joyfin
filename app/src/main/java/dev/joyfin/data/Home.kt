package dev.joyfin.data

data class LibraryRailState(
    val library: Media,
    val items: List<Media> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

data class RegionLoad(
    val loading: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
)

data class Home(
    val resume: List<Media>,
    val latest: List<Media>,
    val libraries: List<Media>,
    val rails: List<LibraryRailState> = emptyList(),
    val resumeLoad: RegionLoad = RegionLoad(),
    val latestLoad: RegionLoad = RegionLoad(),
    val librariesLoad: RegionLoad = RegionLoad(),
)

fun Media.isVideoLibrary(): Boolean =
    collectionType.lowercase() in
        setOf("movies", "tvshows", "homevideos", "musicvideos", "mixed")

/** Keep server ordering and only one entry per updated series. */
fun groupLatestVideo(items: List<Media>): List<Media> =
    items
        .mapNotNull { media ->
            when (media.type) {
                "Movie",
                "Series",
                "Video",
                "MusicVideo" -> media
                "Episode",
                "Season" ->
                    media.seriesId
                        .takeIf { it.isNotBlank() }
                        ?.let {
                            Media(
                                it,
                                media.seriesName.ifBlank { media.name },
                                "Series",
                                imageTag = media.seriesImageTag,
                                imageId = it,
                                backdropId =
                                    media.backdropId.takeUnless { id -> id == media.id } ?: it,
                                backdropTag = media.backdropTag,
                            )
                        }
                else -> null
            }
        }
        .distinctBy { it.id }
        .take(20)
