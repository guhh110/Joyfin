package dev.joyfin.app

import dev.joyfin.browse.*
import dev.joyfin.session.EntryState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PageStateIsolationTest {
    @Test
    fun foregroundFailureUpdatesOnlyItsOwningPage() = runTest {
        val initial =
            UiState(
                entry = EntryState(error = "entry"),
                home = HomeUiState(error = "home"),
                collection = CollectionUiState(error = "collection"),
                detail = DetailUiState(error = "detail"),
                settings = SettingsUiState(error = "settings"),
            )
        for (page in Page.entries) {
            if (page == Page.SUBTITLES) continue
            val before = initial.copy(page = page)
            val state = MutableStateFlow(before)
            val requests = PageRequests(this, state, { false }, {})
            requests.run({}) { error("request failed") }
            advanceUntilIdle()
            val after = state.value
            assertEquals("request failed", after.activeRequest.error)
            assertFalse(after.activeRequest.busy)
            if (!page.isEntry) assertEquals(before.entry, after.entry)
            if (page != Page.HOME) assertEquals(before.home, after.home)
            if (page !in setOf(Page.LIBRARY, Page.SEARCH, Page.PERSON))
                assertEquals(before.collection, after.collection)
            if (page != Page.DETAIL) assertEquals(before.detail, after.detail)
            if (page != Page.SETTINGS) assertEquals(before.settings, after.settings)
        }
    }
}
