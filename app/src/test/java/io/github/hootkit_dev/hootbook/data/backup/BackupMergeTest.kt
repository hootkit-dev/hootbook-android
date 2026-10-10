package io.github.hootkit_dev.hootbook.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.hootkit_dev.hootbook.data.ai.AiConfig
import io.github.hootkit_dev.hootbook.data.ai.AiProtocol
import io.github.hootkit_dev.hootbook.data.ai.AiSettings
import io.github.hootkit_dev.hootbook.data.brokerage.BrokerageRepository
import io.github.hootkit_dev.hootbook.data.brokerage.BrokerageStore
import io.github.hootkit_dev.hootbook.data.brokerage.SavedBrokerConnection
import io.github.hootkit_dev.hootbook.data.database.HootbookDatabase
import io.github.hootkit_dev.hootbook.data.database.SyncTriggers
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.data.database.entity.LendBorrowPersonEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionType
import io.github.hootkit_dev.hootbook.data.icons.MerchantIconStore
import io.github.hootkit_dev.hootbook.data.preferences.UserPreferencesRepository
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Merging a backup (or a sync snapshot) again adds nothing: no copies of transactions, balance rows or people. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupMergeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val start = LocalDateTime.of(2026, 9, 1, 9, 0)
    private val databases = mutableListOf<HootbookDatabase>()

    private class MemoryStore : BrokerageStore {
        override fun read() = emptyList<SavedBrokerConnection>()
        override fun write(connections: List<SavedBrokerConnection>) = Unit
    }

    private fun database() = Room.inMemoryDatabaseBuilder(context, HootbookDatabase::class.java)
        .addCallback(SyncTriggers.Callback).allowMainThreadQueries().build().also { databases += it }

    @After fun tearDown() = databases.forEach { it.close() }

    private val prefs = UserPreferencesRepository(context)
    private val brokerage = BrokerageRepository(MemoryStore(), emptySet()) { 0L }
    private val icons = MerchantIconStore(context)
    private val ai = AiSettings(context)

    private fun exporter(db: HootbookDatabase, settings: AiSettings = ai) = BackupExporter(context, db, prefs, brokerage, icons, settings)
    private fun importer(db: HootbookDatabase, settings: AiSettings = ai) = BackupImporter(context, db, prefs, brokerage, icons, settings)

    private suspend fun HootbookDatabase.fill() {
        accountBalanceDao().insertBalance(
            AccountBalanceEntity(bankName = "Bank", accountLast4 = "1234", balance = BigDecimal("1000"),
                timestamp = start, sourceType = "MANUAL", currency = "CNY")
        )
        (1..3).forEach { i ->
            val at = start.plusDays(i.toLong())
            val id = transactionDao().insertTransaction(
                TransactionEntity(amount = BigDecimal("10"), merchantName = "Shop $i", category = "Shopping",
                    transactionType = TransactionType.EXPENSE, dateTime = at, bankName = "Bank", accountNumber = "1234",
                    transactionHash = "hash-$i", currency = "CNY")
            )
            accountBalanceDao().insertBalance(
                AccountBalanceEntity(bankName = "Bank", accountLast4 = "1234", balance = BigDecimal(1000 - 10 * i),
                    timestamp = at, transactionId = id, sourceType = "TRANSACTION", currency = "CNY")
            )
        }
        lendBorrowDao().insertPerson(LendBorrowPersonEntity(name = "Alex"))
        accountBalanceDao().linkTransactionsToAccounts()
    }

    private suspend fun HootbookDatabase.counts() = listOf(
        transactionDao().getAllTransactions().first().size,
        accountBalanceDao().getAllBalances().first().size,
        accountDao().getAccounts().size,
        lendBorrowDao().getAllPersons().first().size
    )

    private suspend fun backupOf(db: HootbookDatabase, config: BackupConfiguration = BackupConfiguration()): Uri {
        val result = exporter(db).exportBackup(config)
        assertTrue("export failed: $result", result is ExportResult.Success)
        return Uri.fromFile((result as ExportResult.Success).file)
    }

    @Test fun mergingTheSameBackupTwiceAddsNothingTheSecondTime() = runTest {
        val source = database().apply { fill() }
        val backup = backupOf(source)
        val target = database()

        assertTrue(importer(target).importBackup(backup, ImportStrategy.MERGE) is ImportResult.Success)
        val once = target.counts()
        assertEquals(source.counts(), once)

        assertTrue(importer(target).importBackup(backup, ImportStrategy.MERGE) is ImportResult.Success)
        assertEquals(once, target.counts())
    }

    @Test fun mergingADevicesOwnSnapshotChangesNothing() = runTest {
        val db = database().apply { fill() }
        val before = db.counts()
        val latest = db.accountBalanceDao().getAllBalances().first().maxBy { it.timestamp }.balance
        assertTrue(importer(db).importBackup(backupOf(db), ImportStrategy.MERGE) is ImportResult.Success)
        assertEquals(before, db.counts())
        assertEquals(latest, db.accountBalanceDao().getAllBalances().first().maxBy { it.timestamp }.balance)
    }

    private suspend fun HootbookDatabase.syncIds() = mapOf(
        "transactions" to transactionDao().getAllTransactions().first().map { it.syncId }.toSet(),
        "balances" to accountBalanceDao().getAllBalances().first().map { it.syncId }.toSet(),
        "accounts" to accountDao().getAccounts().map { it.syncId }.toSet(),
        "people" to lendBorrowDao().getAllPersons().first().map { it.syncId }.toSet()
    )

    @Test fun aFullRestoreKeepsTheSyncIds() = runTest {
        val source = database().apply { fill() }
        val target = database()
        assertTrue(importer(target).importBackup(backupOf(source), ImportStrategy.REPLACE_ALL) is ImportResult.Success)
        val ids = source.syncIds()
        assertTrue(ids.values.flatten().all { it.length == 32 })
        assertEquals(ids, target.syncIds())
        // A restore queues every record it wrote
        val queued = target.syncDao().pending().filter { it.op == "UPSERT" }.map { it.syncId }.toSet()
        assertTrue(queued.containsAll(ids.values.flatten()))
    }

    @Test fun aRecordEditedElsewhereIsMergedIntoItselfBySyncId() = runTest {
        val source = database().apply { fill() }
        val target = database()
        importer(target).importBackup(backupOf(source), ImportStrategy.REPLACE_ALL)

        // Edited on the source: its hash changes, its sync id does not
        val edited = source.transactionDao().getAllTransactions().first().first { it.transactionHash == "hash-1" }
        source.transactionDao().updateTransaction(
            edited.copy(merchantName = "Market", transactionHash = "hash-1-edited", updatedAt = edited.updatedAt.plusDays(1))
        )
        val before = target.counts()
        assertTrue(importer(target).importBackup(backupOf(source), ImportStrategy.MERGE) is ImportResult.Success)
        assertEquals(before, target.counts())
        val merged = target.transactionDao().getAllTransactions().first().single { it.syncId == edited.syncId }
        assertEquals("Market", merged.merchantName)
    }

    @Test fun mergingABackupFromAnotherDeviceNeverCollidesOnSyncIds() = runTest {
        val source = database().apply { fill() }
        val backup = backupOf(source)
        // The target holds the backup's person under another name: merged by name, the backup's
        // person is new here, but its sync id is taken
        val target = database()
        importer(target).importBackup(backup, ImportStrategy.REPLACE_ALL)
        val alex = target.lendBorrowDao().getAllPersonsSync().single()
        target.lendBorrowDao().updatePerson(alex.copy(name = "Sam"))
        assertTrue(importer(target).importBackup(backup, ImportStrategy.MERGE) is ImportResult.Success)
        val people = target.lendBorrowDao().getAllPersonsSync().associateBy { it.name }
        assertEquals(setOf("Sam", "Alex"), people.keys)
        assertEquals(alex.syncId, people.getValue("Sam").syncId)
        assertNotEquals(alex.syncId, people.getValue("Alex").syncId)
        assertEquals(32, people.getValue("Alex").syncId.length)
    }

    /** The backup at [uri] as an app without sync ids wrote it. */
    private fun withoutSyncFields(uri: Uri): Uri {
        fun strip(value: Any?): Any? = when (value) {
            is org.json.JSONObject -> value.apply {
                remove("syncId"); remove("syncUpdatedAt")
                keys().asSequence().toList().forEach { strip(opt(it)) }
            }
            is org.json.JSONArray -> value.apply { (0 until length()).forEach { strip(opt(it)) } }
            else -> value
        }
        val out = java.io.File(context.cacheDir, "old-backup.zip")
        java.util.zip.ZipInputStream(java.io.File(uri.path!!).inputStream()).use { zip ->
            java.util.zip.ZipOutputStream(out.outputStream()).use { dest ->
                generateSequence { zip.nextEntry }.forEach { entry ->
                    var bytes = zip.readBytes()
                    if (entry.name == "backup.json") {
                        val json = org.json.JSONObject(String(bytes, Charsets.UTF_8))
                        strip(json)
                        bytes = json.toString().toByteArray(Charsets.UTF_8)
                        assertTrue("syncId" !in String(bytes))
                    }
                    dest.putNextEntry(java.util.zip.ZipEntry(entry.name))
                    dest.write(bytes)
                    dest.closeEntry()
                }
            }
        }
        return Uri.fromFile(out)
    }

    @Test fun aBackupWithoutSyncIdsStillImports() = runTest {
        val source = database().apply { fill() }
        val old = withoutSyncFields(backupOf(source))
        listOf(ImportStrategy.REPLACE_ALL, ImportStrategy.MERGE).forEach { strategy ->
            val target = database()
            val result = importer(target).importBackup(old, strategy)
            assertTrue("$strategy: $result", result is ImportResult.Success)
            assertEquals(source.counts(), target.counts())
            val ids = target.syncIds().values.flatten()
            assertTrue(ids.all { it.length == 32 })
            // Fresh ids, not the source's
            assertTrue(ids.none { it in source.syncIds().values.flatten() })
        }
    }

    private fun entries(uri: Uri): Set<String> = java.util.zip.ZipInputStream(java.io.File(uri.path!!).inputStream()).use { zip ->
        generateSequence { zip.nextEntry }.map { it.name }.toSet()
    }

    @Test fun theAiKeyIsInABackupOnlyWhenAskedFor() = runTest {
        ai.save(AiConfig(AiProtocol.ANTHROPIC, AiProtocol.ANTHROPIC.defaultBaseUrl, "model", "sk-test"))
        val db = database()
        assertTrue(BackupExporter.AI_ENTRY !in entries(backupOf(db)))
        assertTrue(BackupExporter.AI_ENTRY in entries(backupOf(db, BackupConfiguration(includeAiKey = true))))
    }

    @Test fun aRestoredKeyFillsAnEmptySettingButNeverReplacesOne() = runTest {
        ai.save(AiConfig(AiProtocol.OPENAI_COMPATIBLE, "https://example.com/v1", "model", "sk-backup"))
        val backup = backupOf(database(), BackupConfiguration(includeAiKey = true))

        val empty = AiSettings(context).apply { save(AiConfig()) }
        importer(database(), empty).importBackup(backup, ImportStrategy.MERGE)
        assertEquals("sk-backup", empty.config.value.apiKey)
        assertEquals(AiProtocol.OPENAI_COMPATIBLE, empty.config.value.protocol)

        val own = AiSettings(context).apply { save(AiConfig(apiKey = "sk-mine")) }
        importer(database(), own).importBackup(backup, ImportStrategy.MERGE)
        assertEquals("sk-mine", own.config.value.apiKey)
    }
}
