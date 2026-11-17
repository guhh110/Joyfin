package dev.joyfin.ui.features.home.components

import dev.joyfin.browse.HomeRegion
import dev.joyfin.browse.TrackingUiState
import dev.joyfin.data.Home
import dev.joyfin.data.HomeLayoutPreferences
import dev.joyfin.data.Media
import dev.joyfin.data.trackingOrder

/** Stable library/item identities keep selection intact while independent regions refresh. */
internal data class CineramaCollection(
    val id: String,
    val title: String,
    val items: List<Media>,
    val loading: Boolean = false,
    val error: String? = null,
    val region: HomeRegion? = null,
    val library: Media? = null,
)

internal fun cineramaCollections(
    home: Home,
    layout: HomeLayoutPreferences,
    tracking: TrackingUiState?,
): List<CineramaCollection> = buildList {
    if (tracking != null && (tracking.entries.isNotEmpty() || tracking.loading || tracking.error != null)) {
        add(CineramaCollection("tracking", "我的追剧", tracking.entries.sortedWith(trackingOrder).map { it.series },
            tracking.loading, tracking.error))
    }
    layout.visibleKeys(home.libraries.map { it.id }).forEach { key ->
        when (key) {
            "resume" -> if (home.resume.isNotEmpty() || home.resumeLoad.loading || home.resumeLoad.error != null) {
                add(CineramaCollection(key, "继续观看", home.resume,
                    home.resumeLoad.loading, home.resumeLoad.error, HomeRegion.RESUME))
            }
            "latest" -> if (home.latest.isNotEmpty() || home.latestLoad.loading || home.latestLoad.error != null) {
                add(CineramaCollection(key, "最近加入", home.latest,
                    home.latestLoad.loading, home.latestLoad.error, HomeRegion.LATEST))
            }
            else -> home.rails.firstOrNull { "library:${it.library.id}" == key }?.let { rail ->
                add(CineramaCollection(key, rail.library.name, rail.items,
                    rail.loading, rail.error, library = rail.library))
            }
        }
    }
    // A failed directory request must remain retryable even when other regions succeeded.
    if (home.librariesLoad.error != null && none { it.library != null }) {
        add(CineramaCollection("libraries", "媒体库", emptyList(), home.librariesLoad.loading,
            home.librariesLoad.error, HomeRegion.LIBRARIES))
    }
}

internal fun wrappedIndex(index: Int, delta: Int, count: Int): Int =
    if (count <= 0) 0 else ((index + delta) % count + count) % count
