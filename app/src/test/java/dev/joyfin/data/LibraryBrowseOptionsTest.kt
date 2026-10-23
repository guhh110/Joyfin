package dev.joyfin.data

import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class LibraryBrowseOptionsTest {
    @Test fun defaultsMatchHomeRailsAndChoosingNameResetsToAscending() {
        val defaults = LibraryBrowseOptions()
        assertEquals("DateCreated,SortName", defaults.sortBy)
        assertEquals("Descending", defaults.sortOrder)
        assertNull(defaults.watched.played)
        val names = defaults.selectSort(LibrarySort.NAME)
        assertEquals("SortName", names.sortBy)
        assertEquals("Ascending", names.sortOrder)
        assertTrue(names.selectSort(LibrarySort.YEAR).descending)
        assertEquals("CommunityRating,SortName", defaults.selectSort(LibrarySort.RATING).sortBy)
    }

    @Test fun selectedOptionsRoundTripAndMissingFieldsKeepDefaults() {
        val value = LibraryBrowseOptions(LibrarySort.YEAR, false, LibraryWatched.UNWATCHED)
        assertEquals(value, AppJson.decodeFromString<LibraryBrowseOptions>(AppJson.encodeToString(value)))
        assertEquals(LibraryBrowseOptions(), AppJson.decodeFromString<LibraryBrowseOptions>("{}"))
        assertEquals(false, value.watched.played)
        assertEquals(true, LibraryWatched.WATCHED.played)
    }

    @Test fun storageIdentitySeparatesLibrariesAccountsServersAndAllMediaButNotTokens() {
        val session = Session("https://example.invalid/jellyfin", "secret", "one", "Viewer")
        val key = LibraryBrowseStore.key(session, "library")
        assertEquals(key, LibraryBrowseStore.key(session.copy(token = "new", server = session.server + "/"), "library"))
        assertNotEquals(key, LibraryBrowseStore.key(session, "other"))
        assertNotEquals(key, LibraryBrowseStore.key(session.copy(userId = "two"), "library"))
        assertNotEquals(key, LibraryBrowseStore.key(session.copy(server = "https://elsewhere.invalid"), "library"))
        assertNotEquals(LibraryBrowseStore.key(session, null), LibraryBrowseStore.key(session, "all"))
    }
}
