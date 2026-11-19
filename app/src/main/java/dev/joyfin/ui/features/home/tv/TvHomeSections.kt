package dev.joyfin.ui.features.home.tv

import dev.joyfin.data.Home
import dev.joyfin.data.HomeLayoutPreferences
import dev.joyfin.data.Media
import dev.joyfin.data.RegionLoad

internal data class TvHomeSection(
    val key: String,
    val title: String,
    val items: List<Media> = emptyList(),
    val load: RegionLoad = RegionLoad(),
    val libraryId: String? = null,
) {
    // Resume dialogs already restore these identities; do not tie them to card geometry.
    val focusPrefix: String get() = when (key) {
        "resume" -> "继续观看"
        "latest" -> "最近加入"
        else -> key
    }
    fun selectionKey(item: Media) = if (key == "resume") tvHomeResumeIdentity(item) else item.id
    fun mediaKey(item: Media) = "$focusPrefix:${item.id}"
    val retryKey: String get() = "home:$key:retry"
    val moreKey: String get() = "home:$key:more"
    val hasMore: Boolean get() = key == "latest" || key == "libraries" || libraryId != null
}

/** The same server regions and saved ordering as mobile, presented one viewport at a time. */
internal fun tvHomeSections(home: Home, layout: HomeLayoutPreferences): List<TvHomeSection> = buildList {
    val keys = layout.visibleKeys(home.libraries.map { it.id })
    keys.forEach { key ->
        val section = when (key) {
            "resume" -> TvHomeSection(key, "继续观看", tvHomeResumeItems(home.resume), home.resumeLoad)
            "latest" -> TvHomeSection(key, "最近加入", home.latest, home.latestLoad)
            else -> home.rails.firstOrNull { "library:${it.library.id}" == key }?.let {
                TvHomeSection(key, it.library.name, it.items,
                    RegionLoad(it.loading, it.error, !it.loading && it.error == null), it.library.id)
            } ?: home.libraries.firstOrNull { "library:${it.id}" == key }?.let {
                TvHomeSection(key, it.name, load = RegionLoad(loading = true), libraryId = it.id)
            }
        }
        if (section != null && (section.libraryId != null || section.items.isNotEmpty() ||
                section.load.loading || section.load.error != null)) add(section)
    }
    if (home.libraries.isEmpty() && (home.librariesLoad.loading || home.librariesLoad.error != null))
        add(TvHomeSection("libraries", "媒体库", load = home.librariesLoad))
    if (keys.isEmpty()) add(TvHomeSection("layout", "首页栏目已隐藏"))
}
