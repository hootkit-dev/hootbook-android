package io.github.hootkit_dev.hootbook.data.brokerage

import io.github.hootkit_dev.hootbook.domain.brokerage.BrokerCredentials
import javax.crypto.KeyGenerator
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class BrokerageEncryptionTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val plaintext = "PRIVATE_TOKEN and private account holdings".toByteArray()
    @Test fun encryptsAndDecryptsWithoutStoringPlaintext() {
        val payload = BrokeragePayloadCipher.encrypt(plaintext, key)
        assertFalse(payload.toString(Charsets.UTF_8).contains("PRIVATE_TOKEN"))
        assertArrayEquals(plaintext, BrokeragePayloadCipher.decrypt(payload, key))
    }
    @Test fun usesNewNonceForEachWrite() {
        assertFalse(BrokeragePayloadCipher.encrypt(plaintext, key).contentEquals(BrokeragePayloadCipher.encrypt(plaintext, key)))
    }
    @Test fun rejectsTamperingAndWrongKey() {
        val payload = BrokeragePayloadCipher.encrypt(plaintext, key)
        val wrongKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        try { BrokeragePayloadCipher.decrypt(payload, wrongKey); fail() } catch (_: java.security.GeneralSecurityException) { }
        payload[payload.lastIndex] = (payload.last().toInt() xor 1).toByte()
        try { BrokeragePayloadCipher.decrypt(payload, key); fail() } catch (_: java.security.GeneralSecurityException) { }
    }
    @Test fun rejectsTruncatedOrUnknownVersion() {
        for (payload in listOf(byteArrayOf(1), ByteArray(40) { 2 })) {
            try { BrokeragePayloadCipher.decrypt(payload, key); fail() } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun serializesGenericCredentialsAndExactDecimalValues() {
        val saved = listOf(SavedBrokerConnection("id", "broker", "Name", BrokerCredentials(mapOf("token" to "SECRET")), IbkrFlexParser().parse(flexReport()), 42L))
        val payload = BrokeragePayloadCipher.encrypt(Json.encodeToString(saved).toByteArray(), key)
        val restored = Json.decodeFromString<List<SavedBrokerConnection>>(BrokeragePayloadCipher.decrypt(payload, key).toString(Charsets.UTF_8))
        assertEquals("SECRET", restored.single().credentials.fields["token"])
        assertEquals("1.125", restored.single().accounts.single().holdings.single().quantity)
    }
}
