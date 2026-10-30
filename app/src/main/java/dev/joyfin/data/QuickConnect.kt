package dev.joyfin.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface QuickConnectConnection {
    suspend fun initiateQuickConnect(server: String): QuickConnectResult
    suspend fun pollQuickConnect(server: String, secret: String): QuickConnectResult
    suspend fun authenticateQuickConnect(server: String, secret: String): Session
}

@Serializable
data class QuickConnectResult(
    @SerialName("Authenticated") val authenticated: Boolean = false,
    @SerialName("Secret") val secret: String = "",
    @SerialName("Code") val code: String = "",
)

@Serializable
internal data class QuickConnectRequest(@SerialName("Secret") val secret: String)

internal class QuickConnectException(message: String) : Exception(message)
