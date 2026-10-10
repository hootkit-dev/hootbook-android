package io.github.hootkit_dev.hootbook.data.cloud.security

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Engine for End-to-End (E2E) encryption of backup archives using AES-256-GCM and PBKDF2 key derivation.
 */
@Singleton
class BackupEncryptionEngine @Inject constructor() {

    companion object {
        private const val MAGIC_HEADER = "CSH_ENC1" // 8 bytes
        private const val PBKDF2_ITERATIONS = 100_000
        private const val KEY_LENGTH_BITS = 256
        private const val SALT_LENGTH_BYTES = 16
        private const val IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val ALGORITHM = "AES/GCM/NoPadding"
    }

    /**
     * Encrypt a local backup file (.zip) to an encrypted file (.enc) using the provided passphrase.
     */
    fun encryptFile(inputFile: File, outputFile: File, passphrase: String): Result<File> {
        return try {
            val secureRandom = SecureRandom()
            val salt = ByteArray(SALT_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
            val iv = ByteArray(IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }

            val secretKey = deriveKey(passphrase, salt)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

            FileOutputStream(outputFile).use { fos ->
                // Write magic header (8 bytes)
                fos.write(MAGIC_HEADER.toByteArray(Charsets.US_ASCII))
                // Write salt (16 bytes)
                fos.write(salt)
                // Write IV (12 bytes)
                fos.write(iv)

                FileInputStream(inputFile).use { fis ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        val encryptedBytes = cipher.update(buffer, 0, bytesRead)
                        if (encryptedBytes != null && encryptedBytes.isNotEmpty()) {
                            fos.write(encryptedBytes)
                        }
                    }
                    val finalBytes = cipher.doFinal()
                    if (finalBytes != null && finalBytes.isNotEmpty()) {
                        fos.write(finalBytes)
                    }
                }
            }

            Result.success(outputFile)
        } catch (e: Exception) {
            Log.e("BackupEncryptionEngine", "Encryption failed", e)
            Result.failure(e)
        }
    }

    /**
     * Decrypt an encrypted backup file (.enc) to a plaintext destination file (.zip) using the provided passphrase.
     */
    fun decryptFile(encryptedFile: File, outputFile: File, passphrase: String): Result<File> {
        return try {
            FileInputStream(encryptedFile).use { fis ->
                // Read magic header
                val headerBytes = ByteArray(MAGIC_HEADER.length)
                val headerRead = fis.read(headerBytes)
                val headerString = String(headerBytes, Charsets.US_ASCII)
                if (headerRead != MAGIC_HEADER.length || headerString != MAGIC_HEADER) {
                    return Result.failure(IllegalArgumentException("Invalid encrypted backup file header. The file may not be an encrypted Hootbook backup."))
                }

                val salt = ByteArray(SALT_LENGTH_BYTES)
                if (fis.read(salt) != SALT_LENGTH_BYTES) {
                    return Result.failure(IllegalArgumentException("Corrupted encrypted backup header (missing salt)."))
                }

                val iv = ByteArray(IV_LENGTH_BYTES)
                if (fis.read(iv) != IV_LENGTH_BYTES) {
                    return Result.failure(IllegalArgumentException("Corrupted encrypted backup header (missing IV)."))
                }

                val secretKey = deriveKey(passphrase, salt)
                val cipher = Cipher.getInstance(ALGORITHM)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

                FileOutputStream(outputFile).use { fos ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        val decryptedBytes = cipher.update(buffer, 0, bytesRead)
                        if (decryptedBytes != null && decryptedBytes.isNotEmpty()) {
                            fos.write(decryptedBytes)
                        }
                    }
                    val finalBytes = cipher.doFinal()
                    if (finalBytes != null && finalBytes.isNotEmpty()) {
                        fos.write(finalBytes)
                    }
                }
            }

            Result.success(outputFile)
        } catch (e: Exception) {
            Log.e("BackupEncryptionEngine", "Decryption failed", e)
            Result.failure(IllegalArgumentException("Failed to decrypt backup. Incorrect passphrase or corrupted file.", e))
        }
    }

    /**
     * Check if a file appears to be an encrypted Hootbook backup based on magic header.
     */
    fun isEncryptedBackup(file: File): Boolean {
        if (!file.exists() || file.length() < MAGIC_HEADER.length + SALT_LENGTH_BYTES + IV_LENGTH_BYTES) {
            return false
        }
        return try {
            FileInputStream(file).use { fis ->
                val headerBytes = ByteArray(MAGIC_HEADER.length)
                fis.read(headerBytes) == MAGIC_HEADER.length && String(headerBytes, Charsets.US_ASCII) == MAGIC_HEADER
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
