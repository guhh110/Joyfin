package dev.joyfin.data.subtitles.opensubtitles

import android.content.Context
import dev.joyfin.data.subtitles.EncryptedSubtitleFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

@Serializable data class OpenSubtitlesAccount(
    val enabled: Boolean = true,
    val apiKey: String = "",
    val username: String = "",
    val token: String = "",
    val host: String = "api.opensubtitles.com",
)
/** Passwords are used once for explicit login and are never persisted. */
class OpenSubtitlesAccountStore(context: Context) {
    private val file = EncryptedSubtitleFile(context, "opensubtitles-account")
    fun load(): OpenSubtitlesAccount = file.read()?.let { Json.decodeFromString<OpenSubtitlesAccount>(it) } ?: OpenSubtitlesAccount()
    fun save(account: OpenSubtitlesAccount) = file.write(Json.encodeToString(account))
    fun remove() = file.remove()
}
