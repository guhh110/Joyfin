package dev.joyfin.session

import dev.joyfin.app.*
import dev.joyfin.data.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow

internal class SessionController(
    private val api: SessionConnection,
    private val store: SessionStorage,
    private val mutable: MutableStateFlow<UiState>,
    private val requests: PageRequests,
    private val normalize: (String) -> String,
    private val resetBrowse: () -> Unit,
    private val enterHome: () -> Unit,
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
            store.save(session)
            api.session = session
            resetBrowse()
            mutable.value =
                UiState(
                    page = Page.RESTORE,
                    entry =
                        EntryState(
                            server = session.server,
                            serverName = store.serverName,
                            userName = session.userName,
                        ),
                )
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
