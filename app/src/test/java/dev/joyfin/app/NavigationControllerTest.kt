package dev.joyfin.app

import dev.joyfin.browse.*
import dev.joyfin.data.Media
import dev.joyfin.data.Person
import dev.joyfin.data.RegionLoad
import dev.joyfin.session.EntryState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NavigationControllerTest {
    private class Fixture(scope: CoroutineScope, initial: UiState) {
        val state = MutableStateFlow(initial)
        val requests = PageRequests(scope, state, { true }, {})
        var cancellations = 0
        var homeRefreshes = 0
        var relatedRetries = 0
        var detailRestores = 0
        var entryCancellations = 0
        val navigation =
            NavigationController(
                state,
                requests,
                { cancellations++ },
                { homeRefreshes++ },
                { relatedRetries++ },
                { detailRestores++ },
                {
                    entryCancellations++
                    requests.cancel()
                    state.value = state.value.copy(entry = state.value.entry.copy(busy = false))
                },
                { state.value = UiState(entry = state.value.entry) },
            )
    }

    @Test
    fun backRestoresPageDataAndItsRetryCommand() = runTest {
        val item = Media("movie", "Movie", "Movie")
        val f =
            Fixture(
                this,
                UiState(
                    page = Page.LIBRARY,
                    collection = CollectionUiState(media = listOf(item), query = "saved"),
                ),
            )
        var retried = 0
        f.requests.retry = { retried++ }
        f.navigation.push(Page.SEARCH)
        f.requests.retry = {}
        f.state.value =
            f.state.value.copy(
                collection = f.state.value.collection.copy(media = emptyList(), query = "new")
            )
        assertTrue(f.navigation.back())
        assertEquals(Page.LIBRARY, f.state.value.page)
        assertEquals(listOf(item), f.state.value.collection.media)
        assertEquals("saved", f.state.value.collection.query)
        assertEquals(0, retried)
        f.requests.retry?.invoke()
        assertEquals(1, retried)
    }

    @Test
    fun backRestartsInterruptedDetailRequest() = runTest {
        val f =
            Fixture(
                this,
                UiState(page = Page.DETAIL, detail = DetailUiState(busy = true, ready = false)),
            )
        var retried = 0
        f.requests.retry = { retried++ }
        f.navigation.settings()
        assertTrue(f.navigation.back())
        assertEquals(Page.DETAIL, f.state.value.page)
        assertEquals(1, retried)
        assertEquals(0, f.detailRestores)
    }

    @Test
    fun backToLoadedDetailRestartsOnlyItsInterruptedRegions() = runTest {
        val f =
            Fixture(
                this,
                UiState(
                    page = Page.DETAIL,
                    detail = DetailUiState(ready = true, relatedLoad = RegionLoad(loading = true)),
                ),
            )
        f.navigation.settings()
        assertTrue(f.navigation.back())
        assertEquals(1, f.relatedRetries)
        assertEquals(1, f.detailRestores)
        assertEquals(0, f.homeRefreshes)
    }

    @Test
    fun backToHomeRefreshesRegionsAndHomeCommandClearsHistory() = runTest {
        val f = Fixture(this, UiState(page = Page.HOME))
        f.navigation.push(Page.SEARCH)
        assertTrue(f.navigation.back())
        assertEquals(1, f.homeRefreshes)
        f.navigation.settings()
        f.navigation.home()
        assertEquals(2, f.homeRefreshes)
        assertEquals(Page.HOME, f.state.value.page)
        assertFalse(f.navigation.back())
    }

    @Test
    fun busyEntryBackCancelsWithoutChangingPageOrInputs() = runTest {
        val entry = EntryState(server = "http://cinema", userName = "viewer", busy = true)
        val f = Fixture(this, UiState(page = Page.LOGIN, entry = entry))
        assertTrue(f.navigation.back())
        assertEquals(Page.LOGIN, f.state.value.page)
        assertEquals("viewer", f.state.value.entry.userName)
        assertEquals(1, f.entryCancellations)
        assertFalse(f.state.value.entry.busy)
        assertTrue(f.navigation.back())
        assertEquals(Page.CONNECT, f.state.value.page)
        assertEquals("http://cinema", f.state.value.entry.server)
        assertFalse(f.navigation.back())
    }

    @Test
    fun nestedPersonAndMovieVisitsRestoreTheirOwnContentAndPagination() = runTest {
        val original =
            CollectionUiState(
                title = "Library",
                parent = "library",
                media = listOf(Media("a", "A", "Movie")),
                total = 80,
                nextOffset = 40,
            )
        val f = Fixture(this, UiState(page = Page.LIBRARY, collection = original))
        f.navigation.push(Page.PERSON)
        assertEquals(CollectionKind.PERSON, f.state.value.collection.kind)
        assertNull(f.state.value.collection.parent)
        val biography = Media("actor", "Actor", "Person", overview = "Biography")
        val person =
            f.state.value.collection.copy(
                person = Person("actor", "Actor", "", "Actor", ""),
                personDetail = biography,
                media = listOf(Media("b", "B", "Movie")),
                total = 20,
                nextOffset = 1,
            )
        f.state.value = f.state.value.copy(collection = person)
        f.navigation.push(Page.DETAIL)
        f.state.value =
            f.state.value.copy(
                detail =
                    DetailUiState(
                        item = Media("b", "B", "Movie"),
                        ready = true,
                        error = "movie error",
                    )
            )
        assertTrue(f.navigation.back())
        assertEquals(Page.PERSON, f.state.value.page)
        assertEquals(person, f.state.value.collection)
        assertEquals("Biography", f.state.value.collection.personDetail?.overview)
        assertNull(f.state.value.collection.error)
        assertTrue(f.navigation.back())
        assertEquals(Page.LIBRARY, f.state.value.page)
        assertEquals(original, f.state.value.collection)
    }
}
