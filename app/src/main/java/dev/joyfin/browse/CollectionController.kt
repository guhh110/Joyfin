package dev.joyfin.browse

import dev.joyfin.app.*
import dev.joyfin.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

/** Library, search and person filmography own the same collection request flow. */
internal class CollectionController(
    private val api: JellyfinApi,
    private val mutable: MutableStateFlow<UiState>,
    private val requests: PageRequests,
    private val push: (Page) -> Unit,
    private val loadLibraryOptions: (String?) -> LibraryBrowseOptions = { LibraryBrowseOptions() },
    private val saveLibraryOptions: (String?, LibraryBrowseOptions) -> Unit = { _, _ -> },
) {
    fun library(id: String? = null, title: String = "媒体库", worksOnly: Boolean = false) {
        push(Page.LIBRARY)
        mutable.value =
            mutable.value.copy(
                collection =
                    mutable.value.collection.copy(
                        media = emptyList(),
                        total = 0,
                        nextOffset = 0,
                        title = title,
                        parent = id,
                        query = "",
                        worksOnly = worksOnly,
                        libraryOptions = loadLibraryOptions(id),
                    )
            )
        loadItems()
    }

    fun arrangeLibrary(value: LibraryBrowseOptions) {
        val current = mutable.value.collection
        if (mutable.value.page != Page.LIBRARY || !current.canArrangeLibrary || current.libraryOptions == value) return
        saveLibraryOptions(current.parent, value)
        // A different query must start from zero, including while an old page is still arriving.
        mutable.value = mutable.value.copy(collection = current.copy(libraryOptions = value,
            media = emptyList(), total = 0, nextOffset = 0, appending = false, error = null))
        loadItems()
    }

    fun search() {
        push(Page.SEARCH)
        mutable.value =
            mutable.value.copy(
                collection =
                    mutable.value.collection.copy(
                        media = emptyList(),
                        total = 0,
                        nextOffset = 0,
                        title = "搜索",
                        parent = null,
                        query = "",
                    )
            )
    }

    fun submitSearch(query: String) {
        if (query.isBlank()) {
            mutable.value =
                mutable.value.copy(collection = mutable.value.collection.copy(error = "请输入搜索名称"))
            return
        }
        mutable.value =
            mutable.value.copy(
                collection =
                    mutable.value.collection.copy(
                        query = query.trim(),
                        media =
                            if (query.trim() == mutable.value.collection.query)
                                mutable.value.collection.media
                            else emptyList(),
                        total =
                            if (query.trim() == mutable.value.collection.query)
                                mutable.value.collection.total
                            else 0,
                        nextOffset =
                            if (query.trim() == mutable.value.collection.query)
                                mutable.value.collection.nextOffset
                            else 0,
                    )
            )
        loadItems()
    }

    fun loadItems(more: Boolean = false) {
        if (more && (mutable.value.collection.busy ||
                mutable.value.collection.nextOffset >= mutable.value.collection.total)) return
        val snapshot = mutable.value.collection
        mutable.value =
            mutable.value.copy(collection = mutable.value.collection.copy(appending = more))
        requests.run({ loadItems(more) }) {
            val start = if (more) snapshot.nextOffset else 0
            val result =
                if (snapshot.kind == CollectionKind.PERSON)
                    api.personItems(requireNotNull(snapshot.person).id, start)
                else api.items(snapshot.parent, snapshot.query, start,
                    snapshot.libraryOptions.takeIf { snapshot.canArrangeLibrary }, snapshot.worksOnly)
            currentCoroutineContext().ensureActive()
            val root = snapshot.kind == CollectionKind.LIBRARY &&
                snapshot.parent == null && snapshot.query.isBlank()
            val items = if (root) result.items.filter { it.isVideoLibrary() } else result.items
            mutable.value =
                mutable.value.copy(
                    collection =
                        mutable.value.collection.copy(
                            media =
                                if (more) (snapshot.media + items).distinctBy { it.id }
                                else items,
                            total = if (root) items.size else result.total,
                            nextOffset = start + items.size,
                        )
                )
        }
    }

    fun openPerson(person: Person) {
        push(Page.PERSON)
        mutable.value =
            mutable.value.copy(
                collection =
                    mutable.value.collection.copy(
                        person = person,
                        personDetail = null,
                        media = emptyList(),
                        total = 0,
                    )
            )
        loadPerson(person)
    }

    private fun loadPerson(person: Person): Unit =
        requests.run({ loadPerson(person) }) {
            val detail = api.person(person.name)
            currentCoroutineContext().ensureActive()
            val resolved =
                person.copy(id = detail.id, imageTag = detail.imageTag.ifBlank { person.imageTag })
            mutable.value =
                mutable.value.copy(
                    collection =
                        mutable.value.collection.copy(person = resolved, personDetail = detail)
                )
            val result = api.personItems(resolved.id)
            currentCoroutineContext().ensureActive()
            mutable.value =
                mutable.value.copy(
                    collection =
                        mutable.value.collection.copy(
                            media = result.items,
                            total = result.total,
                            nextOffset = result.items.size,
                        )
                )
        }
}
