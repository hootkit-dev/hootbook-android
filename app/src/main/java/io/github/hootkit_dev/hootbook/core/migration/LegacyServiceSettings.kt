package io.github.hootkit_dev.hootbook.core.migration

import android.content.Context
import android.util.Base64
import android.util.Xml
import com.google.crypto.tink.Aead
import com.google.crypto.tink.BinaryKeysetReader
import com.google.crypto.tink.DeterministicAead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.daead.DeterministicAeadConfig
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Collections
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.xmlpull.v1.XmlPullParser

/**
 * Android-only, one-time format transport for this app's existing service settings.
 *
 * This never constructs the old repositories, opens SharedPreferences, recovers an AtomicFile,
 * creates a key, or writes a source file. Those otherwise convenient APIs can mutate old data on
 * read/failure. Only existing encrypted keysets/keys are accepted. Provider support, validation,
 * duplicate handling and importing remain exclusively with Core's legacy importer.
 *
 * The returned bytes contain credentials. Keep them in the private migration archive only, close
 * the export after writing it, and expose only [Preview] to UI. Never log the bytes or exceptions
 * from crypto/JSON libraries. No network client is used here.
 */
class LegacyServiceSettings internal constructor(
    private val preferencesDirectory: File,
    private val noBackupDirectory: File,
    private val existingKeys: ExistingKeys,
) {
    constructor(context: Context) : this(
        File(context.applicationInfo.dataDir, "shared_prefs"),
        File(context.applicationInfo.dataDir, "no_backup"),
        ExistingKeys { alias ->
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                .getKey(alias, null) as? SecretKey
        },
    )

    /** A deliberately lookup-only dependency, also allowing synthetic keys in isolated tests. */
    internal fun interface ExistingKeys {
        fun find(alias: String): SecretKey?
    }

    /** Counts describe decoded input, not which providers/records Core will accept. */
    data class Preview(
        val aiPresent: Boolean = false,
        val aiExported: Boolean = false,
        val aiAccessFailed: Boolean = false,
        val aiRequiresSetup: Boolean = false,
        val brokeragePresent: Boolean = false,
        val brokerageConnections: Int = 0,
        val brokerageAccounts: Int = 0,
        val brokerageHoldings: Int = 0,
        val brokerageAccessFailed: Boolean = false,
        val aiConfigured: Boolean? = null,
        val aiHasKey: Boolean? = null,
    )

    /** Intentionally not a data class: generated toString/copy must not expose credentials. */
    class Export internal constructor() : Closeable {
        private val ownedEntries = linkedMapOf<String, ByteArray>()
        val entries: Map<String, ByteArray> = Collections.unmodifiableMap(ownedEntries)
        var preview: Preview = Preview()
            internal set

        internal fun add(name: String, bytes: ByteArray) {
            check(name == "ai.json" || name == "brokerage.json")
            ownedEntries.put(name, bytes)?.fill(0)
        }

        override fun close() {
            ownedEntries.values.forEach { it.fill(0) }
            ownedEntries.clear()
        }

        override fun toString(): String = "LegacyServiceSettings.Export([REDACTED], preview=$preview)"
    }

    suspend fun readOnly(): Export {
        // Own the buffers outside withContext so cancellation while returning from IO also wipes
        // them instead of losing a just-created secret-bearing result.
        val result = Export()
        try {
            withContext(Dispatchers.IO) {
                readAi(result)
                currentCoroutineContext().ensureActive()
                readBrokerage(result)
            }
            currentCoroutineContext().ensureActive()
            return result
        } catch (error: Throwable) {
            result.close()
            throw error
        }
    }

    private suspend fun readAi(result: Export) {
        val encrypted = File(preferencesDirectory, "$AI_PREFERENCES.xml")
        val fallback = File(preferencesDirectory, "cashiro_ai_prefs.xml")
        val encryptedPresent = present(encrypted)
        val source = if (encryptedPresent) encrypted else fallback
        if (!present(source)) return
        result.preview = result.preview.copy(aiPresent = true)
        try {
            currentCoroutineContext().ensureActive()
            val bytes = stableRead(source, MAX_AI_BYTES)
            val stored = try { parsePreferenceStrings(bytes) } finally { bytes.fill(0) }
            val fields = if (encryptedPresent) decodeAiPreferences(stored) else {
                // The old fallback intentionally never persisted the API key. Do not recover a
                // stray plaintext api_key, and do not combine it with a different secure config.
                stored.filterKeys { it in AI_FIELDS && it != "api_key" }
            }
            currentCoroutineContext().ensureActive()
            if (fields.isNotEmpty()) {
                result.add("ai.json", JsonObject(fields.mapValues { JsonPrimitive(it.value) })
                    .toString().toByteArray(Charsets.UTF_8))
            }
            result.preview = result.preview.copy(
                aiExported = fields.isNotEmpty(),
                aiRequiresSetup = fields.isNotEmpty() && fields["api_key"].isNullOrBlank(),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Preserve the original store and report only a safe category. Never substitute an
            // empty key, reset the store, or expose an exception containing a token/URL/value.
            result.preview = result.preview.copy(aiAccessFailed = true, aiRequiresSetup = true)
        }
    }

    private fun decodeAiPreferences(stored: Map<String, String>): Map<String, String> {
        if (stored.isEmpty()) return emptyMap()
        val key = existingKeys.find(AI_KEY_ALIAS) ?: throw GeneralSecurityException()
        AeadConfig.register()
        DeterministicAeadConfig.register()
        val master = DecryptOnlyAead(key)
        val keys = readKeyset(stored[KEY_KEYSET] ?: throw GeneralSecurityException(), master)
            .getPrimitive(DeterministicAead::class.java)
        val values = readKeyset(stored[VALUE_KEYSET] ?: throw GeneralSecurityException(), master)
            .getPrimitive(Aead::class.java)
        val fields = linkedMapOf<String, String>()
        for ((encryptedName, encryptedValue) in stored) {
            if (encryptedName == KEY_KEYSET || encryptedName == VALUE_KEYSET) continue
            val nameBytes = keys.decryptDeterministically(
                Base64.decode(encryptedName, Base64.DEFAULT), AI_PREFERENCES.toByteArray(Charsets.UTF_8),
            )
            val name = try { nameBytes.toString(Charsets.UTF_8) } finally { nameBytes.fill(0) }
            if (name !in AI_FIELDS) continue
            val valueBytes = values.decrypt(
                Base64.decode(encryptedValue, Base64.DEFAULT), encryptedName.toByteArray(Charsets.UTF_8),
            )
            val value = try { decodePreferenceString(valueBytes) } finally { valueBytes.fill(0) }
            if (value != null) {
                check(!fields.containsKey(name))
                fields[name] = value
            }
        }
        return fields
    }

    private suspend fun readBrokerage(result: Export) {
        val source = File(noBackupDirectory, "brokerage-v1.enc")
        if (!present(source)) return
        result.preview = result.preview.copy(brokeragePresent = true)
        var plaintext: ByteArray? = null
        try {
            currentCoroutineContext().ensureActive()
            val payload = stableRead(source, MAX_BROKERAGE_BYTES)
            plaintext = try {
                require(payload.size >= 29 && payload[0] == 1.toByte())
                val key = existingKeys.find(BROKERAGE_KEY_ALIAS) ?: throw GeneralSecurityException()
                decryptGcm(payload, key, offset = 1)
            } finally { payload.fill(0) }
            currentCoroutineContext().ensureActive()
            // Preserve the serialized bytes, including exact decimal strings, credential fields,
            // unknown providers and optional fields. Core decides what is supported in preview.
            val connections = Json.parseToJsonElement(plaintext.toString(Charsets.UTF_8)) as? JsonArray
                ?: throw IllegalArgumentException()
            var accounts = 0
            var holdings = 0
            for (connection in connections) {
                currentCoroutineContext().ensureActive()
                val list = (connection as? JsonObject)?.get("accounts") as? JsonArray ?: continue
                accounts += list.size
                for (account in list) {
                    holdings += ((account as? JsonObject)?.get("holdings") as? JsonArray)?.size ?: 0
                }
            }
            if (connections.isNotEmpty()) {
                result.add("brokerage.json", plaintext)
                plaintext = null // Export owns and wipes this buffer from here.
            }
            result.preview = result.preview.copy(
                brokerageConnections = connections.size,
                brokerageAccounts = accounts,
                brokerageHoldings = holdings,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            result.preview = result.preview.copy(brokerageAccessFailed = true)
        } finally {
            plaintext?.fill(0)
        }
    }

    /** Match Android's committed .bak precedence without invoking its mutating recovery path. */
    private fun present(file: File): Boolean = file.exists() || File(file.path + ".bak").exists()

    private suspend fun stableRead(file: File, limit: Int): ByteArray {
        val candidates = listOf(file, File(file.path + ".bak"))
        val before = candidates.map { fingerprint(it, limit) }
        val source = if (before[1] != null) candidates[1] else candidates[0]
        val bytes = readBounded(source, limit)
        try {
            check(MessageDigest.getInstance("SHA-256").digest(bytes).toList() == before[if (source == candidates[1]) 1 else 0])
            check(before == candidates.map { fingerprint(it, limit) })
            return bytes
        } catch (error: Throwable) {
            bytes.fill(0)
            throw error
        }
    }

    private suspend fun fingerprint(file: File, limit: Int): List<Byte>? {
        if (!file.exists()) return null
        val bytes = readBounded(file, limit)
        return try { MessageDigest.getInstance("SHA-256").digest(bytes).toList() }
        finally { bytes.fill(0) }
    }

    private suspend fun readBounded(file: File, limit: Int): ByteArray {
        require(file.isFile && file.length() <= limit)
        val buffer = ByteArray(16 * 1024)
        try {
            return file.inputStream().use { input ->
                ByteArrayOutputStream().use { output ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size().toLong() + count <= limit)
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
            }
        } finally { buffer.fill(0) }
    }

    private fun parsePreferenceStrings(bytes: ByteArray): Map<String, String> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
        val result = linkedMapOf<String, String>()
        var sawMap = false
        var endedMap = false
        var event = parser.nextToken()
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.DOCDECL -> throw IllegalArgumentException()
                XmlPullParser.START_TAG -> when {
                    parser.depth == 1 && parser.name == "map" && !sawMap -> sawMap = true
                    parser.depth == 2 && parser.name == "string" && sawMap && !endedMap -> {
                        val name = parser.getAttributeValue(null, "name") ?: throw IllegalArgumentException()
                        check(!result.containsKey(name))
                        result[name] = parser.nextText()
                    }
                    else -> throw IllegalArgumentException()
                }
                XmlPullParser.END_TAG -> if (parser.depth == 1 && parser.name == "map") endedMap = true
                XmlPullParser.TEXT -> require(parser.isWhitespace)
                XmlPullParser.ENTITY_REF -> throw IllegalArgumentException()
            }
            event = parser.nextToken()
        }
        require(sawMap && endedMap)
        return result
    }

    private fun readKeyset(hex: String, master: Aead): KeysetHandle {
        require(hex.length % 2 == 0)
        val bytes = ByteArray(hex.length / 2) { index ->
            val high = hex[index * 2].digitToIntOrNull(16) ?: throw IllegalArgumentException()
            val low = hex[index * 2 + 1].digitToIntOrNull(16) ?: throw IllegalArgumentException()
            ((high shl 4) or low).toByte()
        }
        return try { KeysetHandle.read(BinaryKeysetReader.withBytes(bytes), master) }
        finally { bytes.fill(0) }
    }

    private fun decodePreferenceString(bytes: ByteArray): String? {
        require(bytes.size >= 8)
        val input = ByteBuffer.wrap(bytes)
        require(input.int == 0) // EncryptedSharedPreferences STRING type.
        val length = input.int
        require(length >= 0 && length == input.remaining())
        return Charsets.UTF_8.decode(input).toString().takeUnless { it == "__NULL__" }
    }

    /** Tink's keyset unwrap requires only decrypt; encryption is intentionally impossible here. */
    private class DecryptOnlyAead(private val key: SecretKey) : Aead {
        override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray =
            throw GeneralSecurityException("Read-only legacy key access")

        override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray =
            decryptGcm(ciphertext, key, offset = 0, associatedData = associatedData)
    }

    companion object {
        internal const val AI_PREFERENCES = "cashiro_ai_secure_prefs"
        internal const val AI_KEY_ALIAS = "_androidx_security_master_key_"
        internal const val BROKERAGE_KEY_ALIAS = "cashiro_brokerage_v1"
        internal const val KEY_KEYSET = "__androidx_security_crypto_encrypted_prefs_key_keyset__"
        internal const val VALUE_KEYSET = "__androidx_security_crypto_encrypted_prefs_value_keyset__"
        private val AI_FIELDS = setOf("protocol", "base_url", "model", "api_key")
        private const val MAX_AI_BYTES = 1024 * 1024
        private const val MAX_BROKERAGE_BYTES = 16 * 1024 * 1024

        private fun decryptGcm(
            payload: ByteArray,
            key: SecretKey,
            offset: Int,
            associatedData: ByteArray = ByteArray(0),
        ): ByteArray {
            require(payload.size >= offset + 12 + 16)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, payload, offset, 12))
            cipher.updateAAD(associatedData)
            return cipher.doFinal(payload, offset + 12, payload.size - offset - 12)
        }
    }
}
