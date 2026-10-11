package io.github.hootkit_dev.hootbook.core.migration

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.hootkit.core.LegacyImportSummary
import io.github.hootkit_dev.hootbook.core.backup.*
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=Application::class)
class LegacyDatabaseSnapshotTest {
    private lateinit var context: Context
    private lateinit var source: File
    private lateinit var directory: File
    @Before fun setUp() {
        context=ApplicationProvider.getApplicationContext()
        source=context.getDatabasePath("pennywise_database")
        source.parentFile!!.mkdirs()
        directory=File(context.cacheDir,"migration-${UUID.randomUUID()}").also { it.mkdirs() }
        val schema=context.assets.open("migration/legacy-v72.json").use { JSONObject(it.bufferedReader().readText()) }
        SQLiteDatabase.openOrCreateDatabase(source,null).use { db ->
            db.version=72
            db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            db.execSQL("INSERT INTO room_master_table VALUES (42,?)", arrayOf(schema.getString("identityHash")))
            val tables=schema.getJSONArray("tables")
            for(i in 0 until tables.length()) {
                val table=tables.getJSONObject(i); val fields=table.getJSONArray("fields")
                val columns=(0 until fields.length()).joinToString { n -> fields.getJSONObject(n).let {
                    "`${it.getString("column")}` ${if(it.getString("type") in setOf("boolean","integer")) "INTEGER" else "TEXT"}"
                } }
                db.execSQL("CREATE TABLE `${table.getString("name")}` ($columns)")
            }
            db.insertOrThrow("accounts",null,row(schema,"accounts").apply {
                put("id",21L);put("name","Bank_with_underscore");put("last4","6789");put("main_currency","CNY")
            })
            db.insertOrThrow("quick_templates",null,row(schema,"quick_templates").apply {
                put("id",99L);put("name","Coffee");put("amount","0.1234567890123456789");put("prefill_amount",1)
            })
        }
        context.getSharedPreferences("account_prefs",Context.MODE_PRIVATE).edit().putString("main_account","Bank_with_underscore_6789").commit()
    }
    private fun row(schema:JSONObject,name:String):ContentValues {
        val tables=schema.getJSONArray("tables");val table=(0 until tables.length()).map(tables::getJSONObject).single {it.getString("name")==name}
        return ContentValues().apply { val fields=table.getJSONArray("fields")
            for(i in 0 until fields.length()) { val f=fields.getJSONObject(i);val key=f.getString("column")
                if(!f.getBoolean("required"))putNull(key)
                else when(f.getString("type")){"boolean","integer"->put(key,0L);else->put(key,"")}
            }
        }
    }
    @After fun cleanup() { context.deleteDatabase("pennywise_database"); context.deleteDatabase("hootkit.sqlite"); LegacyUpgradeGuard(context).migrationRoot.deleteRecursively(); directory.deleteRecursively();context.getSharedPreferences("account_prefs",Context.MODE_PRIVATE).edit().clear().commit() }
    @Test fun recoveryPreservesExactTemplatesDefaultAndSourceWithoutClaimingImport() = runBlocking {
        val before=LegacyDatabaseSnapshot.fingerprint(source)
        val archive=LegacyDatabaseSnapshot(context).capture(directory,UUID.randomUUID().toString())
        assertEquals(before,LegacyDatabaseSnapshot.fingerprint(source))
        ZipFile(archive).use { zip ->
            assertNull(zip.getEntry("backup.json")) // Cannot accidentally feed a lossy old importer.
            assertNotNull(zip.getEntry("source-database/pennywise_database"))
            val data=JSONObject(zip.getInputStream(zip.getEntry("legacy-data.json")).bufferedReader().readText())
            assertEquals("Hootbook Legacy Recovery v1",data.getString("_format"))
            val template=data.getJSONObject("database").getJSONArray("quick_templates").getJSONObject(0)
            assertEquals("0.1234567890123456789",template.getString("amount"))
            assertTrue(template.getBoolean("prefillAmount"))
            val omissions=data.getJSONObject("metadata").getJSONObject("omissions")
            assertEquals(1,omissions.getInt("templates"))
            assertTrue(omissions.getBoolean("defaultAccount"))
            assertEquals("Bank_with_underscore_6789",omissions.getString("originalDefaultAccountKey"))
        }
        assertTrue(LegacyUpgradeGuard(context).required())
    }
    @Test fun lazyNativeOpenIsBlockedForDatabaseOrOrphanWal() {
        val guard=LegacyUpgradeGuard(context)
        assertThrows(IllegalStateException::class.java) { guard.requireCoreReady() }
        assertTrue(context.deleteDatabase("pennywise_database"))
        guard.requireCoreReady()
        val wal=File(source.path+"-wal")
        try {
            wal.writeText("preserved orphan WAL fixture")
            assertThrows(IllegalStateException::class.java) { guard.requireCoreReady() }
            assertEquals("preserved orphan WAL fixture",wal.readText())
        } finally { wal.delete() }
    }
    @Test fun completionRequiresActualCommittedSummaryAndMatchingJournal() = runBlocking {
        val guard=LegacyUpgradeGuard(context)
        val files=CoreBackupFiles(guard.migrationRoot)
        val id=files.newId();val input=File(files.operation(id),"fixture.backup").apply {writeText("synthetic")}
        val source=files.verify(id,input)
        val summary=LegacyImportSummary(emptyList(),emptyList(),"CNY",null,emptyList(),emptyMap())
        val operation=BackupOperation(source,true,BackupPhase.PLATFORM_APPLIED,legacySummary=summary)
        assertThrows(IllegalStateException::class.java) { guard.complete(operation) }
        context.getDatabasePath("hootkit.sqlite").writeText("synthetic committed database marker")
        assertThrows(IllegalStateException::class.java) { guard.complete(operation.copy(phase=BackupPhase.UNCERTAIN)) }
        BackupOperationJournal(files).write(operation)
        guard.complete(operation)
        assertFalse(guard.required())
        assertTrue(context.getDatabasePath("pennywise_database").isFile)
        BackupOperationJournal(files).write(operation.copy(phase=BackupPhase.UNCERTAIN,legacySummary=null))
        assertTrue(guard.required())
    }
    @Test fun committedWalRowsAreCapturedWithoutCheckpointingTheOriginal() = runBlocking {
        SQLiteDatabase.openDatabase(source.path,null,SQLiteDatabase.OPEN_READWRITE).use { db ->
            assertTrue(db.enableWriteAheadLogging())
            db.execSQL("UPDATE quick_templates SET name = 'Written in WAL' WHERE id = 99")
            val originals=listOf(source,File(source.path+"-wal"),File(source.path+"-shm"))
            val before=originals.associateWith { LegacyDatabaseSnapshot.fingerprint(it) }
            assertNotNull(before[File(source.path+"-wal")])
            val archive=LegacyDatabaseSnapshot(context).capture(directory,UUID.randomUUID().toString())
            assertEquals(before,originals.associateWith { LegacyDatabaseSnapshot.fingerprint(it) })
            ZipFile(archive).use { zip ->
                assertNotNull(zip.getEntry("source-database/pennywise_database-wal"))
                val data=JSONObject(zip.getInputStream(zip.getEntry("legacy-data.json")).bufferedReader().readText())
                assertEquals("Written in WAL",data.getJSONObject("database").getJSONArray("quick_templates").getJSONObject(0).getString("name"))
            }
        }
    }
    @Test fun unknownSchemaFailsWithoutMutatingSource() = runBlocking {
        SQLiteDatabase.openDatabase(source.path,null,SQLiteDatabase.OPEN_READWRITE).use {it.version=73}
        val before=LegacyDatabaseSnapshot.fingerprint(source)
        assertTrue(runCatching {LegacyDatabaseSnapshot(context).capture(directory,UUID.randomUUID().toString())}.isFailure)
        assertEquals(before,LegacyDatabaseSnapshot.fingerprint(source))
    }
    @Test fun unresolvedDefaultIsCountedAndPreservedWithoutBlockingSupportedData() = runBlocking {
        context.getSharedPreferences("account_prefs",Context.MODE_PRIVATE).edit().putString("main_account","missing_1234").commit()
        val before=LegacyDatabaseSnapshot.fingerprint(source)
        val archive=LegacyDatabaseSnapshot(context).capture(directory,UUID.randomUUID().toString())
        ZipFile(archive).use { zip ->
            val data=JSONObject(zip.getInputStream(zip.getEntry("legacy-data.json")).bufferedReader().readText())
            val omissions=data.getJSONObject("metadata").getJSONObject("omissions")
            assertTrue(omissions.getBoolean("defaultAccount"))
            assertEquals("missing_1234",omissions.getString("originalDefaultAccountKey"))
        }
        assertEquals(before,LegacyDatabaseSnapshot.fingerprint(source))
    }
    @Test fun legacyEmptyArraySentinelDoesNotInventAMissingAttachment() = runBlocking {
        val schema=context.assets.open("migration/legacy-v72.json").use { JSONObject(it.bufferedReader().readText()) }
        SQLiteDatabase.openDatabase(source.path,null,SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.insertOrThrow("lend_borrow_transactions",null,row(schema,"lend_borrow_transactions").apply {
                put("id",1L);put("attachments","[]")
            })
        }
        val archive=LegacyDatabaseSnapshot(context).capture(directory,UUID.randomUUID().toString())
        ZipFile(archive).use { zip ->
            val data=JSONObject(zip.getInputStream(zip.getEntry("legacy-data.json")).bufferedReader().readText())
            assertEquals(0,data.getJSONObject("database").getJSONArray("lend_borrow_transactions").getJSONObject(0).getJSONArray("attachments").length())
            assertEquals(0,data.getJSONObject("metadata").getJSONObject("omissions").getJSONArray("missingAttachments").length())
        }
    }
    @Test fun missingAttachmentsAreCountedWhileLedgerAndSourceRemainIntact() = runBlocking {
        val schema=context.assets.open("migration/legacy-v72.json").use { JSONObject(it.bufferedReader().readText()) }
        SQLiteDatabase.openDatabase(source.path,null,SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.insertOrThrow("transactions",null,row(schema,"transactions").apply {
                put("id",1L);put("attachments","attachments/missing.jpg")
            })
        }
        val before=LegacyDatabaseSnapshot.fingerprint(source)
        val archive=LegacyDatabaseSnapshot(context).capture(directory,UUID.randomUUID().toString())
        ZipFile(archive).use { zip ->
            val data=JSONObject(zip.getInputStream(zip.getEntry("legacy-data.json")).bufferedReader().readText())
            assertEquals(1,data.getJSONObject("database").getJSONArray("transactions").length())
            assertEquals(1,data.getJSONObject("metadata").getJSONObject("omissions").getJSONArray("missingAttachments").length())
        }
        assertEquals(before,LegacyDatabaseSnapshot.fingerprint(source))
    }
}
