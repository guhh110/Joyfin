package dev.joyfin.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** Passwords are never persisted; the complete authenticated session is encrypted at rest. */
class SessionStore(context: Context) : SessionStorage {
    private val prefs = context.getSharedPreferences("joyfin", Context.MODE_PRIVATE)
    val deviceId: String =
        prefs.getString("deviceId", null)
            ?: UUID.randomUUID().toString().also { prefs.edit().putString("deviceId", it).apply() }
    override var server: String
        get() = prefs.getString("server", "") ?: ""
        set(value) {
            prefs.edit().putString("server", value).apply()
        }

    override var serverName: String
        get() = prefs.getString("serverName", "") ?: ""
        set(value) {
            prefs.edit().putString("serverName", value).apply()
        }

    var autoNext: Boolean
        get() = prefs.getBoolean("autoNext", true)
        set(value) {
            prefs.edit().putBoolean("autoNext", value).apply()
        }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("joyfin-session", null) as? SecretKey)?.let {
            return it
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                            "joyfin-session",
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
            }
            .generateKey()
    }

    override fun save(session: Session) {
        val cipher =
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val data = AppJson.encodeToString(session)
        val payload = cipher.iv + cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        prefs
            .edit()
            .putString("session", Base64.encodeToString(payload, Base64.NO_WRAP))
            .putString("server", session.server)
            .apply()
    }

    override fun load(): Session? =
        try {
            prefs.getString("session", null)?.let {
                val bytes = Base64.decode(it, Base64.NO_WRAP)
                val cipher =
                    Cipher.getInstance("AES/GCM/NoPadding").apply {
                        init(
                            Cipher.DECRYPT_MODE,
                            key(),
                            GCMParameterSpec(128, bytes.copyOfRange(0, 12)),
                        )
                    }
                AppJson.decodeFromString<Session>(
                    String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
                )
            }
        } catch (_: Exception) {
            clear()
            null
        }

    override fun clear() {
        prefs
            .edit()
            .remove("session")
            .remove("pendingReport")
            .remove("pendingPlaybackStops")
            .apply()
    }

    var pendingPlaybackStops: String?
        get() = prefs.getString("pendingPlaybackStops", null)
        set(value) {
            prefs.edit().putString("pendingPlaybackStops", value).apply()
        }

    var pendingReport: String?
        get() = prefs.getString("pendingReport", null)
        set(value) {
            prefs.edit().putString("pendingReport", value).apply()
        }
}
