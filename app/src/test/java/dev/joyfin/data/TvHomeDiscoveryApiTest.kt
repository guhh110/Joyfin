package dev.joyfin.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class TvHomeDiscoveryApiTest {
    @Test fun requestsUnfinishedWorksWithCompleteHeroMetadata() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val api = JellyfinApi("device-id")
            api.session = Session(server.url("/jellyfin/").toString().trimEnd('/'), "token", "user-id", "Viewer")
            server.enqueue(MockResponse().setBody(
                """{"Items":[{"Id":"series","Name":"New series","Type":"Series","Overview":"Full overview","CommunityRating":8.5,"BackdropImageTags":["backdrop"],"UserData":{"Played":false,"UnplayedItemCount":5}}],"TotalRecordCount":1}"""))
            val items = api.homeDiscovery()
            val url = server.takeRequest().requestUrl!!
            assertEquals("/jellyfin/Items", url.encodedPath)
            assertEquals("user-id", url.queryParameter("UserId"))
            assertEquals("Movie,Series", url.queryParameter("IncludeItemTypes"))
            assertEquals("false", url.queryParameter("IsPlayed"))
            assertEquals("true", url.queryParameter("Recursive"))
            assertEquals("true", url.queryParameter("EnableUserData"))
            assertEquals("DateCreated,SortName", url.queryParameter("SortBy"))
            assertEquals("Descending", url.queryParameter("SortOrder"))
            assertEquals("20", url.queryParameter("Limit"))
            assertTrue(url.queryParameter("Fields")!!.contains("Overview"))
            assertEquals("Full overview", items.single().overview)
            assertEquals("backdrop", items.single().backdropTag)
            assertEquals(8.5, items.single().rating!!, 0.0)
            assertEquals(5, items.single().unplayedCount)
        } finally {
            server.shutdown()
        }
    }
}
