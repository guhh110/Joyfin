package dev.joyfin.audit

import dev.joyfin.data.*
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class LibraryAuditApiTest {
    @Test fun auditRequestsAreReadOnlyPagedAndBoundToTheSnapshotAccount() = runTest {
        MockWebServer().use { server ->
            val owner = auditAccount.copy(server = server.url("/jellyfin").toString().trimEnd('/'))
            val api = JellyfinApi("test-device").apply { session = owner }
            server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"work","Type":"Series"}],"TotalRecordCount":1}"""))
            val page = api.auditWorks(owner, "library", 100)
            assertNull(page.items!!.single().sources)
            val works = server.takeRequest()
            assertEquals("GET", works.method)
            assertEquals("/jellyfin/Items", works.requestUrl!!.encodedPath)
            assertEquals("100", works.requestUrl!!.queryParameter("StartIndex"))
            assertEquals("100", works.requestUrl!!.queryParameter("Limit"))
            assertEquals("Movie,Series", works.requestUrl!!.queryParameter("IncludeItemTypes"))
            assertEquals("library", works.requestUrl!!.queryParameter("ParentId"))
            assertEquals(owner.userId, works.requestUrl!!.queryParameter("UserId"))
            server.enqueue(MockResponse().setBody("""{"Items":[],"TotalRecordCount":0}"""))
            api.auditEpisodes(owner, "series", true, 0)
            val missing = server.takeRequest()
            assertEquals("GET", missing.method)
            assertEquals("true", missing.requestUrl!!.queryParameter("IsMissing"))
            server.enqueue(MockResponse().setBody("""{"Id":"work","Type":"Movie","MediaSources":[]}"""))
            assertEquals(emptyList<AuditSourceDto>(), api.auditItem(owner, "work").sources)
            assertEquals("GET", server.takeRequest().method)
            api.session = owner.copy(userId = "other")
            try { api.auditWorks(owner, null, 0); fail("Expected owner guard") }
            catch (_: IllegalStateException) { }
            assertEquals(3, server.requestCount)
        }
    }
}
