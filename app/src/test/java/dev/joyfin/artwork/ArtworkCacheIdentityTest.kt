package dev.joyfin.artwork

import dev.joyfin.data.Session
import coil.decode.DataSource
import org.junit.Assert.*
import org.junit.Test

class ArtworkCacheIdentityTest {
    private val session = Session("https://server.test/jellyfin/", "secret-token", "viewer", "Viewer")
    private val url = "https://server.test/jellyfin/Items/title/Images/Primary?MaxWidth=320&Tag=v1"

    @Test fun tokenRefreshAndTrailingSlashDoNotDiscardTheSameAccountsCache() {
        assertEquals(ArtworkCacheIdentity.namespace(session),
            ArtworkCacheIdentity.namespace(session.copy(server = session.server.trimEnd('/'), token = "new-token")))
    }

    @Test fun serversAccountsArtworkVersionsAndImageSizesRemainIndependent() {
        val namespace = ArtworkCacheIdentity.namespace(session)
        val original = ArtworkCacheIdentity.key(namespace, url)
        assertNotEquals(original, ArtworkCacheIdentity.key(ArtworkCacheIdentity.namespace(session.copy(userId = "other")), url))
        assertNotEquals(original, ArtworkCacheIdentity.key(ArtworkCacheIdentity.namespace(session.copy(server = "https://other.test")), url))
        assertNotEquals(original, ArtworkCacheIdentity.key(namespace, url.replace("v1", "v2")))
        assertNotEquals(original, ArtworkCacheIdentity.key(namespace, url.replace("320", "640")))
        assertFalse(original.contains("server.test"))
        assertFalse(original.contains("viewer"))
        assertFalse(original.contains("secret-token"))
    }

    @Test fun localResourcesAreNotCountedAsNetworkImageDiskCacheHits() {
        val before = ArtworkCacheDiagnostics.state.value
        ArtworkCacheDiagnostics.success(DataSource.DISK, remote = false)
        ArtworkCacheDiagnostics.success(DataSource.DISK, remote = true)
        ArtworkCacheDiagnostics.success(DataSource.MEMORY_CACHE, remote = true)
        val after = ArtworkCacheDiagnostics.state.value
        assertEquals(before.local + 1, after.local)
        assertEquals(before.disk + 1, after.disk)
        assertEquals(before.memory + 1, after.memory)
        assertEquals(before.network, after.network)
    }
}
