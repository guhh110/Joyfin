package dev.joyfin.app

import dev.joyfin.browse.*
import dev.joyfin.session.EntryState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PageRequestsTest {
    @Test
    fun entryRequestDoesNotOverwriteBrowseLoadingState() = runTest {
        val state =
            MutableStateFlow(
                UiState(
                    page = Page.CONNECT,
                    collection = CollectionUiState(busy = true, error = "browse"),
                )
            )
        val requests = PageRequests(this, state, { false }, {})
        requests.run({}) { error("entry") }
        advanceUntilIdle()
        assertTrue(state.value.collection.busy)
        assertEquals("browse", state.value.collection.error)
        assertFalse(state.value.entry.busy)
        assertEquals("entry", state.value.entry.error)
    }

    @Test
    fun browseRequestDoesNotOverwriteEntryState() = runTest {
        val entry = EntryState(server = "http://cinema", error = "entry")
        val state = MutableStateFlow(UiState(page = Page.SEARCH, entry = entry))
        val requests = PageRequests(this, state, { false }, {})
        requests.run({}) { error("browse") }
        advanceUntilIdle()
        assertEquals(entry, state.value.entry)
        assertEquals("browse", state.value.collection.error)
        assertFalse(state.value.collection.busy)
    }

    @Test
    fun oldRequestFailureAndCompletionCannotClearNewRequestLoading() = runTest {
        val state = MutableStateFlow(UiState(page = Page.SEARCH))
        val requests = PageRequests(this, state, { false }, {})
        requests.run({}) {
            withContext(NonCancellable) { delay(100) }
            error("old request")
        }
        runCurrent()
        requests.run({}) { delay(1000) }
        runCurrent()
        advanceTimeBy(200)
        runCurrent()
        assertTrue(state.value.collection.busy)
        assertNull(state.value.collection.error)
        advanceUntilIdle()
        assertFalse(state.value.collection.busy)
    }
}
