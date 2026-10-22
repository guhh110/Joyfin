package dev.joyfin.data

import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class HomeLayoutPreferencesTest {
    @Test fun defaultsPreserveTheExistingHomeAndNewLibrariesAppendWithoutDuplicates() {
        assertEquals(listOf("resume", "latest", "library:a", "library:b"),
            HomeLayoutPreferences().visibleKeys(listOf("a", "b")))
        val preferences = HomeLayoutPreferences(order = listOf("library:a", "resume", "library:a"))
        assertEquals(listOf("library:a", "resume", "latest", "library:b"), preferences.visibleKeys(listOf("a", "b")))
    }
    @Test fun movingVisibleRowsPreservesUnavailableLibrariesAndHiddenRows() {
        val preferences = HomeLayoutPreferences(order = listOf("library:a", "resume", "latest", "library:b"), hidden = setOf("latest"))
        val moved = preferences.move("library:b", -1, listOf("b"))
        assertEquals(listOf("resume", "library:b"), moved.visibleKeys(listOf("b")))
        assertEquals(listOf("library:a", "resume", "library:b", "latest"), moved.orderedKeys(listOf("a", "b")))
        assertEquals(setOf("latest"), moved.hidden)
        assertEquals(moved, moved.move("missing", 1, listOf("b")))
    }
    @Test fun preferencesRoundTripAndHidingEverythingNeverDeletesLibraryData() {
        val preferences = HomeLayoutPreferences().toggle("resume").toggle("latest").toggle("library:a")
        val decoded = AppJson.decodeFromString<HomeLayoutPreferences>(AppJson.encodeToString(preferences))
        assertTrue(decoded.visibleKeys(listOf("a")).isEmpty())
        assertEquals(listOf("resume", "latest", "library:a"), decoded.orderedKeys(listOf("a")))
        assertEquals(listOf("resume"), decoded.toggle("resume").visibleKeys(listOf("a")))
    }
}
