package io.github.hootkit_dev.hootbook.core.backup

import android.app.Application
import io.github.hootkit_dev.hootbook.presentation.data.backup.BackupWorkflow
import io.github.hootkit_dev.hootbook.presentation.data.backup.BackupTestAccess
import java.io.File
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],manifest=Config.NONE)
class CoreBackupWorkflowTest {
    @get:Rule val temp=TemporaryFolder()
    @Test fun explicitPlatformSkipRetainsCommittedLedgerAndSourceAndNeverReimports()=runTest {
        val root=temp.newFolder();val files=CoreBackupFiles(root);val journal=BackupOperationJournal(files);val access=BackupTestAccess()
        val id=UUID.randomUUID().toString();val source=File(files.operation(id),"source.backup").apply{writeText("original")}
        val staged=files.verify(id,source);val op=BackupOperation(staged,false,BackupPhase.CORE_COMMITTED);journal.write(op)
        val workflow=BackupWorkflow(access,files,journal,root);val result=workflow.skipPlatform(op)
        assertEquals(BackupPhase.PLATFORM_SKIPPED,result.phase);assertFalse(result.blocksImport);assertEquals("original",source.readText());assertEquals(0,access.imports)
        assertEquals(BackupPhase.PLATFORM_SKIPPED,workflow.recover().single().phase)
    }
    @Test fun uncertainImportIsNeverUnlockedBySkippingPlatform()=runTest {
        val root=temp.newFolder();val files=CoreBackupFiles(root);val id=UUID.randomUUID().toString();val source=File(files.operation(id),"source.backup").apply{writeText("original")}
        val op=BackupOperation(files.verify(id,source),false,BackupPhase.UNCERTAIN);val access=BackupTestAccess();val workflow=BackupWorkflow(access,files,BackupOperationJournal(files),root)
        try{workflow.skipPlatform(op);fail()}catch(_:IllegalStateException){}
        assertTrue(op.blocksImport);assertEquals(0,access.imports);assertEquals("original",source.readText())
    }
}
