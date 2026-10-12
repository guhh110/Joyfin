package dev.joyfin.data

import org.json.JSONObject

interface SessionStorage {
    var server: String
    var serverName: String

    fun load(): Session?

    fun save(session: Session)

    fun clear()
}

interface SessionConnection {
    var session: Session?

    suspend fun connect(server: String): JSONObject

    suspend fun login(server: String, name: String, password: String): Session

    suspend fun validateSession()

    suspend fun logout()
}
