package dev.joyfin.data

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import dev.joyfin.browse.TrackingController
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test

class TrackingApiTest {
    @Test fun anotherClientCanAddRemoveAndUpdateProgressThroughTheSameUserData() = runBlocking {
        MockWebServer().use { server ->
            val selected = java.util.concurrent.atomic.AtomicBoolean(false)
            val position = java.util.concurrent.atomic.AtomicLong(0)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val url = request.requestUrl!!
                    if (url.queryParameter("UserId") != "user") return MockResponse().setResponseCode(403)
                    if (url.encodedPath == "/UserFavoriteItems/s") {
                        selected.set(request.method == "POST")
                        return MockResponse().setResponseCode(204)
                    }
                    val rows = if (url.queryParameter("IncludeItemTypes") == "Episode")
                        """{"Id":"e1","Name":"E1","Type":"Episode","ParentIndexNumber":1,"IndexNumber":1,"RunTimeTicks":100,"UserData":{"PlaybackPositionTicks":${position.get()}}}"""
                    else if (selected.get()) """{"Id":"s","Name":"Show","Type":"Series","UserData":{"IsFavorite":true}}""" else ""
                    return MockResponse().setBody("""{"Items":[$rows],"TotalRecordCount":${if (rows.isEmpty()) 0 else 1}}""")
                }
            }
            server.start()
            fun client() = JellyfinApi("device").apply { session = Session(server.url("/").toString(), "token", "user", "User") }
            val phone = client()
            val tv = client()
            val controller = TrackingController(this, { tv.session }, tv::favoriteSeries,
                tv::trackingEpisodes, tv::favorite, { fail("Unexpected expiration") })
            suspend fun refresh() {
                controller.refresh(all = true, force = true)
                withTimeout(5_000) { controller.state.first { it.loaded && !it.loading } }
            }
            try {
                refresh()
                assertTrue(controller.state.value.empty)
                phone.favorite("s", true)
                refresh()
                assertEquals(TrackingStatus.WAITING, controller.state.value.entries.single().progress!!.status)
                position.set(40)
                refresh()
                assertEquals(40L, controller.state.value.entries.single().progress!!.target!!.resumeTicks)
                phone.favorite("s", false)
                refresh()
                assertTrue(controller.state.value.empty)
            } finally { controller.clear() }
        }
    }

    @Test fun favoritesAreScopedToUserAndSeriesAndSupportPaging() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"s","Type":"Series","UserData":{"IsFavorite":true}}],"TotalRecordCount":41}"""))
            server.start()
            val api = JellyfinApi("device").apply { session = Session(server.url("/").toString(), "token", "user", "User") }
            val page = api.favoriteSeries(40)
            assertEquals(41, page.total)
            val query = server.takeRequest().requestUrl!!
            assertEquals("user", query.queryParameter("UserId"))
            assertEquals("Series", query.queryParameter("IncludeItemTypes"))
            assertEquals("true", query.queryParameter("IsFavorite"))
            assertEquals("true", query.queryParameter("Recursive"))
            assertEquals("40", query.queryParameter("StartIndex"))
            assertTrue(query.queryParameter("Fields")!!.contains("Overview"))
        }
    }

    @Test fun lightweightEpisodesReadAllPagesAndExcludeVirtualPlaceholders() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"e1","Type":"Episode","ParentIndexNumber":1,"IndexNumber":1,"UserData":{"PlaybackPositionTicks":12}}],"TotalRecordCount":3}"""))
            server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"virtual","Type":"Episode","LocationType":"Virtual"},{"Id":"e2","Type":"Episode","ParentIndexNumber":1,"IndexNumber":2}],"TotalRecordCount":3}"""))
            server.start()
            val api = JellyfinApi("device").apply { session = Session(server.url("/").toString(), "token", "user", "User") }
            val episodes = api.trackingEpisodes("series")
            assertEquals(listOf("e1", "e2"), episodes.map { it.id })
            assertEquals(12L, episodes.first().resumeTicks)
            val first = server.takeRequest().requestUrl!!
            assertEquals("series", first.queryParameter("ParentId"))
            assertEquals("Episode", first.queryParameter("IncludeItemTypes"))
            assertEquals("false", first.queryParameter("IsVirtualItem"))
            assertEquals("false", first.queryParameter("EnableImages"))
            assertFalse(first.queryParameter("Fields")!!.contains("MediaSources"))
            assertEquals("1", server.takeRequest().requestUrl!!.queryParameter("StartIndex"))
        }
    }
}
