package dev.joyfin.session

/** Entry request state is independent of browse loading and never contains a password. */
data class EntryState(
    val server: String = "",
    val serverName: String = "",
    val userName: String = "",
    val sessionExpired: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
)
