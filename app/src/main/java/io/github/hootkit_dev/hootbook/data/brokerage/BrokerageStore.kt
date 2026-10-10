package io.github.hootkit_dev.hootbook.data.brokerage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import io.github.hootkit_dev.hootbook.domain.brokerage.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal class SavedBrokerConnection(
    val id: String,
    val providerId: String,
    val label: String,
    val credentials: BrokerCredentials,
    val accounts: List<BrokerageAccount>,
    val syncedAt: Long
)

internal interface BrokerageStore {
    fun read(): List<SavedBrokerConnection>
    fun write(connections: List<SavedBrokerConnection>)
}

/** Entire payload is encrypted, including account IDs and holdings; excluded from all backups. */
@Singleton
internal class EncryptedBrokerageStore @Inject constructor(@ApplicationContext context: Context) : BrokerageStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, "brokerage-v1.enc"))
    private val json = Json { ignoreUnknownKeys = true }
    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }

    @Synchronized override fun read(): List<SavedBrokerConnection> {
        try {
            if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return emptyList()
            val payload = file.openRead().use { it.readBytes() }
            require(payload.size >= 29 && payload[0] == 1.toByte())
            return json.decodeFromString(BrokeragePayloadCipher.decrypt(payload, key()).toString(Charsets.UTF_8))
        } catch (_: Exception) { throw BrokerageException(BrokerageError.STORAGE) }
    }

    @Synchronized override fun write(connections: List<SavedBrokerConnection>) {
        try {
            val payload = BrokeragePayloadCipher.encrypt(json.encodeToString(connections).toByteArray(), key())
            val stream = file.startWrite()
            try {
                stream.write(payload)
                file.finishWrite(stream)
            } catch (e: Exception) {
                file.failWrite(stream)
                throw e
            }
        } catch (_: Exception) {
            // Fail closed. Never fall back to plain SharedPreferences.
            throw BrokerageException(BrokerageError.STORAGE)
        }
    }

    companion object { private const val KEY_ALIAS = "cashiro_brokerage_v1" }
}

/** Versioned AES-GCM payload; authentication failures are never treated as empty data. */
internal object BrokeragePayloadCipher {
    fun encrypt(plaintext: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        require(cipher.iv.size == 12)
        return byteArrayOf(1) + cipher.iv + cipher.doFinal(plaintext)
    }
    fun decrypt(payload: ByteArray, key: SecretKey): ByteArray {
        require(payload.size >= 29 && payload[0] == 1.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, payload.copyOfRange(1, 13)))
        return cipher.doFinal(payload.copyOfRange(13, payload.size))
    }
}
