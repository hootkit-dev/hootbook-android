package io.github.hootkit_dev.hootbook.presentation.core.backup

import android.app.Application
import dev.hootkit.core.CoreException
import dev.hootkit.core.ImportMode
import dev.hootkit.core.InvalidReason
import io.github.hootkit_dev.hootbook.core.backup.BackupOperationJournal
import io.github.hootkit_dev.hootbook.core.backup.BackupPhase
import io.github.hootkit_dev.hootbook.core.backup.CoreBackupFiles
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], manifest = Config.NONE)
class CoreBackupRefreshHookTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun nativeAndLegacyCommitNotifyOnlyAfterDurableCommitReceipt() = runTest {
        for (legacy in listOf(false, true)) {
            val root = temp.newFolder()
            val files = CoreBackupFiles(root)
            val source = files.stage { ByteArrayInputStream("synthetic backup".toByteArray()) }
            val journal = BackupOperationJournal(files)
            var callbacks = 0
            var phaseAtCallback: BackupPhase? = null
            val access = object : CoreBackupAccess by BackupTestAccess() {
                override fun importCommitted() {
                    callbacks++
                    phaseAtCallback = journal.read(source.operationId)!!.phase
                }
            }
            val workflow = CoreBackupWorkflow(access, files, journal, root)
            workflow.inspect(source, legacy, null)
            assertEquals(0, callbacks)
            val result = workflow.restore(source, legacy, ImportMode.MERGE, null, false)
            assertEquals(BackupPhase.PLATFORM_APPLIED, result.phase)
            assertEquals(1, callbacks)
            assertEquals(BackupPhase.CORE_COMMITTED, phaseAtCallback)
            workflow.recover()
            workflow.finishPlatform(result)
            assertEquals(1, callbacks)
        }
    }

    @Test fun failedOrUncertainImportsNeverNotifyOrRefresh() = runTest {
        for (failure in listOf(CoreException.Invalid("file", InvalidReason.BAD_FORMAT), IOException("Synthetic interruption"))) {
            val root = temp.newFolder()
            val files = CoreBackupFiles(root)
            val source = files.stage { ByteArrayInputStream("synthetic backup".toByteArray()) }
            var callbacks = 0
            val delegate = BackupTestAccess().apply { this.failure = failure }
            val access = object : CoreBackupAccess by delegate {
                override fun importCommitted() { callbacks++ }
            }
            val workflow = CoreBackupWorkflow(access, files, BackupOperationJournal(files), root)
            assertTrue(runCatching { workflow.restore(source, false, ImportMode.MERGE, null, false) }.isFailure)
            assertEquals(0, callbacks)
        }
    }

    @Test fun platformApplyFailureDoesNotSuppressTheCommittedLedgerRefreshOrReplayIt() = runTest {
        val root = temp.newFolder()
        val files = CoreBackupFiles(root)
        val source = files.stage { ByteArrayInputStream("synthetic backup".toByteArray()) }
        var callbacks = 0
        var failPlatform = true
        val access = object : CoreBackupAccess by BackupTestAccess() {
            override fun importCommitted() { callbacks++ }
        }
        val workflow = CoreBackupWorkflow(access, files, BackupOperationJournal(files), root) {
            if (failPlatform) throw IOException("Synthetic platform failure")
        }
        assertTrue(runCatching { workflow.restore(source, false, ImportMode.MERGE, null, false) }.isFailure)
        assertEquals(1, callbacks)
        val operation = workflow.recover().single()
        assertEquals(BackupPhase.CORE_COMMITTED, operation.phase)
        failPlatform = false
        workflow.finishPlatform(operation)
        assertEquals(1, callbacks)
    }

    @Test fun refreshSchedulingFailureCannotTurnCommittedImportIntoFailure() = runTest {
        val root = temp.newFolder()
        val files = CoreBackupFiles(root)
        val source = files.stage { ByteArrayInputStream("synthetic backup".toByteArray()) }
        val access = object : CoreBackupAccess by BackupTestAccess() {
            override fun importCommitted() { error("Synthetic scheduler failure") }
        }
        val workflow = CoreBackupWorkflow(access, files, BackupOperationJournal(files), root)
        assertEquals(BackupPhase.PLATFORM_APPLIED,
            workflow.restore(source, false, ImportMode.MERGE, null, false).phase)
    }
}
