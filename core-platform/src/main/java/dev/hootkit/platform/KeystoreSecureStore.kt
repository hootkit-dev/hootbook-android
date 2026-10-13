package dev.hootkit.platform

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import dev.hootkit.core.PlatformException
import dev.hootkit.core.SecureStore
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device-bound AES-GCM values; synchronous, serialized and atomically persisted. No plaintext fallback. */
class KeystoreSecureStore(context: Context) : SecureStore {
    private val directory = File(context.applicationContext.noBackupFilesDir, "hootkit-core/secure")
    private val keyStore by lazy { KeyStore.getInstance("AndroidKeyStore").apply { load(null) } }

    @Synchronized
    override fun get(key: String): ByteArray? {
        val file = fileFor(key)
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
        return try {
            val secret = keyStore.getKey(ALIAS, null) as? SecretKey ?: return null
            val bytes = file.openRead().use { it.readBytes() }
            if (bytes.size < 1 + IV_SIZE + 16 || bytes[0] != FORMAT_VERSION) return null
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(128, bytes.copyOfRange(1, 1 + IV_SIZE)))
                updateAAD(key.toByteArray(Charsets.UTF_8))
                doFinal(bytes, 1 + IV_SIZE, bytes.size - 1 - IV_SIZE)
            }
        } catch (_: AEADBadTagException) {
            null // A copied value or an invalid ciphertext is not a usable credential.
        } catch (_: UnrecoverableKeyException) {
            null
        } catch (_: KeyPermanentlyInvalidatedException) {
            null
        } catch (_: Exception) {
            throw PlatformException.Unavailable()
        }
    }

    @Synchronized
    override fun set(key: String, value: ByteArray) {
        try {
            check(directory.isDirectory || directory.mkdirs())
            val secret = (keyStore.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator
                .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                    init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build())
                    generateKey()
                }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, secret)
                updateAAD(key.toByteArray(Charsets.UTF_8))
            }
            val encrypted = byteArrayOf(FORMAT_VERSION) + cipher.iv + cipher.doFinal(value)
            val file = fileFor(key)
            val stream = file.startWrite()
            try {
                stream.write(encrypted)
                stream.fd.sync()
                file.finishWrite(stream)
            } catch (failure: Exception) {
                file.failWrite(stream)
                throw failure
            }
        } catch (_: Exception) {
            throw PlatformException.Unavailable()
        }
    }

    @Synchronized
    override fun remove(key: String) {
        try {
            val file = fileFor(key)
            file.delete()
            check(!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists())
        } catch (_: Exception) {
            throw PlatformException.Unavailable()
        }
    }

    private fun fileFor(key: String): AtomicFile {
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return AtomicFile(File(directory, hash))
    }

    private companion object {
        const val ALIAS = "hootkit.core.secure.v1"
        const val IV_SIZE = 12
        const val FORMAT_VERSION: Byte = 1
    }
}
