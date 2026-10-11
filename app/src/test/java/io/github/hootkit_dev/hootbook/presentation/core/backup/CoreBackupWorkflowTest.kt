package io.github.hootkit_dev.hootbook.presentation.core.backup

import android.app.Application
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.core.backup.*
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

internal fun backupTestInfo() = BackupInfo(LocalDateTime(2026, 10u, 10u, 12u, 0u, 0u, 0u), "0.1", null,
    1u, false, listOf(TableCount(Table.TRANSACTIONS, 2u)), BackupIncludes(true, false, false, false))
internal class BackupTestAccess : CoreBackupAccess {
    var imports = 0; var legacyImports = 0; var lastOptions: ImportOptions? = null
    var exportOptions: ExportOptions? = null; var failure: Exception? = null; var previouslyImported = false
    var onImport: ((String) -> Unit)? = null
    var beforeReturn: (suspend () -> Unit)? = null
    var summary = ImportSummary(2u, 1u, 3u, null, emptyList(), false, 0u, null)
    val legacySummary = LegacyImportSummary(listOf(LegacyCount("transactions", 2u)), listOf(LegacyCount("cards", 1u)),
        "CNY", "{\"defaultAccount\":123}", emptyList(), mapOf("积分" to "X109"))
    private val backup = object : BackupInterface {
        override suspend fun exportBackup(path: String, options: ExportOptions): BackupInfo {
            exportOptions = options; File(path).writeText("synthetic-export"); return backupTestInfo()
        }
        override suspend fun inspectBackup(path: String, password: String?): BackupInfo = backupTestInfo()
        override suspend fun importBackup(path: String, options: ImportOptions): ImportSummary {
            imports++; lastOptions = options; onImport?.invoke(options.platformDir); beforeReturn?.invoke(); failure?.let { throw it }; return summary
        }
    }
    private val legacy = object : LegacyInterface {
        override suspend fun importedAt(): LocalDateTime? = if (previouslyImported) backupTestInfo().createdAt else null
        override suspend fun inspectBackup(path: String, password: String?): LegacyInfo = LegacyInfo(false, null, 1u, 2u)
        override suspend fun importBackup(path: String, options: LegacyOptions): LegacyImportSummary {
            legacyImports++; failure?.let { throw it }; return legacySummary
        }
    }
    override suspend fun <T> backup(block: suspend (BackupInterface) -> T): T = block(backup)
    override suspend fun <T> legacy(block: suspend (LegacyInterface) -> T): T = block(legacy)
}

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], manifest = Config.NONE)
class CoreBackupWorkflowTest {
    @get:Rule val temp = TemporaryFolder()
    private val root get() = temp.root
    private fun workflow(access: BackupTestAccess, files: CoreBackupFiles = CoreBackupFiles(root)) =
        CoreBackupWorkflow(access, files, BackupOperationJournal(files), root)
    private suspend fun source(files: CoreBackupFiles) = files.stage { ByteArrayInputStream("source-original".toByteArray()) }
    @Test fun separatePreviewSummarySurvivesUncertainCommitJournal() = runTest {
        val files = CoreBackupFiles(root); val source = source(files)
        val journal = BackupOperationJournal(files)
        val preview = BackupOperation(source, true, BackupPhase.PREPARED, legacySummary = BackupTestAccess().legacySummary)
        journal.writePreview(preview)
        journal.write(preview.copy(phase = BackupPhase.IMPORTING, legacySummary = null))
        val recovered = workflow(BackupTestAccess(), files).recover().single()
        assertEquals(BackupPhase.UNCERTAIN, recovered.phase)
        assertNull(recovered.legacySummary)
        assertEquals(preview, journal.readPreview(source.operationId))
    }
    @Test fun mergeCallsCoreOnceAndPreservesOriginal() = runTest {
        val access = BackupTestAccess(); val files = CoreBackupFiles(root); val source = source(files)
        val operation = workflow(access, files).restore(source, false, ImportMode.MERGE, "secret", false)
        assertEquals(1, access.imports); assertEquals(ImportMode.MERGE, access.lastOptions!!.mode)
        assertEquals(BackupPhase.PLATFORM_APPLIED, operation.phase); assertEquals(access.summary, operation.importSummary)
        assertEquals("source-original", source.file.readText())
        assertFalse(File(source.file.parentFile, "journal.json").readText().contains("secret"))
    }
    @Test fun replaceNeedsConfirmationBeforeCoreCallAndReturnsCoreSafetyPath() = runTest {
        val access = BackupTestAccess(); val files = CoreBackupFiles(root); val source = source(files); val workflow = workflow(access, files)
        try { workflow.restore(source, false, ImportMode.REPLACE_ALL, null, false); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(0, access.imports)
        access.summary = access.summary.copy(safetyBackup = "/private/core/before-restore.hkbak")
        assertEquals(access.summary.safetyBackup, workflow.restore(source, false, ImportMode.REPLACE_ALL, null, true).importSummary!!.safetyBackup)
    }
    @Test fun legacyOnlyUserFileAndCoreSummaryIsPreserved() = runTest {
        val access = BackupTestAccess(); val files = CoreBackupFiles(root); val source = source(files)
        val operation = workflow(access, files).restore(source, true, ImportMode.MERGE, null, false)
        assertEquals(1, access.legacyImports); assertEquals(0, access.imports)
        assertEquals(access.legacySummary, operation.legacySummary)
        val review = File(source.file.parentFile, "preferences-review.json")
        assertEquals(access.legacySummary.preferencesJson, review.readText())
        assertEquals(access.legacySummary, BackupOperationJournal(files).read(source.operationId)!!.legacySummary)
    }
    @Test fun historicalImportedAtDoesNotOverrideCoreLegacyEmptyLedgerValidation() = runTest {
        val access = BackupTestAccess().apply { previouslyImported = true }; val files = CoreBackupFiles(root)
        val result = workflow(access, files).restore(source(files), true, ImportMode.MERGE, null, false)
        assertEquals(1, access.legacyImports); assertEquals(BackupPhase.PLATFORM_APPLIED, result.phase)
    }
    @Test fun interruptedNativeReturnBecomesUncertainAndCannotReplay() = runTest {
        val access = BackupTestAccess().apply { failure = java.io.IOException("connection lost") }; val files = CoreBackupFiles(root)
        val workflow = workflow(access, files); val source = source(files)
        try { workflow.restore(source, false, ImportMode.MERGE, null, false); fail() } catch (_: java.io.IOException) { }
        assertEquals(BackupPhase.UNCERTAIN, workflow.recover().single().phase)
        access.failure = null
        try { workflow.restore(source, false, ImportMode.MERGE, null, false); fail() } catch (_: IllegalStateException) { }
        assertEquals(1, access.imports)
    }
    @Test fun storageIoAfterSimulatedCommitBecomesUncertainAndNeverReplays() = runTest {
        val committedLedger = File(root, "simulated-committed-ledger")
        val access = BackupTestAccess().apply {
            onImport = { committedLedger.writeText("committed-once") }
            failure = CoreException.Storage(StorageErrorKind.IO, "writer closed after commit")
        }
        val files = CoreBackupFiles(File(root, "operations")); val source = source(files)
        val workflow = CoreBackupWorkflow(access, files, BackupOperationJournal(files), File(root, "operations"))
        try { workflow.restore(source, false, ImportMode.MERGE, null, false); fail() } catch (_: CoreException.Storage) { }
        assertEquals("committed-once", committedLedger.readText())
        val persisted = BackupOperationJournal(files).read(source.operationId)!!
        assertEquals(BackupPhase.UNCERTAIN, persisted.phase); assertTrue(persisted.blocksImport)
        access.failure = null; access.onImport = { fail("Duplicate ledger mutation") }
        val restarted = CoreBackupWorkflow(access, files, BackupOperationJournal(files), File(root, "operations"))
        assertEquals(BackupPhase.UNCERTAIN, restarted.recover().single().phase)
        try { restarted.restore(source, false, ImportMode.MERGE, null, false); fail() } catch (_: IllegalStateException) { }
        assertEquals(1, access.imports); assertEquals("committed-once", committedLedger.readText())
    }
    @Test fun knownFilePasswordPreflightFailuresPermitExplicitCorrectedFileRetry() = runTest {
        val known = listOf(CoreException.Invalid("file", InvalidReason.BAD_FORMAT),
            CoreException.Invalid("file", InvalidReason.UNKNOWN_VALUE),
            CoreException.Invalid("password", InvalidReason.REQUIRED),
            CoreException.Invalid("password", InvalidReason.BAD_FORMAT))
        for (error in known) {
            val operationRoot = temp.newFolder(); val files = CoreBackupFiles(operationRoot)
            val access = BackupTestAccess().apply { failure = error }
            val workflow = CoreBackupWorkflow(access, files, BackupOperationJournal(files), operationRoot)
            val wrong = source(files)
            try { workflow.restore(wrong, false, ImportMode.MERGE, "wrong", false); fail() } catch (_: CoreException.Invalid) { }
            assertEquals(BackupPhase.FAILED, workflow.recover().single().phase)
            assertFalse(workflow.recover().single().blocksImport)
            access.failure = null
            val correctedFile = source(files)
            assertEquals(BackupPhase.PLATFORM_APPLIED, workflow.restore(correctedFile, false, ImportMode.MERGE, "correct", false).phase)
            assertEquals(2, access.imports); assertTrue(wrong.file.exists())
        }
    }
    @Test fun schemaTooNewPreflightAllowsExplicitCompatibleFileRetry() = runTest {
        val access = BackupTestAccess().apply { failure = CoreException.Storage(StorageErrorKind.SCHEMA_TOO_NEW, "future schema") }
        val files = CoreBackupFiles(root); val workflow = workflow(access, files); val futureFile = source(files)
        try { workflow.restore(futureFile, false, ImportMode.MERGE, null, false); fail() } catch (_: CoreException.Storage) { }
        assertEquals(BackupPhase.FAILED, workflow.recover().single().phase)
        access.failure = null
        assertEquals(BackupPhase.PLATFORM_APPLIED, workflow.restore(source(files), false, ImportMode.MERGE, null, false).phase)
        assertEquals(2, access.imports); assertTrue(futureFile.file.exists())
    }
    @Test fun otherTypedErrorsAndCancellationStayUncertainAndPersistentlyBlockReplay() = runTest {
        val unknown = listOf(CoreException.Storage(StorageErrorKind.CORRUPT, "unknown commit"),
            CoreException.Storage(StorageErrorKind.FULL, "unknown commit"),
            CoreException.Platform("secure store callback interrupted"),
            CoreException.Invalid("account", InvalidReason.REQUIRED),
            CoreException.Invalid("file", InvalidReason.REQUIRED),
            CoreException.Invalid("password", InvalidReason.EMPTY),
            CoreException.Conflict(ConflictReason.ACCOUNT_EXISTS),
            CoreException.Cancelled(), kotlinx.coroutines.CancellationException("interrupted"))
        for (error in unknown) {
            val operationRoot = temp.newFolder(); val files = CoreBackupFiles(operationRoot)
            val access = BackupTestAccess().apply { failure = error }
            val workflow = CoreBackupWorkflow(access, files, BackupOperationJournal(files), operationRoot); val source = source(files)
            try { workflow.restore(source, false, ImportMode.MERGE, null, false); fail() } catch (_: Exception) { }
            assertEquals(BackupPhase.UNCERTAIN, workflow.recover().single().phase)
            assertTrue(BackupOperationJournal(files).read(source.operationId)!!.blocksImport)
            access.failure = null
            try { workflow.restore(source, false, ImportMode.MERGE, null, false); fail() } catch (_: IllegalStateException) { }
            assertEquals(1, access.imports)
        }
    }
    @Test fun preflightWhitelistIsSpecificToFieldReasonAndOperation() {
        assertTrue(hasPreflightFailureEvidence(CoreException.Conflict(ConflictReason.NOT_EMPTY), legacy = true))
        assertFalse(hasPreflightFailureEvidence(CoreException.Conflict(ConflictReason.NOT_EMPTY), legacy = false))
        assertTrue(hasPreflightFailureEvidence(CoreException.Storage(StorageErrorKind.SCHEMA_TOO_NEW, "source schema"), legacy = false))
        assertFalse(hasPreflightFailureEvidence(CoreException.Storage(StorageErrorKind.SCHEMA_TOO_NEW, "unknown legacy error"), legacy = true))
        assertFalse(hasPreflightFailureEvidence(CoreException.Invalid("platform_files", InvalidReason.BAD_FORMAT), legacy = false))
        assertFalse(hasPreflightFailureEvidence(CoreException.Invalid("password", InvalidReason.UNKNOWN_VALUE), legacy = false))
    }
    @Test fun processDeathBetweenCommitAndReceiptNeverAutomaticallyImports() = runTest {
        val access = BackupTestAccess(); val files = CoreBackupFiles(root); val source = source(files)
        BackupOperationJournal(files).write(BackupOperation(source, false, BackupPhase.IMPORTING))
        val restored = workflow(access, files).recover().single()
        assertEquals(BackupPhase.UNCERTAIN, restored.phase); assertNull(restored.importSummary); assertTrue(restored.blocksImport)
        assertEquals(0, access.imports)
    }
    @Test fun platformFailureRetainsCommittedReceiptAndRetryDoesNotReimport() = runTest {
        val access = BackupTestAccess().apply { summary = summary.copy(platformFiles = listOf("unknown/asset"), preferencesJson = "{\"currency\":\"BAD\"}") }
        val files = CoreBackupFiles(root); val source = source(files); val workflow = workflow(access, files)
        try { workflow.restore(source, false, ImportMode.MERGE, null, false); fail() } catch (_: IllegalArgumentException) { }
        val pending = workflow.recover().single(); assertEquals(BackupPhase.CORE_COMMITTED, pending.phase)
        File(files.platform(source.operationId), "unknown/asset").apply { parentFile.mkdirs(); writeText("asset") }
        assertEquals(BackupPhase.PLATFORM_APPLIED, workflow.finishPlatform(pending).phase)
        workflow.finishPlatform(pending); assertEquals(1, access.imports)
        assertEquals("{\"currency\":\"BAD\"}", File(source.file.parentFile, "preferences-review.json").readText())
    }
    @Test fun typedNotEmptyPreservesFileAndGivesExplicitRecovery() = runTest {
        val error = CoreException.Conflict(ConflictReason.NOT_EMPTY)
        val access = BackupTestAccess().apply { failure = error }; val files = CoreBackupFiles(root); val source = source(files)
        try { workflow(access, files).restore(source, true, ImportMode.MERGE, null, false); fail() } catch (_: CoreException.Conflict) { }
        assertEquals(BackupPhase.FAILED, workflow(access, files).recover().single().phase)
        assertTrue(source.file.exists()); assertTrue(backupFailureText(error).contains("空账本"))
        access.failure = null // The test explicitly changes the preflight condition; no automatic retry.
        val correctedFile = source(files)
        assertEquals(BackupPhase.PLATFORM_APPLIED,
            workflow(access, files).restore(correctedFile, true, ImportMode.MERGE, null, false).phase)
        assertEquals(2, access.legacyImports); assertTrue(source.file.exists())
    }
    @Test fun schemaAndProtocolErrorsDoNotLeakStorageDetails() {
        assertTrue(backupFailureText(CoreException.Storage(StorageErrorKind.SCHEMA_TOO_NEW, "secret financial data")).contains("升级"))
        assertFalse(backupFailureText(CoreException.Storage(StorageErrorKind.CORRUPT, "secret financial data")).contains("secret"))
        assertTrue(backupFailureText(CoreException.Invalid("file", InvalidReason.UNKNOWN_VALUE)).contains("协议版本"))
    }
    @Test fun exportForwardsEncryptionAndIncludesExactlyToCore() = runTest {
        val access = BackupTestAccess(); val options = ExportOptions(false, true, true, "password", null, emptyList(), "device")
        val (file, info) = workflow(access).export(options)
        assertEquals(options, access.exportOptions); assertEquals(backupTestInfo(), info)
        assertEquals("synthetic-export", file.file.readText())
    }
    @Test fun unknownPlatformAssetsRoundTripAsBackupFilesNotDiscarded() = runTest {
        val access = BackupTestAccess().apply { summary = summary.copy(platformFiles = listOf("future/unknown.bin"))
            onImport = { path -> File(path, "future/unknown.bin").apply { parentFile.mkdirs(); writeText("opaque") } } }
        val files = CoreBackupFiles(root); val workflow = workflow(access, files)
        workflow.restore(source(files), false, ImportMode.MERGE, null, false)
        val result = workflow.preservedAssets().single()
        assertEquals("platform/future/unknown.bin", result.entryName); assertEquals("opaque", File(result.path).readText())
    }
    @Test fun platformApplyFailureAndRetryNeverReimportsLedger() = runTest {
        val access = BackupTestAccess(); val files = CoreBackupFiles(root); val source = source(files)
        var attempts = 0; var failApply = true
        val workflow = CoreBackupWorkflow(access, files, BackupOperationJournal(files), root) { operation ->
            assertEquals(BackupPhase.CORE_COMMITTED, operation.phase)
            attempts++
            if (failApply) throw java.io.IOException("platform image install interrupted")
        }
        try { workflow.restore(source, false, ImportMode.MERGE, null, false); fail() } catch (_: java.io.IOException) { }
        val pending = workflow.recover().single()
        assertEquals(BackupPhase.CORE_COMMITTED, pending.phase); assertEquals(1, access.imports)
        failApply = false
        assertEquals(BackupPhase.PLATFORM_APPLIED, workflow.finishPlatform(pending).phase)
        assertEquals(2, attempts); assertEquals(1, access.imports); assertTrue(source.file.exists())
    }
    @Test fun cancelledPlatformApplyLeavesCommittedStatusForIdempotentRetry() = runTest {
        val access = BackupTestAccess(); val files = CoreBackupFiles(root); val source = source(files)
        val workflow = CoreBackupWorkflow(access, files, BackupOperationJournal(files), root) {
            throw kotlinx.coroutines.CancellationException("platform cancellation")
        }
        try { workflow.restore(source, false, ImportMode.MERGE, null, false); fail() } catch (_: kotlinx.coroutines.CancellationException) { }
        assertEquals(BackupPhase.CORE_COMMITTED, workflow.recover().single().phase)
        assertEquals(1, access.imports); assertTrue(source.file.exists())
    }

}
