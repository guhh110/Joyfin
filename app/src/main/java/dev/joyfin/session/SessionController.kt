package dev.joyfin.session

import dev.joyfin.app.*
import dev.joyfin.data.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow

internal class SessionController(
    private val api: SessionConnection,
    private val store: SessionStorage,
    private val mutable: MutableStateFlow<UiState>,
    private val requests: PageRequests,
    private val normalize: (String) -> String,
    private val resetBrowse: () -> Unit,
    private val enterHome: () -> Unit,
    private val quickConnect: QuickConnectConnection? = api as? QuickConnectConnection,
) {
    fun start() {
        api.session = store.load()
        api.session?.let { session ->
            mutable.value =
                mutable.value.copy(
                    page = Page.RESTORE,
                    entry =
                        mutable.value.entry.copy(
                            server = session.server,
                            userName = session.userName,
                        ),
                )
            restoreSession()
        }
    }

    fun connect(address: String): Unit =
        requests.run({ connect(address) }) {
            val normalized = normalize(address)
            val info = api.connect(normalized)
            currentCoroutineContext().ensureActive()
            store.server = normalized
            store.serverName = info.serverName
            mutable.value =
                UiState(
                    page = Page.LOGIN,
                    entry =
                        EntryState(
                            server = normalized,
                            serverName = store.serverName,
                            userName = mutable.value.entry.userName,
                        ),
                )
        }

    // Retry deliberately excludes credentials; a failed login requires another form submission.
    fun login(name: String, password: String): Unit =
        requests.run({}) {
            require(name.isNotBlank()) { "请输入用户名" }
            mutable.value =
                mutable.value.copy(
                    entry = mutable.value.entry.copy(userName = name.trim(), sessionExpired = false)
                )
            val session = api.login(mutable.value.entry.server, name.trim(), password)
            currentCoroutineContext().ensureActive()
            acceptSession(session)
        }

    fun quickLogin() {
        if (mutable.value.page != Page.LOGIN || mutable.value.entry.busy) return
        val server = mutable.value.entry.server
        mutable.value = mutable.value.copy(entry = mutable.value.entry.copy(
            quickConnect = true, quickConnectCode = null, sessionExpired = false))
        requests.run(::quickLogin) {
            try {
                val connection = quickConnect ?: throw QuickConnectException("此服务器暂不支持快速连接，请使用账号密码登录")
                // The secret stays in this cancellable request; only the display code enters UI state.
                val session = withTimeoutOrNull(5 * 60_000L) {
                    val challenge = connection.initiateQuickConnect(server)
                    currentCoroutineContext().ensureActive()
                    mutable.value = mutable.value.copy(entry = mutable.value.entry.copy(quickConnectCode = challenge.code))
                    while (!connection.pollQuickConnect(server, challenge.secret).authenticated) delay(2_000)
                    currentCoroutineContext().ensureActive()
                    connection.authenticateQuickConnect(server, challenge.secret)
                } ?: throw QuickConnectException("快速登录码已过期，请重新获取")
                currentCoroutineContext().ensureActive()
                acceptSession(session)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                // Network exceptions may contain the polling URL and its secret.
                val message = when {
                    e is ApiException && e.status == 404 -> "快速登录码已失效，或服务器不支持快速连接，请重新获取或使用账号密码登录"
                    e is ApiException && e.status in setOf(401, 403) -> "服务器未允许快速连接，请使用账号密码登录"
                    e is QuickConnectException -> e.message ?: "快速登录失败，请重新获取"
                    else -> "快速登录暂时失败，请检查网络后重新获取"
                }
                throw IllegalStateException(message)
            } finally {
                if (currentCoroutineContext().isActive)
                    mutable.value = mutable.value.copy(entry = mutable.value.entry.copy(quickConnectCode = null))
            }
        }
    }

    private fun acceptSession(session: Session) {
        store.save(session)
        api.session = session
        resetBrowse()
        mutable.value = UiState(page = Page.RESTORE, entry = EntryState(
            server = session.server, serverName = store.serverName, userName = session.userName))
        requests.retry = ::restoreSession
        enterHome()
    }

    fun restoreSession(): Unit =
        requests.run(::restoreSession) {
            api.validateSession()
            currentCoroutineContext().ensureActive()
            mutable.value =
                mutable.value.copy(entry = mutable.value.entry.copy(busy = false, error = null))
            enterHome()
        }

    fun cancelEntry() {
        if (!mutable.value.page.isEntry) return
        requests.cancel()
        mutable.value =
            mutable.value.copy(
                entry =
                    mutable.value.entry.copy(
                        busy = false,
                        quickConnect = false,
                        quickConnectCode = null,
                        error = if (mutable.value.page == Page.RESTORE) "连接已取消，请重新连接" else null,
                    )
            )
    }

    fun changeServer() {
        requests.cancel()
        reset(Page.CONNECT)
    }

    fun logout(): Unit =
        requests.run({ logout() }) {
            try {
                api.logout()
            } finally {
                // A cancelled logout must not overwrite a newer navigation/session transition.
                currentCoroutineContext().ensureActive()
                reset(Page.LOGIN)
            }
        }

    fun expireSession() {
        requests.cancel()
        reset(Page.LOGIN, expired = true)
    }

    fun backToConnection() {
        mutable.value =
            UiState(
                entry =
                    EntryState(
                        server = store.server,
                        serverName = store.serverName,
                        userName = mutable.value.entry.userName,
                    )
            )
    }

    private fun reset(page: Page, expired: Boolean = false) {
        resetBrowse()
        store.clear()
        api.session = null
        mutable.value =
            UiState(
                page = page,
                entry =
                    EntryState(
                        server = store.server,
                        serverName = store.serverName,
                        userName = mutable.value.entry.userName,
                        sessionExpired = expired,
                    ),
            )
    }
}
