package dev.joyfin.session

import dev.joyfin.app.*
import dev.joyfin.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionControllerTest {
    private val saved = Session("http://cinema", "token", "user", "viewer")

    private class Store : SessionStorage {
        override var server = "http://cinema"
        override var serverName = "Cinema"
        var saved: Session? = null
        var clears = 0

        override fun load() = saved

        override fun save(session: Session) {
            saved = session
            server = session.server
        }

        override fun clear() {
            saved = null
            clears++
        }
    }

    private class Api : SessionConnection {
        override var session: Session? = null
        var connect: suspend (String) -> ServerInfo = { ServerInfo("New Cinema") }
        var authenticate: suspend (String, String, String) -> Session = { server, name, _ ->
            Session(server, "new-token", "user", name)
        }
        var validate: suspend () -> Unit = {}
        var signOut: suspend () -> Unit = {}
        var loginCalls = 0

        override suspend fun connect(server: String) = connect.invoke(server)

        override suspend fun login(server: String, name: String, password: String): Session {
            loginCalls++
            return authenticate(server, name, password)
        }

        override suspend fun validateSession() = validate()

        override suspend fun logout() = signOut()
    }

    private class Fixture(scope: CoroutineScope, page: Page = Page.CONNECT) {
        val api = Api()
        val store = Store()
        val state =
            MutableStateFlow(
                UiState(
                    page = page,
                    entry =
                        EntryState(
                            server = store.server,
                            serverName = store.serverName,
                            userName = "viewer",
                        ),
                )
            )
        var resets = 0
        var homeEntries = 0
        val requests =
            PageRequests(scope, state, { api.session != null }, { controller.expireSession() })
        val controller: SessionController =
            SessionController(
                api,
                store,
                state,
                requests,
                JellyfinApi::normalizeServer,
                { resets++ },
                {
                    homeEntries++
                    state.value = state.value.copy(page = Page.HOME)
                },
            )
    }

    @Test
    fun connectionNormalizesAddressAndPreservesUserName() = runTest {
        val f = Fixture(this)
        f.controller.connect("  cinema:8096/  ")
        assertTrue(f.state.value.entry.busy)
        advanceUntilIdle()
        assertEquals(Page.LOGIN, f.state.value.page)
        assertEquals("http://cinema:8096", f.store.server)
        assertEquals("New Cinema", f.state.value.entry.serverName)
        assertEquals("viewer", f.state.value.entry.userName)
        assertFalse(f.state.value.entry.busy)
    }

    @Test
    fun successfulLoginPersistsSessionBeforeEnteringHome() = runTest {
        val f = Fixture(this, Page.LOGIN)
        f.api.authenticate = { server, name, password ->
            assertEquals("secret", password)
            assertEquals("viewer", name)
            Session(server, "new-token", "user", name)
        }
        f.controller.login(" viewer ", "secret")
        advanceUntilIdle()
        assertEquals(f.store.saved, f.api.session)
        assertEquals("new-token", f.api.session?.token)
        assertEquals(1, f.resets)
        assertEquals(1, f.homeEntries)
        assertEquals(Page.HOME, f.state.value.page)
        assertFalse(f.state.value.entry.busy)
    }

    @Test
    fun failedLoginCannotReplayPasswordThroughRetry() = runTest {
        val f = Fixture(this, Page.LOGIN)
        f.api.authenticate = { _, _, _ -> throw ApiException(401, "wrong password") }
        f.controller.login("viewer", "secret")
        advanceUntilIdle()
        assertEquals("wrong password", f.state.value.entry.error)
        assertFalse(f.state.value.entry.sessionExpired)
        f.requests.retry?.invoke()
        advanceUntilIdle()
        assertEquals(1, f.api.loginCalls)
        assertNull(f.store.saved)
    }

    @Test
    fun cancelledLoginDiscardsResponseEvenWhenTransportIgnoresCancellation() = runTest {
        val f = Fixture(this, Page.LOGIN)
        f.api.authenticate = { _, _, _ ->
            withContext(NonCancellable) { delay(500) }
            saved
        }
        f.controller.login("viewer", "secret")
        runCurrent()
        f.controller.cancelEntry()
        advanceUntilIdle()
        assertEquals(Page.LOGIN, f.state.value.page)
        assertFalse(f.state.value.entry.busy)
        assertNull(f.store.saved)
        assertNull(f.api.session)
        assertEquals(0, f.homeEntries)
    }

    @Test
    fun restoreCancellationKeepsStoredSessionAndCanRetry() = runTest {
        val f = Fixture(this)
        f.store.saved = saved
        f.api.validate = { delay(500) }
        f.controller.start()
        runCurrent()
        assertEquals(Page.RESTORE, f.state.value.page)
        f.controller.cancelEntry()
        advanceUntilIdle()
        assertEquals(saved, f.store.saved)
        assertEquals("连接已取消，请重新连接", f.state.value.entry.error)
        assertEquals(0, f.homeEntries)
        f.requests.retry?.invoke()
        advanceUntilIdle()
        assertEquals(Page.HOME, f.state.value.page)
        assertNull(f.state.value.entry.error)
        assertFalse(f.state.value.entry.busy)
    }

    @Test
    fun networkFailureDuringRestoreRetainsCredentialsForRetry() = runTest {
        val f = Fixture(this)
        f.store.saved = saved
        f.api.validate = { error("offline") }
        f.controller.start()
        advanceUntilIdle()
        assertEquals(Page.RESTORE, f.state.value.page)
        assertEquals(saved, f.store.saved)
        assertEquals("offline", f.state.value.entry.error)
        f.api.validate = {}
        f.requests.retry?.invoke()
        advanceUntilIdle()
        assertEquals(1, f.homeEntries)
    }

    @Test
    fun unauthorizedRestoreClearsSessionAndReturnsToLogin() = runTest {
        val f = Fixture(this)
        f.store.saved = saved
        f.api.validate = { throw ApiException(401, "expired") }
        f.controller.start()
        advanceUntilIdle()
        assertEquals(Page.LOGIN, f.state.value.page)
        assertTrue(f.state.value.entry.sessionExpired)
        assertNull(f.state.value.entry.error)
        assertFalse(f.state.value.entry.busy)
        assertNull(f.store.saved)
        assertNull(f.api.session)
        assertEquals("viewer", f.state.value.entry.userName)
        assertEquals("http://cinema", f.state.value.entry.server)
    }

    @Test
    fun changeServerPreventsOldConnectionFromOverwritingNewPage() = runTest {
        val f = Fixture(this)
        f.api.connect = {
            withContext(NonCancellable) { delay(500) }
            ServerInfo("Stale")
        }
        f.controller.connect("http://old-server")
        runCurrent()
        f.controller.changeServer()
        advanceUntilIdle()
        assertEquals(Page.CONNECT, f.state.value.page)
        assertEquals("Cinema", f.state.value.entry.serverName)
        assertEquals("http://cinema", f.store.server)
        assertEquals(0, f.homeEntries)
    }

    @Test
    fun logoutClearsLocalSessionEvenWhenServerFails() = runTest {
        val f = Fixture(this, Page.SETTINGS)
        f.store.saved = saved
        f.api.session = saved
        f.api.signOut = { error("offline") }
        f.controller.logout()
        advanceUntilIdle()
        assertNull(f.store.saved)
        assertNull(f.api.session)
        assertEquals(Page.LOGIN, f.state.value.page)
        assertEquals("offline", f.state.value.entry.error)
        assertEquals(1, f.resets)
    }

    @Test
    fun cancelledLogoutCannotOverrideAChangedServer() = runTest {
        val f = Fixture(this, Page.SETTINGS)
        f.api.session = saved
        f.api.signOut = { withContext(NonCancellable) { delay(500) } }
        f.controller.logout()
        runCurrent()
        f.controller.changeServer()
        advanceUntilIdle()
        assertEquals(Page.CONNECT, f.state.value.page)
        assertEquals(1, f.resets)
        assertNull(f.state.value.entry.error)
    }
}
