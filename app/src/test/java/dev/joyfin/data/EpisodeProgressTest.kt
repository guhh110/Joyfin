package dev.joyfin.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.concurrent.TimeUnit

class EpisodeProgressTest {
    @Test fun progressQueryKeepsViewingHistoryWithoutRequestingFullEpisodeMetadata() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val api = JellyfinApi("test-device").apply {
                session = Session(server.url("/jellyfin").toString(), "test-token", "viewer", "Viewer")
            }
            server.enqueue(MockResponse().setBody("""{"Items":[
                {"Id":"e7","Name":"Earlier","Type":"Episode","SeriesId":"show","SeasonId":"s1",
                 "ParentIndexNumber":1,"IndexNumber":7,"RunTimeTicks":100,
                 "UserData":{"PlaybackPositionTicks":30,"LastPlayedDate":"2026-10-01T00:00:00Z"}},
                {"Id":"e24","Name":"Latest","Type":"Episode","SeriesId":"show","SeasonId":"s1",
                 "ParentIndexNumber":1,"IndexNumber":24,"RunTimeTicks":100,
                 "UserData":{"PlaybackPositionTicks":40,"LastPlayedDate":"2026-10-22T00:00:00Z"}}
            ]}"""))
            val episodes = api.episodeProgress("show")
            val target = requireNotNull(preferredEpisode(episodes))
            assertEquals("e24", target.id)
            assertEquals("s1", target.seasonId)
            assertEquals(1, target.season)
            assertEquals(24, target.episode)
            assertEquals(40L, target.resumeTicks)
            assertEquals(Instant.parse("2026-10-22T00:00:00Z").toEpochMilli(), target.lastPlayedAt)
            val url = requireNotNull(server.takeRequest(3, TimeUnit.SECONDS)).requestUrl!!
            assertEquals("/jellyfin/Shows/show/Episodes", url.encodedPath)
            assertEquals("viewer", url.queryParameter("UserId"))
            assertEquals("true", url.queryParameter("EnableUserData"))
            assertEquals("false", url.queryParameter("EnableImages"))
            assertEquals("ParentId", url.queryParameter("Fields"))
            assertNull(url.queryParameter("SeasonId"))
            assertNull(url.queryParameter("Limit"))

            server.enqueue(MockResponse().setBody("""{"Items":[]}"""))
            api.episodes("show", "s1")
            val preview = requireNotNull(server.takeRequest(3, TimeUnit.SECONDS)).requestUrl!!
            assertEquals("s1", preview.queryParameter("SeasonId"))
            assertTrue(preview.queryParameter("Fields")!!.contains("MediaSources"))
            assertNull(preview.queryParameter("EnableImages"))
        }
    }
}
