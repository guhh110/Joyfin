package dev.joyfin.data.subtitles

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encryption primitive only; site-specific stores own their schema and lifecycle. */
internal class EncryptedSubtitleFile(context: Context, name: String) {
    private val file = File(context.noBackupFilesDir, name)
    private val alias = "joyfin-$name"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun read(): String? {
        if (!file.exists()) return null
        val bytes = file.readBytes()
        require(bytes.size > 28) { "来源配置损坏，请移除后重新保存" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }
    @Synchronized fun write(text: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeBytes(cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8)))
        check(temp.renameTo(file)) { "无法保存来源配置" }
    }
    @Synchronized fun remove() {
        check(!file.exists() || file.delete()) { "无法移除来源配置" }
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); if (containsAlias(alias)) deleteEntry(alias) }
    }
}
