package dev.joyfin.browse

import dev.joyfin.app.*
import dev.joyfin.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class CollectionControllerTest {
    private class Fixture(scope: CoroutineScope, server: MockWebServer) {
        val saved = mutableMapOf<String?, LibraryBrowseOptions>()
        val detail =
            DetailUiState(
                item = Media("movie", "Movie", "Movie"),
                ready = true,
                error = "detail error",
            )
        val state = MutableStateFlow(UiState(page = Page.DETAIL, detail = detail))
        private val api =
            JellyfinApi("test").apply {
                session =
                    Session(server.url("/").toString().trimEnd('/'), "token", "user", "viewer")
            }
        private val requests = PageRequests(scope, state, { true }, { error("unexpected expiry") })
        private val navigation = NavigationController(state, requests, {}, {}, {}, {}, {}, {})
        val controller = CollectionController(api, state, requests, navigation::push,
            { saved[it] ?: LibraryBrowseOptions() }, { parent, value -> saved[parent] = value })

        suspend fun loaded(): CollectionUiState =
            withTimeout(5000) { state.first { !it.collection.busy }.collection }
    }

    @Test
    fun libraryLoadDoesNotReplaceMovieDetailAndResetsCollectionContext() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setBody(
                        """{"Items":[{"Id":"a","Name":"A","Type":"Movie"}],"TotalRecordCount":3}"""
                    )
            )
            val f = Fixture(this, server)
            f.controller.library("library", "My Library")
            val collection = f.loaded()
            assertEquals(CollectionKind.LIBRARY, collection.kind)
            assertEquals("My Library", collection.title)
            assertEquals("library", collection.parent)
            assertEquals("", collection.query)
            assertNull(collection.person)
            assertEquals(listOf("a"), collection.media.map { it.id })
            assertEquals(1, collection.nextOffset)
            assertEquals(3, collection.total)
            assertEquals(f.detail, f.state.value.detail)
        }
    }

    @Test
    fun personBiographyBelongsToCollectionWhileMovieDetailStaysUntouched() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setBody(
                        """{"Id":"actor","Name":"Actor","Type":"Person","Overview":"Biography"}"""
                    )
            )
            server.enqueue(
                MockResponse()
                    .setBody(
                        """{"Items":[{"Id":"work","Name":"Work","Type":"Movie"}],"TotalRecordCount":1}"""
                    )
            )
            val f = Fixture(this, server)
            f.controller.openPerson(Person("old-id", "Actor", "", "Actor", ""))
            val collection = f.loaded()
            assertEquals(CollectionKind.PERSON, collection.kind)
            assertEquals("actor", collection.person?.id)
            assertEquals("Biography", collection.personDetail?.overview)
            assertEquals(listOf("work"), collection.media.map { it.id })
            assertNull(collection.error)
            assertEquals(f.detail, f.state.value.detail)
        }
    }

    @Test
    fun repeatedSearchKeepsVisibleResultsWhileNewQueryClearsThem() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setBody(
                        """{"Items":[{"Id":"a","Name":"A","Type":"Movie"}],"TotalRecordCount":1}"""
                    )
            )
            server.enqueue(
                MockResponse()
                    .setBody(
                        """{"Items":[{"Id":"b","Name":"B","Type":"Movie"}],"TotalRecordCount":1}"""
                    )
                    .setHeadersDelay(100, java.util.concurrent.TimeUnit.MILLISECONDS)
            )
            server.enqueue(MockResponse().setBody("""{"Items":[],"TotalRecordCount":0}"""))
            val f = Fixture(this, server)
            f.controller.search()
            f.controller.submitSearch("story")
            f.loaded()
            f.controller.submitSearch("story")
            assertEquals(listOf("a"), f.state.value.collection.media.map { it.id })
            assertTrue(f.state.value.collection.busy)
            assertEquals(listOf("b"), f.loaded().media.map { it.id })
            f.controller.submitSearch("different")
            assertTrue(f.state.value.collection.media.isEmpty())
            assertTrue(f.state.value.collection.busy)
            assertTrue(f.loaded().media.isEmpty())
        }
    }

    @Test
    fun searchAppendUsesItsOwnOffsetAndKeepsUniqueCards() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setBody(
                        """{"Items":[{"Id":"a","Name":"A","Type":"Movie"},{"Id":"b","Name":"B","Type":"Movie"}],"TotalRecordCount":4}"""
                    )
            )
            server.enqueue(
                MockResponse()
                    .setBody(
                        """{"Items":[{"Id":"b","Name":"B","Type":"Movie"},{"Id":"c","Name":"C","Type":"Movie"}],"TotalRecordCount":4}"""
                    )
            )
            val f = Fixture(this, server)
            f.controller.search()
            f.controller.submitSearch(" story ")
            assertEquals(2, f.loaded().nextOffset)
            f.controller.loadItems(more = true)
            val collection = f.loaded()
            assertEquals(CollectionKind.SEARCH, collection.kind)
            assertEquals("story", collection.query)
            assertEquals(listOf("a", "b", "c"), collection.media.map { it.id })
            assertEquals(4, collection.nextOffset)
            server.takeRequest()
            val appendRequest = server.takeRequest()
            assertEquals("2", appendRequest.requestUrl?.queryParameter("StartIndex"))
            assertEquals(f.detail, f.state.value.detail)
        }
    }

    @Test fun libraryOptionsResetPagingPersistPerLibraryAndApplyToEveryPage() = runBlocking {
        MockWebServer().use { server ->
            fun response(id: String) = MockResponse().setBody("""{"Items":[{"Id":"$id","Type":"Movie"}],"TotalRecordCount":4}""")
            server.enqueue(response("old"))
            server.enqueue(response("filtered"))
            server.enqueue(response("more"))
            server.enqueue(response("other"))
            server.enqueue(response("restored"))
            val f = Fixture(this, server)
            f.controller.library("one", worksOnly = true)
            f.loaded()
            val initial = server.takeRequest().requestUrl!!
            assertEquals("DateCreated,SortName", initial.queryParameter("SortBy"))
            val options = LibraryBrowseOptions(LibrarySort.RATING, false, LibraryWatched.UNWATCHED)
            f.controller.arrangeLibrary(options)
            assertTrue(f.state.value.collection.media.isEmpty())
            assertEquals(0, f.state.value.collection.nextOffset)
            assertEquals(listOf("filtered"), f.loaded().media.map { it.id })
            f.controller.loadItems(true)
            assertEquals(listOf("filtered", "more"), f.loaded().media.map { it.id })
            val pages = listOf(server.takeRequest().requestUrl!!, server.takeRequest().requestUrl!!)
            assertEquals(listOf("0", "1"), pages.map { it.queryParameter("StartIndex") })
            pages.forEach {
                assertEquals("one", it.queryParameter("ParentId"))
                assertEquals("CommunityRating,SortName", it.queryParameter("SortBy"))
                assertEquals("Ascending", it.queryParameter("SortOrder"))
                assertEquals("false", it.queryParameter("IsPlayed"))
                assertEquals("true", it.queryParameter("Recursive"))
                assertEquals("Movie,Series,Video,MusicVideo", it.queryParameter("IncludeItemTypes"))
            }
            f.controller.library("two"); f.loaded()
            assertEquals(LibraryBrowseOptions(), f.state.value.collection.libraryOptions)
            f.controller.library("one"); f.loaded()
            assertEquals(options, f.state.value.collection.libraryOptions)
            assertEquals(options, f.saved["one"])
        }
    }

    @Test fun rootDirectoriesRemainUnfilteredWhileAllMediaUsesRecursiveWorksAndItsOwnPreference() = runBlocking {
        MockWebServer().use { server ->
            repeat(2) { server.enqueue(MockResponse().setBody("""{"Items":[],"TotalRecordCount":0}""")) }
            val f = Fixture(this, server)
            val options = LibraryBrowseOptions(watched = LibraryWatched.WATCHED)
            f.saved[null] = options
            f.controller.library(); f.loaded()
            assertFalse(f.state.value.collection.canArrangeLibrary)
            f.controller.arrangeLibrary(options.copy(descending = false))
            val root = server.takeRequest().requestUrl!!
            assertEquals("SortName", root.queryParameter("SortBy"))
            assertNull(root.queryParameter("IsPlayed"))
            assertNull(root.queryParameter("Recursive"))
            f.controller.library(title = "全部媒体", worksOnly = true); f.loaded()
            assertTrue(f.state.value.collection.canArrangeLibrary)
            val all = server.takeRequest().requestUrl!!
            assertEquals("true", all.queryParameter("Recursive"))
            assertEquals("Movie,Series,Video,MusicVideo", all.queryParameter("IncludeItemTypes"))
            assertEquals("true", all.queryParameter("IsPlayed"))
        }
    }

    @Test fun changingSortCancelsAnOldAppendAndKeepsTheNewResult() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"first","Type":"Movie"}],"TotalRecordCount":3}"""))
            server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"stale","Type":"Movie"}],"TotalRecordCount":3}""")
                .setHeadersDelay(200, java.util.concurrent.TimeUnit.MILLISECONDS))
            server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"new","Type":"Movie"}],"TotalRecordCount":1}"""))
            val f = Fixture(this, server)
            f.controller.library("one"); f.loaded(); server.takeRequest()
            f.controller.loadItems(true)
            withContext(Dispatchers.IO) { server.takeRequest() }
            f.controller.arrangeLibrary(LibraryBrowseOptions(sort = LibrarySort.YEAR))
            assertEquals(listOf("new"), f.loaded().media.map { it.id })
            delay(250)
            assertEquals(listOf("new"), f.state.value.collection.media.map { it.id })
            assertEquals(1, f.state.value.collection.nextOffset)
            assertFalse(f.state.value.collection.appending)
        }
    }
}
