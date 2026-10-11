package io.github.hootkit_dev.hootbook.core.migration

import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import dev.hootkit.core.LegacyImportSummary
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Uses the packaged Android Core library, with no production Application, Hilt graph, or keys. */
class IsolatedLegacyPreviewNativeTest {
    @Test fun frozenNativeImportReportsExactCountsAndAiStatusWithoutTouchingProduction() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("Run with IsolatedUiTestRunner so production startup cannot race the assertions",
            Application::class.java, context.applicationContext.javaClass)
        val productionDatabases = listOf("hootkit.sqlite", "pennywise_database").flatMap { name ->
            val database = context.getDatabasePath(name)
            listOf(database, File(database.path + "-wal"), File(database.path + "-shm"), File(database.path + ".lock"))
        }
        val databaseBefore = productionDatabases.associate { it.path to fingerprint(it) }
        val productionRoot = File(context.noBackupFilesDir, "hootkit-core")
        val productionBefore = inventory(productionRoot)
        val fixture = File(context.cacheDir, "legacy-native-preview-${UUID.randomUUID()}")
        check(fixture.mkdir())
        try {
            val source = File(fixture, "compatible.backup")
            writeFixture(source)
            val sourceBefore = fingerprint(source)
            val temporaryRoot = File(fixture, "preview-parent").apply { check(mkdir()) }
            val sentinel = File(temporaryRoot, "caller-owned.txt").apply { writeText("keep this file") }
            val aiStatuses = mutableListOf<Pair<Boolean, Boolean>>()
            var firstSummary: LegacyImportSummary? = null

            // Each invocation must get an empty Core and memory SecureStore, even with identical
            // legacy sync IDs, native import markers, and synthetic AI/broker credentials.
            repeat(2) {
                val summary = IsolatedLegacyPreview().preview(source, temporaryRoot, "zh-CN") { configured, hasKey ->
                    aiStatuses += configured to hasKey
                }
                assertEquals(mapOf("accounts" to 1u, "anchors" to 1u, "transactions" to 1u, "attachments" to 1u),
                    summary.imported.associate { count -> count.item to count.count })
                assertEquals(mapOf("unknown_transactions" to 1u, "brokerage_connections" to 1u),
                    summary.dropped.associate { count -> count.item to count.count })
                assertEquals("CNY", summary.mainCurrency)
                assertTrue(summary.renamedCurrencies.isEmpty())
                assertEquals(listOf("profile/profile_image"), summary.platformFiles)
                val preferences = JSONObject(requireNotNull(summary.preferencesJson))
                assertEquals("Synthetic preview profile", preferences.getJSONObject("profile").getString("user_name"))
                if (firstSummary == null) firstSummary = summary else assertEquals(firstSummary, summary)
                assertEquals(sourceBefore, fingerprint(source))
                assertEquals(listOf(sentinel), temporaryRoot.listFiles()!!.toList())
                assertEquals("keep this file", sentinel.readText())
                assertEquals(databaseBefore, productionDatabases.associate { file -> file.path to fingerprint(file) })
                assertEquals(productionBefore, inventory(productionRoot))
            }
            assertEquals(listOf(true to true, true to true), aiStatuses)
        } finally {
            // The test only removes its own UUID-named fixture directory.
            check(fixture.deleteRecursively())
        }
        assertEquals(databaseBefore, productionDatabases.associate { it.path to fingerprint(it) })
        assertEquals(productionBefore, inventory(productionRoot))
    }

    private fun writeFixture(target: File) {
        // Keys/date/decimal formats follow the exact frozen legacy parser. The second transaction
        // deliberately has an unsupported kind; it must appear in dropped, not disappear silently.
        val backup = """
            {
              "_format":"Cashiro Backup v1.0",
              "_created":"2026-01-03T12:00:00",
              "database":{
                "accounts":[{
                  "id":1,"syncId":"synthetic-preview-account","name":"Synthetic preview wallet",
                  "last4":"wallet","mainCurrency":"CNY","isWallet":true,"createdAt":"2026-01-01T00:00:00"
                }],
                "account_balances":[{
                  "id":1,"accountId":1,"bankName":"Synthetic preview wallet","accountLast4":"wallet",
                  "balance":"50.00","currency":"CNY","timestamp":"2026-01-02T13:00:00",
                  "sourceType":"MANUAL","isWallet":true,"createdAt":"2026-01-02T13:00:00"
                }],
                "transactions":[{
                  "id":1,"syncId":"synthetic-preview-transaction","accountId":1,
                  "merchantName":"Synthetic preview merchant","amount":"3.25","category":"Others",
                  "transactionType":"EXPENSE","currency":"CNY","dateTime":"2026-01-02T12:00:00",
                  "createdAt":"2026-01-02T12:00:00","updatedAt":"2026-01-02T12:00:00",
                  "attachments":["attachments/synthetic-receipt.bin"]
                },{
                  "id":2,"accountId":1,"amount":"2.00","transactionType":"UNSUPPORTED_PREVIEW_KIND",
                  "currency":"CNY","dateTime":"2026-01-02T12:00:00",
                  "createdAt":"2026-01-02T12:00:00","updatedAt":"2026-01-02T12:00:00"
                }]
              },
              "preferences":{
                "currency":{"unified_currency_enabled":true,"unified_currency_code":"CNY"},
                "profile":{"user_name":"Synthetic preview profile"}
              }
            }
        """.trimIndent().toByteArray(Charsets.UTF_8)
        val ai = """
            {"protocol":"OPENAI_COMPATIBLE","base_url":"https://example.invalid/v1",
             "model":"synthetic-preview-model","api_key":"synthetic-preview-key-never-real"}
        """.trimIndent().toByteArray(Charsets.UTF_8)
        // All-zero numeric credentials satisfy the frozen local format validator. They are
        // synthetic, and the helper's deny transport cannot contact any brokerage endpoint.
        val brokerage = """
            [{"id":"synthetic-valid","providerId":"ibkr_flex","label":"Synthetic broker",
              "credentials":{"fields":{"token":"000000","queryId":"0"}},
              "accounts":[{"accountId":"SYNTHETIC-PREVIEW-ACCOUNT","asOf":"2026-01-02",
                "holdings":[],"cashBalances":[{"currency":"CNY","endingCash":"12.50"}]}]},
             {"id":"synthetic-unsupported","providerId":"unsupported_preview_provider","accounts":[]}]
        """.trimIndent().toByteArray(Charsets.UTF_8)
        val entries = linkedMapOf(
            "backup.json" to backup,
            "attachments/synthetic-receipt.bin" to byteArrayOf(0, 1, 2, 3, -1),
            "profile/profile_image" to byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10),
            "ai.json" to ai,
            "brokerage.json" to brokerage,
        )
        ZipOutputStream(target.outputStream()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    private fun fingerprint(file: File): String? {
        if (!file.exists()) return null
        check(file.isFile)
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun inventory(root: File): Map<String, String?> {
        if (!root.exists()) return emptyMap()
        return root.walkTopDown().associate { file ->
            file.relativeTo(root).invariantSeparatorsPath to if (file.isDirectory) "directory" else fingerprint(file)
        }
    }
}
