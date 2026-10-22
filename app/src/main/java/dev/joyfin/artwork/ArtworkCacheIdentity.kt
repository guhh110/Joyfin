package dev.joyfin.artwork

import dev.joyfin.data.Session
import java.security.MessageDigest

/** Versioned keys preserve image tags and requested sizes, and separate accounts without retaining credentials. */
internal object ArtworkCacheIdentity {
    fun namespace(session: Session?): String = session?.let {
        digest("${it.server.trimEnd('/')}\u0000${it.userId}")
    }.orEmpty()

    fun key(namespace: String, url: String): String = "artwork-v1:" + digest("$namespace\u0000$url")

    private fun digest(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        val hex = "0123456789abcdef"
        return buildString(bytes.size * 2) {
            bytes.forEach {
                val unsigned = it.toInt() and 0xff
                append(hex[unsigned ushr 4])
                append(hex[unsigned and 0xf])
            }
        }
    }
}
