package dev.joyfin.browse

import dev.joyfin.app.Page
import dev.joyfin.app.PageRequests
import dev.joyfin.app.UiState
import dev.joyfin.data.JellyfinApi
import dev.joyfin.data.Media
import dev.joyfin.data.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SeriesRecommendationLoadingTest {
    @Test fun recommendationBecomesAvailableBeforeSlowSeriesDetails() = runBlocking {
        MockWebServer().use { server ->
            val releaseDetails = CountDownLatch(1)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl!!.encodedPath) {
                    "/Items/show" -> {
                        releaseDetails.await(5, TimeUnit.SECONDS)
                        MockResponse().setBody("""{"Id":"show","Name":"Show","Type":"Series"}""")
                    }
                    "/Shows/show/Episodes" -> MockResponse().setBody("""{"Items":[
                        {"Id":"latest","Name":"Latest","Type":"Episode","SeriesId":"show",
                        "ParentIndexNumber":1,"IndexNumber":24,"UserData":{"PlaybackPositionTicks":100}}
                    ]}""")
                    else -> MockResponse().setBody("""{"Items":[]}""")
                }
            }
            server.start()
            val api = JellyfinApi("test-device").apply {
                session = Session(server.url("/").toString(), "test-token", "viewer", "Viewer")
            }
            val state = MutableStateFlow(UiState(page = Page.HOME))
            val requests = PageRequests(this, state, { true }, { fail("Unexpected auth error") })
            val controller = DetailController(this, api, state, requests,
                { state.value = state.value.copy(page = it) }, { _, _ -> fail("Unexpected navigation") },
                { fail("Unexpected auth error") })
            try {
                controller.open(Media("show", "Show", "Series"))
                val ready = withTimeout(3000) { state.first { it.detail.series?.recommendation?.data != null } }
                assertEquals("latest", ready.detail.series!!.recommendation.data!!.id)
                assertFalse(ready.detail.ready)
                assertTrue(ready.detail.busy)
                releaseDetails.countDown()
                val full = withTimeout(3000) { state.first { it.detail.ready && !it.detail.busy } }
                assertEquals("latest", full.detail.series!!.recommendation.data!!.id)
                assertNull(full.detail.error)
            } finally {
                releaseDetails.countDown()
                requests.cancel()
                controller.cancel()
            }
        }
    }
}
