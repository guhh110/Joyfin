package dev.joyfin.data.subtitles.assrt

import android.content.Context
import dev.joyfin.data.subtitles.EncryptedSubtitleFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

@Serializable data class AssrtAccount(val enabled: Boolean = true, val token: String = "")
/** ASSRT has an API token, no username/password login or session cache. */
class AssrtAccountStore(context: Context) {
    private val file = EncryptedSubtitleFile(context, "assrt-account")
    fun load(): AssrtAccount = file.read()?.let { Json.decodeFromString<AssrtAccount>(it) } ?: AssrtAccount()
    fun save(account: AssrtAccount) = file.write(Json.encodeToString(account))
    fun remove() = file.remove()
}
