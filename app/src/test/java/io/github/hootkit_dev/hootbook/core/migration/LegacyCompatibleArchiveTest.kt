package io.github.hootkit_dev.hootbook.core.migration

import android.app.Application
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.assertFailsWith
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
class LegacyCompatibleArchiveTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun emitsExactFrozenEnvelopeWithoutQuickTemplatesAndPreservesOtherValues() {
        val recoveryDocument = recoveryDocument()
        val rawDocument = recoveryDocument.toString().toByteArray(Charsets.UTF_8)
        val recovery = recoveryArchive(linkedMapOf("legacy-data.json" to rawDocument))
        val originalBytes = recovery.readBytes()
        val target = File(temporary.root, "compatible.backup")

        val metadata = LegacyCompatibleArchive.create(recovery, target, emptyMap())

        ZipFile(target).use { zip ->
            assertEquals(listOf("backup.json"), names(zip))
            assertNull(zip.getEntry("legacy-data.json"))
            val compatible = JSONObject(bytes(zip, "backup.json").toString(Charsets.UTF_8))
            assertEquals("Cashiro Backup v1.0", compatible.getString("_format"))
            assertEquals(recoveryDocument.getString("_created"), compatible.getString("_created"))
            assertEquals(recoveryDocument.getJSONObject("metadata").toString(), metadata.toString())
            assertEquals(metadata.toString(), compatible.getJSONObject("metadata").toString())
            assertEquals(recoveryDocument.getJSONObject("preferences").toString(),
                compatible.getJSONObject("preferences").toString())
            val database = compatible.getJSONObject("database")
            assertFalse(database.has("quick_templates"))
            assertEquals(setOf("accounts", "transactions", "custom_exchange_rates"), database.keys().asSequence().toSet())
            for (table in listOf("accounts", "transactions", "custom_exchange_rates")) {
                assertEquals(recoveryDocument.getJSONObject("database").getJSONArray(table).toString(),
                    database.getJSONArray(table).toString())
            }
            val transaction = database.getJSONArray("transactions").getJSONObject(0)
            assertEquals("0.1234567890123456789", transaction.getString("amount"))
            assertEquals(9_007_199_254_740_991L, transaction.getLong("id"))
            assertTrue(transaction.getBoolean("isDeleted"))
            assertTrue(transaction.isNull("notes"))
            assertEquals("保留原文 ☕", database.getJSONArray("accounts").getJSONObject(0).getString("name"))
        }
        assertArrayEquals(originalBytes, recovery.readBytes())
        ZipFile(recovery).use { zip ->
            assertArrayEquals(rawDocument, bytes(zip, "legacy-data.json"))
            val retained = JSONObject(bytes(zip, "legacy-data.json").toString(Charsets.UTF_8))
            assertEquals("Hootbook Legacy Recovery v1", retained.getString("_format"))
            val templates = retained.getJSONObject("database").getJSONArray("quick_templates")
            assertEquals(2, templates.length())
            assertEquals("0.0000000000000000001", templates.getJSONObject(0).getString("amount"))
            assertEquals("First synthetic template", templates.getJSONObject(0).getString("name"))
            assertEquals(2, retained.getJSONObject("metadata").getJSONObject("androidOmissions").getInt("templates"))
        }
    }

    @Test fun copiesAttachmentAvatarAndServicePayloadsByteForByteButOmitsRawRecoverySources() {
        val rawDocument = recoveryDocument().toString().toByteArray(Charsets.UTF_8)
        val rawDatabase = byteArrayOf(0, 83, 81, 76, 105, 116, 101, -1)
        val rawWal = byteArrayOf(0, -1, 16, 32)
        val rawPreferences = byteArrayOf(10, 3, 1, 0, -1)
        val attachment = byteArrayOf(0, 1, 2, 10, 13, -128, -1)
        val avatar = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
        val entries = linkedMapOf(
            "legacy-data.json" to rawDocument,
            "source-database/" to byteArrayOf(),
            "source-database/pennywise_database" to rawDatabase,
            "source-database/pennywise_database-wal" to rawWal,
            "source-database/pennywise_database-shm" to byteArrayOf(3, 4, 5),
            "source-database/nested/raw-copy" to byteArrayOf(6, 7, 8),
            "source-preferences/user_preferences.preferences_pb" to rawPreferences,
            "attachments/" to byteArrayOf(),
            "attachments/synthetic-receipt.bin" to attachment,
            "avatars/synthetic-contact.png" to avatar,
        )
        val recovery = recoveryArchive(entries)
        val originalBytes = recovery.readBytes()
        // Deliberately synthetic credentials and whitespace. Transport must not parse/rewrite them.
        val ai = "{\n  \"protocol\" : \"OPENAI_COMPATIBLE\", \"api_key\" : \"synthetic-test-key-only\"\n}\n"
            .toByteArray(Charsets.UTF_8)
        val brokerage = "[ { \"id\" : \"synthetic-broker\", \"token\" : \"synthetic-test-token-only\", \"accounts\" : [ { \"holdings\" : [ { \"quantity\" : \"1.2300000000000000000\" } ] } ] } ]\n"
            .toByteArray(Charsets.UTF_8)
        val services = linkedMapOf("ai.json" to ai, "brokerage.json" to brokerage)
        val serviceBytesBefore = services.mapValues { it.value.copyOf() }
        val target = File(temporary.root, "compatible.backup")

        LegacyCompatibleArchive.create(recovery, target, services)

        ZipFile(target).use { zip ->
            assertEquals(setOf("backup.json", "attachments/synthetic-receipt.bin", "avatars/synthetic-contact.png",
                "ai.json", "brokerage.json"), names(zip).toSet())
            assertTrue(names(zip).none { it.startsWith("source-database/") || it.startsWith("source-preferences/") })
            assertTrue(zip.entries().asSequence().none { it.isDirectory })
            assertArrayEquals(attachment, bytes(zip, "attachments/synthetic-receipt.bin"))
            assertArrayEquals(avatar, bytes(zip, "avatars/synthetic-contact.png"))
            assertArrayEquals(ai, bytes(zip, "ai.json"))
            assertArrayEquals(brokerage, bytes(zip, "brokerage.json"))
        }
        assertArrayEquals(originalBytes, recovery.readBytes())
        serviceBytesBefore.forEach { (name, expected) -> assertArrayEquals(expected, services.getValue(name)) }
        ZipFile(recovery).use { zip ->
            assertEquals(entries.keys.toList(), names(zip))
            entries.forEach { (name, expected) -> assertArrayEquals(expected, bytes(zip, name)) }
            assertNull(zip.getEntry("ai.json"))
            assertNull(zip.getEntry("brokerage.json"))
        }
    }

    @Test fun absentTemplatesAndServiceSettingsDoNotInventEntriesOrChangeMetadata() {
        val document = recoveryDocument().apply { getJSONObject("database").remove("quick_templates") }
        val recovery = recoveryArchive(mapOf("legacy-data.json" to document.toString().toByteArray()))
        val target = File(temporary.root, "compatible.backup")

        val metadata = LegacyCompatibleArchive.create(recovery, target, emptyMap())

        assertEquals(document.getJSONObject("metadata").toString(), metadata.toString())
        ZipFile(target).use { zip ->
            assertEquals(listOf("backup.json"), names(zip))
            assertNull(zip.getEntry("ai.json"))
            assertNull(zip.getEntry("brokerage.json"))
            val compatible = JSONObject(bytes(zip, "backup.json").toString(Charsets.UTF_8))
            assertFalse(compatible.getJSONObject("database").has("quick_templates"))
        }
    }

    @Test fun rejectsSameSourceAndExistingDestinationWithoutOverwritingEither() {
        val recovery = recoveryArchive(mapOf("legacy-data.json" to recoveryDocument().toString().toByteArray()))
        val originalBytes = recovery.readBytes()
        assertFailsWith<IllegalArgumentException> { LegacyCompatibleArchive.create(recovery, recovery, emptyMap()) }
        assertArrayEquals(originalBytes, recovery.readBytes())
        val target = File(temporary.root, "existing.backup").apply { writeText("existing file stays intact") }
        assertFailsWith<IllegalArgumentException> { LegacyCompatibleArchive.create(recovery, target, emptyMap()) }
        assertEquals("existing file stays intact", target.readText())
        assertArrayEquals(originalBytes, recovery.readBytes())
    }

    @Test fun rejectsUnrecognizedServiceEntriesBeforeCreatingOutput() {
        val recovery = recoveryArchive(mapOf("legacy-data.json" to recoveryDocument().toString().toByteArray()))
        val originalBytes = recovery.readBytes()
        for ((index, name) in listOf("backup.json", "platform/ai.json", "../ai.json", "AI.json").withIndex()) {
            val target = File(temporary.root, "rejected-$index.backup")
            assertFailsWith<IllegalArgumentException> {
                LegacyCompatibleArchive.create(recovery, target, mapOf(name to byteArrayOf(1)))
            }
            assertFalse(target.exists())
        }
        assertArrayEquals(originalBytes, recovery.readBytes())
    }

    @Test fun rejectsWrongEnvelopeOrMissingDocumentWithoutChangingRecovery() {
        val wrong = recoveryDocument().put("_format", "Cashiro Backup v1.0")
        val recovery = recoveryArchive(mapOf("legacy-data.json" to wrong.toString().toByteArray()))
        val before = recovery.readBytes()
        val target = File(temporary.root, "wrong-envelope.backup")
        assertFailsWith<IllegalArgumentException> { LegacyCompatibleArchive.create(recovery, target, emptyMap()) }
        assertFalse(target.exists())
        assertArrayEquals(before, recovery.readBytes())

        val missing = recoveryArchive(mapOf("unrelated.json" to "{}".toByteArray()), "missing-document.backup")
        val missingBefore = missing.readBytes()
        val missingTarget = File(temporary.root, "missing-target.backup")
        assertFailsWith<IllegalArgumentException> { LegacyCompatibleArchive.create(missing, missingTarget, emptyMap()) }
        assertFalse(missingTarget.exists())
        assertArrayEquals(missingBefore, missing.readBytes())
    }

    private fun recoveryArchive(entries: Map<String, ByteArray>, name: String = "recovery.backup"): File =
        File(temporary.root, name).also { file ->
            ZipOutputStream(file.outputStream()).use { zip ->
                entries.forEach { (entry, data) ->
                    zip.putNextEntry(ZipEntry(entry))
                    zip.write(data)
                    zip.closeEntry()
                }
            }
        }

    private fun names(zip: ZipFile): List<String> = zip.entries().asSequence().map { it.name }.toList()

    private fun bytes(zip: ZipFile, name: String): ByteArray =
        zip.getInputStream(requireNotNull(zip.getEntry(name))).use { it.readBytes() }

    private fun recoveryDocument() = JSONObject("""
        {
          "_format": "Hootbook Legacy Recovery v1",
          "_created": "2026-01-02T03:04:05",
          "metadata": {
            "database_version": 72,
            "export_id": "11111111-1111-1111-1111-111111111111",
            "legacyBridge": {"format":"hootbook.android.legacy-bridge","version":1,"sourceSchemaVersion":72},
            "androidOmissions": {"templates":2,"defaultAccount":true,"missingAttachments":["missing-synthetic.bin"]},
            "sourceFingerprints": {"pennywise_database":"synthetic-fingerprint","pennywise_database-wal":null}
          },
          "database": {
            "accounts": [{"id":21,"name":"保留原文 ☕","mainCurrency":"CNY","balance":"1.2300"}],
            "transactions": [{"id":9007199254740991,"amount":"0.1234567890123456789","currency":"CNY","isDeleted":true,"notes":null}],
            "custom_exchange_rates": [{"fromCurrency":"X-CUSTOM","toCurrency":"CNY","rate":"7.0000000000000000001"}],
            "quick_templates": [
              {"id":31,"name":"First synthetic template","amount":"0.0000000000000000001","prefillAmount":true},
              {"id":32,"name":"Second synthetic template","amount":null,"prefillAmount":false}
            ]
          },
          "preferences": {"account":{"defaultAccountId":21},"currency":{"unified_currency_enabled":true,"unified_currency_code":"CNY"}}
        }
    """.trimIndent())
}
