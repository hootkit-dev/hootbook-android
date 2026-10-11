package io.github.hootkit_dev.hootbook.core.migration

import android.app.Application
import android.util.Base64
import android.util.Xml
import com.google.crypto.tink.Aead
import com.google.crypto.tink.BinaryKeysetWriter
import com.google.crypto.tink.DeterministicAead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AesGcmKeyManager
import com.google.crypto.tink.daead.AesSivKeyManager
import com.google.crypto.tink.daead.DeterministicAeadConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.io.StringWriter
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** All files and keys are synthetic fixtures. Never loads the Android production Keystore. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LegacyServiceSettingsTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var prefs: File
    private lateinit var noBackup: File
    private val master: SecretKey = SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "AES")
    private val broker: SecretKey = SecretKeySpec(ByteArray(32) { (it + 42).toByte() }, "AES")
    private val lookups = mutableListOf<String>()

    @Before fun setUp() {
        prefs = folder.newFolder("shared_prefs")
        noBackup = folder.newFolder("no_backup")
        AeadConfig.register()
        DeterministicAeadConfig.register()
    }

    private fun reader(keys: LegacyServiceSettings.ExistingKeys = LegacyServiceSettings.ExistingKeys { alias ->
        lookups += alias
        when (alias) {
            LegacyServiceSettings.AI_KEY_ALIAS -> master
            LegacyServiceSettings.BROKERAGE_KEY_ALIAS -> broker
            else -> null
        }
    }) = LegacyServiceSettings(prefs, noBackup, keys)

    @Test fun absentStoresDoNotLookupKeysOrCreateFiles() = runBlocking {
        reader().readOnly().use { result ->
            assertTrue(result.entries.isEmpty())
            assertEquals(LegacyServiceSettings.Preview(), result.preview)
        }
        assertTrue(lookups.isEmpty())
        assertTrue(prefs.listFiles().orEmpty().isEmpty())
        assertTrue(noBackup.listFiles().orEmpty().isEmpty())
    }

    @Test fun aiReadsOriginalEncryptedPreferencesFormatAndRetainsExactFields() = runBlocking {
        val values = linkedMapOf(
            "protocol" to "OPENAI_COMPATIBLE",
            "base_url" to "https://example.invalid/v1/",
            "model" to "synthetic-model",
            "api_key" to " synthetic-only-key<&> ",
        )
        val source = aiFile().also { writeAi(it, values) }
        val before = source.readBytes()
        val result = reader().readOnly()
        val bytes = result.entries.getValue("ai.json")
        val restored = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        assertEquals(values, restored.mapValues { it.value.jsonPrimitive.content })
        assertTrue(result.preview.aiPresent)
        assertTrue(result.preview.aiExported)
        assertFalse(result.preview.aiAccessFailed)
        assertFalse(result.preview.aiRequiresSetup)
        assertEquals(listOf(LegacyServiceSettings.AI_KEY_ALIAS), lookups)
        assertArrayEquals(before, source.readBytes())
        assertFalse(result.toString().contains("synthetic-only-key"))
        result.close()
        assertTrue(bytes.all { it == 0.toByte() })
        assertTrue(result.entries.isEmpty())
    }

    @Test fun brokeragePreservesExactSerializedCredentialsUnknownProviderAndDecimalStrings() = runBlocking {
        val text = brokerageJson()
        val source = brokerageFile().also { it.writeBytes(byteArrayOf(1) + encrypt(text.toByteArray(), broker)) }
        val before = source.readBytes()
        reader().readOnly().use { result ->
            assertEquals(text, result.entries.getValue("brokerage.json").toString(Charsets.UTF_8))
            assertTrue(result.preview.brokeragePresent)
            assertEquals(2, result.preview.brokerageConnections)
            assertEquals(1, result.preview.brokerageAccounts)
            assertEquals(1, result.preview.brokerageHoldings)
            assertFalse(result.preview.brokerageAccessFailed)
            assertFalse(result.toString().contains("fixture-token"))
            assertFalse(result.preview.toString().contains("U-SYNTHETIC"))
        }
        assertArrayEquals(before, source.readBytes())
        assertEquals(listOf(LegacyServiceSettings.BROKERAGE_KEY_ALIAS), lookups)
    }

    @Test fun interruptedAtomicWritesUseCommittedBackupWithoutRenamingDeletingOrResetting() = runBlocking {
        writeAi(aiFile(), mapOf("protocol" to "ANTHROPIC", "api_key" to "uncommitted-fixture"))
        writeAi(File(aiFile().path + ".bak"), mapOf("protocol" to "ANTHROPIC", "api_key" to "committed-fixture"))
        brokerageFile().writeText("unfinished encrypted write")
        File(brokerageFile().path + ".bak").writeBytes(byteArrayOf(1) + encrypt(brokerageJson().toByteArray(), broker))
        val files = prefs.listFiles().orEmpty().toList() + noBackup.listFiles().orEmpty().toList()
        val originals = files.associateWith { it.readBytes() }
        reader().readOnly().use { result ->
            val ai = Json.parseToJsonElement(result.entries.getValue("ai.json").toString(Charsets.UTF_8)).jsonObject
            assertEquals("committed-fixture", ai.getValue("api_key").jsonPrimitive.content)
            assertEquals(2, result.preview.brokerageConnections)
        }
        originals.forEach { (file, bytes) -> assertArrayEquals(bytes, file.readBytes()) }
        assertEquals(2, prefs.listFiles().orEmpty().size)
        assertEquals(2, noBackup.listFiles().orEmpty().size)
    }

    @Test fun missingExistingKeysAreAccessFailuresAndNeverBecomeEmptySuccessfulExports() = runBlocking {
        writeAi(aiFile(), mapOf("protocol" to "ANTHROPIC", "api_key" to "fixture-ai-key"))
        brokerageFile().writeBytes(byteArrayOf(1) + encrypt(brokerageJson().toByteArray(), broker))
        val aiBefore = aiFile().readBytes()
        val brokerBefore = brokerageFile().readBytes()
        reader(LegacyServiceSettings.ExistingKeys { null }).readOnly().use { result ->
            assertTrue(result.preview.aiPresent)
            assertTrue(result.preview.aiAccessFailed)
            assertTrue(result.preview.aiRequiresSetup)
            assertFalse(result.preview.aiExported)
            assertTrue(result.preview.brokeragePresent)
            assertTrue(result.preview.brokerageAccessFailed)
            assertEquals(0, result.preview.brokerageConnections)
            assertTrue(result.entries.isEmpty())
        }
        assertArrayEquals(aiBefore, aiFile().readBytes())
        assertArrayEquals(brokerBefore, brokerageFile().readBytes())
    }

    @Test fun authenticationFailureDoesNotFallBackToDifferentPlaintextAiSettings() = runBlocking {
        writeAi(aiFile(), mapOf("protocol" to "ANTHROPIC", "api_key" to "fixture-ai-key"))
        writePreferences(File(prefs, "cashiro_ai_prefs.xml"), mapOf("protocol" to "OPENAI_COMPATIBLE", "model" to "different"))
        brokerageFile().writeBytes((byteArrayOf(1) + encrypt(brokerageJson().toByteArray(), broker)).also {
            it[it.lastIndex] = (it.last().toInt() xor 1).toByte()
        })
        val before = (prefs.listFiles().orEmpty().toList() + noBackup.listFiles().orEmpty().toList()).associateWith { it.readBytes() }
        reader(LegacyServiceSettings.ExistingKeys { SecretKeySpec(ByteArray(32), "AES") }).readOnly().use { result ->
            assertTrue(result.preview.aiAccessFailed)
            assertTrue(result.preview.brokerageAccessFailed)
            assertTrue(result.entries.isEmpty())
        }
        before.forEach { (file, bytes) -> assertArrayEquals(bytes, file.readBytes()) }
    }

    @Test fun legacyPlaintextFallbackCopiesOnlyNonSecretFieldsAndRequestsSetup() = runBlocking {
        writePreferences(File(prefs, "cashiro_ai_prefs.xml"), linkedMapOf(
            "protocol" to "OPENAI_COMPATIBLE", "base_url" to "https://example.invalid/v1",
            "model" to "fixture-model", "api_key" to "must-never-copy-plaintext-key",
        ))
        reader().readOnly().use { result ->
            val ai = Json.parseToJsonElement(result.entries.getValue("ai.json").toString(Charsets.UTF_8)).jsonObject
            assertEquals(setOf("protocol", "base_url", "model"), ai.keys)
            assertTrue(result.preview.aiRequiresSetup)
            assertFalse(result.preview.aiAccessFailed)
        }
        assertTrue(lookups.isEmpty())
    }

    @Test fun transportDoesNotInventDefaultsOrFilterUnknownProviderProtocols() = runBlocking {
        writeAi(aiFile(), mapOf("protocol" to "FUTURE_PROVIDER", "api_key" to "fixture-key"))
        reader().readOnly().use { result ->
            val ai = Json.parseToJsonElement(result.entries.getValue("ai.json").toString(Charsets.UTF_8)).jsonObject
            assertEquals(setOf("protocol", "api_key"), ai.keys)
            assertEquals("FUTURE_PROVIDER", ai.getValue("protocol").jsonPrimitive.content)
        }
    }

    @Test fun oneBrokenStoreDoesNotDiscardOtherReadableServiceSettings() = runBlocking {
        aiFile().writeText("not xml: fixture-secret-must-not-appear-in-report")
        brokerageFile().writeBytes(byteArrayOf(1) + encrypt(brokerageJson().toByteArray(), broker))
        reader().readOnly().use { result ->
            assertTrue(result.preview.aiAccessFailed)
            assertFalse(result.preview.brokerageAccessFailed)
            assertEquals(setOf("brokerage.json"), result.entries.keys)
            assertFalse(result.toString().contains("fixture-secret"))
        }
    }

    @Test fun emptyDecodedStoresArePresentWithoutInventingEntries() = runBlocking {
        writeAi(aiFile(), emptyMap())
        brokerageFile().writeBytes(byteArrayOf(1) + encrypt("[]".toByteArray(), broker))
        reader().readOnly().use { result ->
            assertTrue(result.preview.aiPresent)
            assertTrue(result.preview.brokeragePresent)
            assertFalse(result.preview.aiAccessFailed)
            assertFalse(result.preview.brokerageAccessFailed)
            assertFalse(result.preview.aiExported)
            assertTrue(result.entries.isEmpty())
        }
    }

    @Test fun invalidJsonAndOversizedFilesFailClosedWithoutLookingUpOversizedStoreKey() = runBlocking {
        RandomAccessFile(aiFile(), "rw").use { it.setLength(1024L * 1024L + 1) }
        brokerageFile().writeBytes(byteArrayOf(1) + encrypt("{invalid fixture}".toByteArray(), broker))
        reader().readOnly().use { result ->
            assertTrue(result.preview.aiAccessFailed)
            assertTrue(result.preview.brokerageAccessFailed)
            assertTrue(result.entries.isEmpty())
        }
        assertEquals(listOf(LegacyServiceSettings.BROKERAGE_KEY_ALIAS), lookups)
        assertEquals(1024L * 1024L + 1, aiFile().length())
    }

    @Test fun cancellationPropagatesAndDoesNotMasqueradeAsAnAccessFailure() = runBlocking {
        writeAi(aiFile(), mapOf("protocol" to "ANTHROPIC", "api_key" to "fixture-key"))
        val before = aiFile().readBytes()
        try {
            reader(LegacyServiceSettings.ExistingKeys { throw CancellationException("synthetic cancellation") }).readOnly()
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertArrayEquals(before, aiFile().readBytes())
        }
    }

    @Test fun declarationOrEntityPayloadIsRejectedWithoutFollowingExternalReferences() = runBlocking {
        aiFile().writeText("""<?xml version="1.0"?><!DOCTYPE map [<!ENTITY source SYSTEM "file:///unreadable-synthetic-fixture">]><map><string name="protocol">&source;</string></map>""")
        val before = aiFile().readBytes()
        reader().readOnly().use { result ->
            assertTrue(result.preview.aiAccessFailed)
            assertTrue(result.entries.isEmpty())
        }
        assertTrue(lookups.isEmpty())
        assertArrayEquals(before, aiFile().readBytes())
    }

    private fun aiFile() = File(prefs, "${LegacyServiceSettings.AI_PREFERENCES}.xml")
    private fun brokerageFile() = File(noBackup, "brokerage-v1.enc")

    private fun writeAi(file: File, fields: Map<String, String>) {
        val keyset = KeysetHandle.generateNew(AesSivKeyManager.aes256SivTemplate())
        val valueset = KeysetHandle.generateNew(AesGcmKeyManager.aes256GcmTemplate())
        val keys = keyset.getPrimitive(DeterministicAead::class.java)
        val values = valueset.getPrimitive(Aead::class.java)
        val wrappedKeys = linkedMapOf(
            LegacyServiceSettings.KEY_KEYSET to encryptedKeyset(keyset),
            LegacyServiceSettings.VALUE_KEYSET to encryptedKeyset(valueset),
        )
        for ((name, value) in fields) {
            val encryptedName = Base64.encodeToString(keys.encryptDeterministically(
                name.toByteArray(), LegacyServiceSettings.AI_PREFERENCES.toByteArray(),
            ), Base64.NO_WRAP)
            val text = value.toByteArray()
            val typedValue = ByteBuffer.allocate(8 + text.size).putInt(0).putInt(text.size).put(text).array()
            wrappedKeys[encryptedName] = Base64.encodeToString(
                values.encrypt(typedValue, encryptedName.toByteArray()), Base64.NO_WRAP,
            )
        }
        writePreferences(file, wrappedKeys)
    }

    private fun encryptedKeyset(handle: KeysetHandle): String {
        val stream = ByteArrayOutputStream()
        handle.write(BinaryKeysetWriter.withOutputStream(stream), object : Aead {
            override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray =
                encrypt(plaintext, master, associatedData)
            override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, master, GCMParameterSpec(128, ciphertext, 0, 12))
                cipher.updateAAD(associatedData)
                return cipher.doFinal(ciphertext, 12, ciphertext.size - 12)
            }
        })
        return stream.toByteArray().joinToString("") { "%02x".format(it) }
    }

    private fun writePreferences(file: File, fields: Map<String, String>) {
        val writer = StringWriter()
        val xml = Xml.newSerializer().apply { setOutput(writer); startDocument("UTF-8", true); startTag(null, "map") }
        fields.forEach { (name, value) ->
            xml.startTag(null, "string").attribute(null, "name", name).text(value).endTag(null, "string")
        }
        xml.endTag(null, "map"); xml.endDocument()
        file.writeText(writer.toString())
    }

    private fun encrypt(plaintext: ByteArray, key: SecretKey, aad: ByteArray = ByteArray(0)): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(aad)
        check(cipher.iv.size == 12)
        return cipher.iv + cipher.doFinal(plaintext)
    }

    private fun brokerageJson() = """[
      {"id":"fixture-1","providerId":"ibkr_flex","label":"Synthetic","credentials":{"fields":{"token":"fixture-token","queryId":"fixture-query","unknown":"kept-for-Core"}},"syncedAt":123456789,
       "accounts":[{"accountId":"U-SYNTHETIC","asOf":"2026-10-01","holdings":[{"instrumentId":"fixture-instrument","symbol":"TEST","description":"Synthetic","currency":"USD","quantity":"0.1234567890123456789","marketValue":"-123.4567890123456789"}],"cashBalances":[{"currency":"USD","endingCash":"-0.00000000000001"}]}]},
      {"id":"fixture-2","providerId":"future_provider","label":"Preserved for Core decision","credentials":{"fields":{"token":"fixture-future"}},"accounts":[],"syncedAt":0}
    ]""".trimIndent()
}
