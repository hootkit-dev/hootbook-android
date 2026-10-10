package io.github.hootkit_dev.hootbook.data.sync

import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The test vector of docs/sync.md ("Encryption"), so another client (iOS) can check it derives,
 * seals and opens exactly as this one does. The expected values were computed independently
 * (Python: hashlib.pbkdf2_hmac and cryptography's AESGCM).
 */
class SyncCryptoTest {
    private val passphrase = "correct horse battery staple"
    private val salt = ByteArray(16) { it.toByte() } // 00 01 … 0f
    private val iterations = 600_000
    private val expectedKeyHex = "ef177144eec9420cbc1093d2a8b344a92bc506d0d4ec9c028dd19f8324d8c1e6"
    private val docId = "transactions_00112233445566778899aabbccddeeff"
    private val plaintext = """{"amount":"12.50","currency":"CNY","merchant_name":"咖啡"}"""
    private val nonce = ByteArray(12) { (0x10 + it).toByte() } // 10 11 … 1b
    private val expectedPayload =
        "EBESExQVFhcYGRobdzAcqH+KxgPpgLv5/t1ByjMUuIfzFaTOrVNiyPKbA6HH4aPLmYSET6R2zSL5lG0pJDoDfCvI94+EN6FeGLYHGlDEkuyY8f2H/asEHA=="
    private val keyCheckNonce = ByteArray(12) { (0x20 + it).toByte() } // 20 21 … 2b
    private val expectedKeyCheck = "ICEiIyQlJicoKSorKfauKuRIU+Dzp/RiFbSaHUnkWT6z/Gr5hCCWJAnSXRFxg9t7EsSfd4M="

    private val key by lazy { SyncCrypto.deriveKey(passphrase, salt, iterations) }

    @Test fun theKeyMatchesTheVector() {
        assertEquals("AAECAwQFBgcICQoLDA0ODw==", Base64.getEncoder().encodeToString(salt))
        assertEquals(expectedKeyHex, key.joinToString("") { "%02x".format(it) })
    }

    @Test fun sealingMatchesTheVectorAndOpensBack() {
        val sealed = SyncCrypto.seal(key, plaintext.toByteArray(Charsets.UTF_8), docId, nonce)
        assertEquals(expectedPayload, sealed)
        assertEquals(plaintext, SyncCrypto.open(key, expectedPayload, docId).toString(Charsets.UTF_8))
    }

    @Test fun theKeyCheckMatchesTheVector() {
        val check = SyncCrypto.seal(
            key, SyncCrypto.KEY_CHECK_PLAINTEXT.toByteArray(Charsets.UTF_8), SyncCrypto.KEY_CHECK_AAD, keyCheckNonce
        )
        assertEquals(expectedKeyCheck, check)
        val params = CryptoParams(salt, iterations, expectedKeyCheck)
        assertArrayEquals(key, SyncCrypto.unlock(passphrase, params))
    }

    @Test fun aWrongPassphraseIsRejectedByTheKeyCheck() {
        val params = CryptoParams(salt, iterations, expectedKeyCheck)
        try {
            SyncCrypto.unlock("correct horse battery stapler", params)
            fail("a wrong passphrase was accepted")
        } catch (expected: WrongPassphraseException) {
        }
    }

    @Test fun aPayloadOpensOnlyUnderItsOwnDocumentId() {
        try {
            SyncCrypto.open(key, expectedPayload, "transactions_ffeeddccbbaa99887766554433221100")
            fail("a payload moved to another document opened")
        } catch (expected: Exception) {
        }
    }

    @Test fun newParametersUnlockWithTheirPassphraseOnly() {
        val (params, key) = SyncCrypto.create("一二三四五六", iterations = 1_000)
        assertEquals(16, params.salt.size)
        assertTrue(SyncCrypto.checks(key, params))
        assertArrayEquals(key, SyncCrypto.unlock("一二三四五六", params))
        assertFalse(SyncCrypto.checks(SyncCrypto.deriveKey("123456", params.salt, 1_000), params))
        // A random nonce each time: the same plaintext never seals the same way twice
        val a = SyncCrypto.seal(key, byteArrayOf(1, 2, 3), docId)
        val b = SyncCrypto.seal(key, byteArrayOf(1, 2, 3), docId)
        assertTrue(a != b)
    }
}
