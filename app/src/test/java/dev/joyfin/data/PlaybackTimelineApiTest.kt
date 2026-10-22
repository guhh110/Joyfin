package dev.joyfin.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class PlaybackTimelineApiTest {
    @Test fun oldServerSegmentsAreOptionalButAuthenticationErrorsStillPropagate() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val api = JellyfinApi("test").apply { session = Session(server.url("/").toString(), "token", "viewer", "Viewer") }
            server.enqueue(MockResponse().setResponseCode(404))
            assertTrue(api.mediaSegments("e").isEmpty())
            assertEquals("/MediaSegments/e", server.takeRequest().path)
            server.enqueue(MockResponse().setResponseCode(401))
            try { api.mediaSegments("e"); fail("Expected expired session") }
            catch (e: ApiException) { assertEquals(401, e.status) }
            server.takeRequest()
            server.enqueue(MockResponse().setBody("""{"Items":[
                {"ItemId":"e","Type":"Intro","StartTicks":0,"EndTicks":900000000},
                {"ItemId":"other","Type":"Outro","StartTicks":100,"EndTicks":200}]}"""))
            assertEquals(listOf(MediaSegment("e", "Intro", 0, 900000000)), api.mediaSegments("e"))
        }
    }

    @Test fun chapterMetadataAndSkipPreferencesKeepOldMediaParsingCompatible() {
        val dto = AppJson.decodeFromString<ItemDto>("""{"Id":"movie","Type":"Movie","Chapters":[
            {"Name":"Opening","StartPositionTicks":0},{"StartPositionTicks":900000000}]}""")
        assertEquals(listOf(MediaChapter(0, "Opening"), MediaChapter(900000000)), Media.from(dto).chapters)
        assertTrue(Media.from(AppJson.decodeFromString<ItemDto>("""{"Id":"old"}""")).chapters.isEmpty())
        assertEquals(SkipPreferences(), AppJson.decodeFromString<SkipPreferences>("{}"))
        assertEquals(SkipPreferences(0, 600), SkipPreferences(-10, 900).validated())
    }
}
