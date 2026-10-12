package dev.joyfin.browse

import dev.joyfin.app.*
import dev.joyfin.data.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class DetailLoadingTest {
    private class Fixture(scope: CoroutineScope, server: MockWebServer) {
        val state =
            MutableStateFlow(
                UiState(
                    page = Page.DETAIL,
                    detail = DetailUiState(item = Media("a", "A", "Movie"), ready = true, visit = 1),
                )
            )
        private val api =
            JellyfinApi("test").apply {
                session =
                    Session(server.url("/").toString().trimEnd('/'), "token", "user", "viewer")
            }
        private val requests = PageRequests(scope, state, { true }, { error("unexpected expiry") })
        val controller =
            DetailController(
                scope,
                api,
                state,
                requests,
                {},
                { _, _ -> },
                { error("unexpected expiry") },
            )

        suspend fun loaded() = withTimeout(5000) { state.first { !it.detail.favoriteBusy }.detail }
    }

    @Test
    fun favoriteLoadingIsLocalAndRejectsRepeatedSubmission() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setResponseCode(204).setHeadersDelay(100, TimeUnit.MILLISECONDS)
            )
            val f = Fixture(this, server)
            f.controller.toggleFavorite()
            assertTrue(f.state.value.detail.favoriteBusy)
            assertFalse(f.state.value.detail.busy)
            assertTrue(f.state.value.detail.ready)
            f.controller.toggleFavorite()
            assertTrue(f.loaded().item!!.favorite)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun favoriteFailureEndsSpinnerAndKeepsExistingContent() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(500))
            val f = Fixture(this, server)
            val item = f.state.value.detail.item
            f.controller.toggleFavorite()
            val after = f.loaded()
            assertEquals(item, after.item)
            assertNotNull(after.error)
            assertTrue(after.ready)
        }
    }

    @Test
    fun canceledFavoriteCannotClearANewerVisitsLoadingOrChangeItsItem() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setResponseCode(204).setHeadersDelay(200, TimeUnit.MILLISECONDS)
            )
            server.enqueue(
                MockResponse().setResponseCode(204).setHeadersDelay(100, TimeUnit.MILLISECONDS)
            )
            val f = Fixture(this, server)
            f.controller.toggleFavorite()
            yield()
            withContext(Dispatchers.IO) { server.takeRequest() }
            f.controller.cancel()
            assertFalse(f.state.value.detail.favoriteBusy)
            f.state.value =
                f.state.value.copy(
                    detail = DetailUiState(item = Media("b", "B", "Movie"), visit = 2, ready = true)
                )
            f.controller.toggleFavorite()
            assertTrue(f.state.value.detail.favoriteBusy)
            val latest = f.loaded()
            assertEquals("b", latest.item!!.id)
            assertTrue(latest.item.favorite)
        }
    }
}
