package dev.joyfin.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
enum class LibrarySort(val label: String, val field: String, val defaultDescending: Boolean = true) {
    ADDED("添加时间", "DateCreated"),
    YEAR("年份", "ProductionYear"),
    RATING("评分", "CommunityRating"),
    NAME("名称", "SortName", false),
}

@Serializable
enum class LibraryWatched(val label: String, val played: Boolean?) {
    ALL("全部", null), WATCHED("已看", true), UNWATCHED("未看", false),
}

/** Server sorting/filtering happens before paging; the UI never reorders a partial page. */
@Serializable
data class LibraryBrowseOptions(
    val sort: LibrarySort = LibrarySort.ADDED,
    val descending: Boolean = true,
    val watched: LibraryWatched = LibraryWatched.ALL,
) {
    fun selectSort(value: LibrarySort) = copy(sort = value, descending = value.defaultDescending)
    val sortBy: String get() = if (sort == LibrarySort.NAME) sort.field else "${sort.field},SortName"
    val sortOrder: String get() = if (descending) "Descending" else "Ascending"
}

/** Independent preferences per server, account and library; null identifies the all-media view. */
internal class LibraryBrowseStore(context: Context) {
    private val prefs = context.getSharedPreferences("library-browse", Context.MODE_PRIVATE)
    fun load(session: Session?, parent: String?): LibraryBrowseOptions = session?.let {
        runCatching {
            AppJson.decodeFromString<LibraryBrowseOptions>(prefs.getString(key(it, parent), "{}") ?: "{}")
        }.getOrDefault(LibraryBrowseOptions())
    } ?: LibraryBrowseOptions()

    fun save(session: Session?, parent: String?, value: LibraryBrowseOptions) {
        session ?: return
        prefs.edit().putString(key(session, parent), AppJson.encodeToString(value)).apply()
    }

    companion object {
        internal fun key(session: Session, parent: String?) = PlaybackPreferenceStore.key(session,
            Media("library-browse:${parent?.let { "parent:$it" } ?: "all"}", "", "Folder"))
    }
}
