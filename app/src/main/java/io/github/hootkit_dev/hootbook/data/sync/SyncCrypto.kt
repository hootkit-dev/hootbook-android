package io.github.hootkit_dev.hootbook.data.sync

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The encryption parameters of an account, stored once in `users/{uid}/meta/crypto`
 * (docs/sync.md, "Encryption"). [keyCheck] is [SyncCrypto.KEY_CHECK_PLAINTEXT] sealed with the key,
 * so another device can tell a wrong passphrase before it reads anything.
 */
data class CryptoParams(
    val salt: ByteArray,
    val iterations: Int,
    val keyCheck: String,
    val kdf: String = SyncCrypto.KDF,
    val cipher: String = SyncCrypto.CIPHER,
    val version: Int = SyncSchema.VERSION,
) {
    override fun equals(other: Any?) = other is CryptoParams && salt.contentEquals(other.salt) &&
        iterations == other.iterations && keyCheck == other.keyCheck && kdf == other.kdf && cipher == other.cipher
    override fun hashCode() = salt.contentHashCode() * 31 + keyCheck.hashCode()
}

class WrongPassphraseException : Exception("The sync passphrase does not match this account's")

/**
 * End-to-end encryption of sync payloads: AES-256-GCM with a 256-bit key from the passphrase by
 * PBKDF2-HMAC-SHA256. A sealed value is base64 (standard alphabet, padded) of
 * `nonce (12 bytes) || ciphertext || tag (16 bytes)`, with the document id as associated data.
 */
object SyncCrypto {
    const val KDF = "PBKDF2-HMAC-SHA256"
    const val CIPHER = "AES-256-GCM"
    /** OWASP's 2023 figure for PBKDF2-HMAC-SHA256. Derived once per device, when the passphrase is entered. */
    const val ITERATIONS = 600_000
    const val SALT_BYTES = 16
    const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256

    /** What the key check seals, with [KEY_CHECK_AAD] as associated data. */
    const val KEY_CHECK_PLAINTEXT = "cashiro-sync-key-check-v1"
    const val KEY_CHECK_AAD = "meta/crypto"

    private val random = SecureRandom()

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also(random::nextBytes)

    fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Seals [plaintext] under [key], bound to [aad] (the document id). [nonce] is random unless given (tests). */
    fun seal(key: ByteArray, plaintext: ByteArray, aad: String, nonce: ByteArray = ByteArray(NONCE_BYTES).also(random::nextBytes)): String {
        require(nonce.size == NONCE_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(nonce + cipher.doFinal(plaintext))
    }

    /** Opens a value [seal] made; throws [AEADBadTagException] for a wrong key, other associated data or tampering. */
    fun open(key: ByteArray, sealed: String, aad: String): ByteArray {
        val bytes = Base64.getDecoder().decode(sealed)
        if (bytes.size < NONCE_BYTES + TAG_BITS / 8) throw AEADBadTagException("too short")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, bytes, 0, NONCE_BYTES))
        cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(bytes, NONCE_BYTES, bytes.size - NONCE_BYTES)
    }

    /** New parameters for an account that has none, and the key they give [passphrase]. */
    fun create(passphrase: String, iterations: Int = ITERATIONS): Pair<CryptoParams, ByteArray> {
        val salt = newSalt()
        val key = deriveKey(passphrase, salt, iterations)
        val check = seal(key, KEY_CHECK_PLAINTEXT.toByteArray(Charsets.UTF_8), KEY_CHECK_AAD)
        return CryptoParams(salt, iterations, check) to key
    }

    /** The key [passphrase] gives under [params]; throws [WrongPassphraseException] when the key check fails. */
    fun unlock(passphrase: String, params: CryptoParams): ByteArray {
        val key = deriveKey(passphrase, params.salt, params.iterations)
        if (!checks(key, params)) throw WrongPassphraseException()
        return key
    }

    fun checks(key: ByteArray, params: CryptoParams): Boolean = try {
        open(key, params.keyCheck, KEY_CHECK_AAD).toString(Charsets.UTF_8) == KEY_CHECK_PLAINTEXT
    } catch (e: Exception) {
        false
    }
}
