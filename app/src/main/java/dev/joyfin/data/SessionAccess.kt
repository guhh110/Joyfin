package dev.joyfin.data

interface SessionStorage {
    var server: String
    var serverName: String

    fun load(): Session?

    fun save(session: Session)

    fun clear()
}

interface SessionConnection {
    var session: Session?

    suspend fun connect(server: String): ServerInfo

    suspend fun login(server: String, name: String, password: String): Session

    suspend fun validateSession()

    suspend fun logout()
}
