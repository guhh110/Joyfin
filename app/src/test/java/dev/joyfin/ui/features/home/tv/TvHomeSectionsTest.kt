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

    @Test fun resumeGroupsSeriesByTheirLatestActivityAndKeepsTheActualEpisode() {
        val older = Media("older", "第一集", "Episode", seriesId = "series", lastPlayedAt = 10)
        val latest = older.copy(id = "latest", name = "第二集", lastPlayedAt = 30,
            ticks = 100, resumeTicks = 40, imageTag = "episode-art")
        val other = older.copy(id = "other", seriesId = "other-series", lastPlayedAt = 20)
        val rows = tvHomeResumeItems(listOf(older, other, latest))
        assertEquals(listOf("latest", "other"), rows.map { it.id })
        assertSame(latest, rows.first())
        val section = TvHomeSection("resume", "继续观看")
        assertEquals(section.selectionKey(older), section.selectionKey(latest))
        assertEquals("继续观看:latest", section.mediaKey(latest))
    }

    @Test fun resumeKeepsDistinctSeriesWithTheSameTitleAndMoviesWithCollidingSeriesIds() {
        val episode = Media("episode", "第一集", "Episode", seriesId = "series",
            seriesName = "同名剧", lastPlayedAt = 30)
        val other = episode.copy(id = "other", seriesId = "other-series", lastPlayedAt = 10)
        val film = movie.copy(id = "series", lastPlayedAt = 20)
        assertEquals(listOf(episode, film, other), tvHomeResumeItems(listOf(other, film, episode)))
    }

    @Test fun resumeKeepsUnidentifiedEpisodesSeparateAndDeduplicatesRepeatedItemIds() {
        val episode = Media("episode", "同名集", "Episode", seriesName = "同名剧")
        val other = episode.copy(id = "other", seriesId = " ")
        val recentMovie = movie.copy(lastPlayedAt = 20, resumeTicks = 50)
        val rows = tvHomeResumeItems(listOf(episode, movie, other, recentMovie, episode))
        assertEquals(listOf(recentMovie, episode, other), rows)
        assertSame(recentMovie, rows.first())
    }

    @Test fun resumeUsesServerOrderForEqualAndMissingViewingDates() {
        val first = Media("first", "第一集", "Episode", seriesId = "series", lastPlayedAt = 10)
        val tied = first.copy(id = "tied")
        val unknown = first.copy(id = "unknown", lastPlayedAt = null)
        val nextSeries = first.copy(id = "next", seriesId = "next-series")
        val unknownFirst = first.copy(id = "unknown-first", seriesId = "unknown-series", lastPlayedAt = null)
        val unknownNext = unknownFirst.copy(id = "unknown-next")
        val rows = tvHomeResumeItems(listOf(unknown, unknownFirst, first, movie, tied, nextSeries, unknownNext))
        assertEquals(listOf(first, nextSeries, unknownFirst, movie), rows)
        assertTrue(tvHomeResumeItems(emptyList()).isEmpty())
    }

    @Test fun resumeProjectionDoesNotChangeSourceOrOtherSections() {
        val first = Media("first", "第一集", "Episode", seriesId = "series")
        val second = first.copy(id = "second", lastPlayedAt = 10)
        val source = listOf(first, second)
        val home = Home(source, source, emptyList())
        val sections = tvHomeSections(home, HomeLayoutPreferences())
        assertEquals(listOf(second), sections.first { it.key == "resume" }.items)
        assertSame(source, sections.first { it.key == "latest" }.items)
        assertSame(source, home.resume)
        assertEquals(listOf(first, second), source)
    }
}
