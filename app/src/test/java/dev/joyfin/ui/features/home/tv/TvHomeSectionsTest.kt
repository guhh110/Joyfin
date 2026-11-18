package dev.joyfin.ui.features.home.tv

import dev.joyfin.data.*
import org.junit.Assert.*
import org.junit.Test

class TvHomeSectionsTest {
    private val movie = Media("movie", "影片", "Movie")
    private val library = Media("a", "服务器片库", "CollectionFolder")

    @Test fun savedOrderAndHiddenLibrariesApplyWithoutChangingServerData() {
        val home = Home(listOf(movie), listOf(movie), listOf(library),
            listOf(LibraryRailState(library, listOf(movie), loading = false)))
        val layout = HomeLayoutPreferences(order = listOf("library:a", "latest", "resume"), hidden = setOf("latest"))
        val sections = tvHomeSections(home, layout)
        assertEquals(listOf("library:a", "resume"), sections.map { it.key })
        assertEquals("服务器片库", sections.first().title)
        assertEquals("a", sections.first().libraryId)
        assertSame(movie, sections.first().items.single())
        assertEquals("继续观看:movie", sections.last().mediaKey(movie))
    }

    @Test fun emptyCompletedPersonalRowsDisappearButFailuresAndLoadingRemainReachable() {
        val empty = Home(emptyList(), emptyList(), emptyList(),
            resumeLoad = RegionLoad(loaded = true), latestLoad = RegionLoad(loaded = true),
            librariesLoad = RegionLoad(loaded = true))
        assertTrue(tvHomeSections(empty, HomeLayoutPreferences()).isEmpty())
        val pending = empty.copy(resumeLoad = RegionLoad(loading = true), latestLoad = RegionLoad(error = "offline"))
        val sections = tvHomeSections(pending, HomeLayoutPreferences())
        assertEquals(listOf("resume", "latest"), sections.map { it.key })
        assertTrue(sections.first().load.loading)
        assertEquals("offline", sections.last().load.error)
    }

    @Test fun discoveredLibraryKeepsItsIdentityWhileItsItemsLoadAndWhenItIsEmpty() {
        val home = Home(emptyList(), emptyList(), listOf(library))
        val pending = tvHomeSections(home, HomeLayoutPreferences()).single()
        assertEquals("library:a", pending.key)
        assertTrue(pending.load.loading)
        val loaded = tvHomeSections(home.copy(rails = listOf(LibraryRailState(library, loading = false))),
            HomeLayoutPreferences()).single()
        assertEquals(pending.key, loaded.key)
        assertTrue(loaded.load.loaded)
        assertTrue(loaded.hasMore)
    }

    @Test fun libraryDiscoveryFailureHasOneRetrySectionAndHidingEverythingKeepsSettingsReachable() {
        val home = Home(emptyList(), listOf(movie), emptyList(), librariesLoad = RegionLoad(error = "offline"))
        val sections = tvHomeSections(home, HomeLayoutPreferences())
        assertEquals(listOf("latest", "libraries"), sections.map { it.key })
        assertEquals("home:libraries:retry", sections.last().retryKey)
        val hidden = HomeLayoutPreferences(hidden = setOf("resume", "latest", "library:a"))
        assertEquals(listOf("layout"), tvHomeSections(home.copy(libraries = listOf(library)), hidden).map { it.key })
    }
}
