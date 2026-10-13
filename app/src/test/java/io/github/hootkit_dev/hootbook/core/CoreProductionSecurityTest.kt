package io.github.hootkit_dev.hootbook.core

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import io.github.hootkit_dev.hootbook.core.ui.*
import io.github.hootkit_dev.hootbook.core.ai.CoreAiInbox
import io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentStore
import io.github.hootkit_dev.hootbook.widget.QuickEntry
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],manifest=Config.NONE)
class CoreProductionSecurityTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    @Test fun overlappingSecretDestinationsNeverClearRemainingLeaseAndReleaseIsIdempotent() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get(); val window=activity.window
        val first=CoreSecureWindows.acquire(window); val second=CoreSecureWindows.acquire(window)
        first(); first(); assertTrue(window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        second(); assertEquals(0,window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
    }
    @Test fun originalExternalSecureFlagAndUnrelatedBitsSurviveAllLeases() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get(); val window=activity.window
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val first=CoreSecureWindows.acquire(window);val lock=CoreSecureWindows.acquire(window);first();lock()
        assertTrue(window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        assertTrue(window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
    }
    @Test fun fileSelfAuthorityEncodedSelfUserInfoAndUnknownSchemesAreRejected() {
        val pkg=context.packageName
        for(uri in listOf("file:///data/private/key", "https://host/image", "content://$pkg.fileprovider/core_backup_share/key", "content://user@$pkg.fileprovider/key", "content://$pkg%2efileprovider/key", "content://external:42/item"))
            assertThrows(IllegalArgumentException::class.java){CoreUriBoundary.requireExternalDocument(context,Uri.parse(uri))}
        assertEquals("content",CoreUriBoundary.requireExternalDocument(context,Uri.parse("content://external-provider/document/1")).scheme)
    }
    @Test fun everyUserDocumentReaderRejectsPrivateUriBeforeOpeningBytes()=runTest {
        val privateUri=Uri.parse("content://${context.packageName}.fileprovider/core_backup_share/private")
        val inbox=CoreAiInbox(context);val attachment=CoreAttachmentStore(context);val assets=CorePlatformAssets(context)
        try{inbox.copy(listOf(privateUri));fail()}catch(_:IllegalArgumentException){}
        try{attachment.importFrom(privateUri);fail()}catch(_:IllegalArgumentException){}
        try{assets.importImage(privateUri);fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun quickEntriesAreTypedNavigationAndConsumeOnceAcrossRecreation()=runTest {
        File(context.noBackupFilesDir,"hootkit-core/intent-inbox").deleteRecursively()
        val app=CoroutineScope(SupervisorJob()+StandardTestDispatcher(testScheduler))
        try {
            val ingress=CoreIntentIngress(CoreAiInbox(context),app,context)
            ingress.accept(context,QuickEntry.intent(context,QuickEntry.Entry.Template("core/string id")))
            val token=ingress.pending.value.single().token
            val reopened=CoreIntentIngress(CoreAiInbox(context),app,context);assertEquals(token,reopened.pending.value.single().token)
            assertEquals(CoreIngressTarget.Quick(QuickEntry.Entry.Template("core/string id")),reopened.pending.value.single().target)
            reopened.consumed(token);assertTrue(CoreIntentIngress(CoreAiInbox(context),app,context).pending.value.isEmpty())
        }finally{app.cancel()}
    }
    @Test fun shareFilesCopyBeforeTypedAiNavigationAndStayInPrivateInbox()=runTest {
        File(context.noBackupFilesDir,"hootkit-core/intent-inbox").deleteRecursively();File(context.noBackupFilesDir,"hootkit-core/ai-inbox").deleteRecursively()
        val app=CoroutineScope(SupervisorJob()+StandardTestDispatcher(testScheduler));val uri=Uri.parse("content://external-provider/statement.csv")
        Shadows.shadowOf(context.contentResolver).registerInputStream(uri,ByteArrayInputStream("bank data".toByteArray()))
        try { val inbox=CoreAiInbox(context);val ingress=CoreIntentIngress(inbox,app,context)
            ingress.accept(context,Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM,uri))
            assertTrue(ingress.pending.value.isEmpty())
            ingress.pending.first { it.isNotEmpty() };assertEquals(CoreIngressTarget.Ai,ingress.pending.value.single().target);assertEquals(1,inbox.files.value.size)
        }finally{app.cancel()}
    }
    @Test fun nestedIntentsAndUnknownActionsNeverDispatchOrForward()=runTest {
        File(context.noBackupFilesDir,"hootkit-core/intent-inbox").deleteRecursively();val app=CoroutineScope(SupervisorJob()+StandardTestDispatcher(testScheduler))
        try {val ingress=CoreIntentIngress(CoreAiInbox(context),app,context)
            ingress.accept(context,Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"x").putExtra(Intent.EXTRA_INTENT,Intent("private-action")))
            ingress.accept(context,Intent("unknown-action").setData(Uri.parse("file:///private")));runCurrent()
            assertTrue(ingress.pending.value.isEmpty());assertTrue(ingress.failed.value)
        }finally{app.cancel()}
    }
    @Test fun pendingAtomicBackupRestoresAndFullQueueNeverOverflowsAfterColdRead()=runTest {
        File(context.noBackupFilesDir,"hootkit-core/intent-inbox").deleteRecursively();val app=CoroutineScope(SupervisorJob()+StandardTestDispatcher(testScheduler))
        try { val ingress=CoreIntentIngress(CoreAiInbox(context),app,context)
            repeat(100){ingress.accept(context,QuickEntry.intent(context,QuickEntry.Entry.Home))}
            ingress.accept(context,QuickEntry.intent(context,QuickEntry.Entry.Home));assertEquals(100,ingress.pending.value.size);assertTrue(ingress.failed.value)
            val file=File(context.noBackupFilesDir,"hootkit-core/intent-inbox/pending.json");file.copyTo(File(file.path+".bak"),true);assertTrue(file.delete())
            val reopened=CoreIntentIngress(CoreAiInbox(context),app,context);assertEquals(100,reopened.pending.value.size)
        }finally{app.cancel()}
    }
    @Test fun preservedLegacyStaticAddHasNoLongIdMappingOrHiddenMutation() {
        val entry=QuickEntry.parse(Intent(QuickEntry.ACTION_TRANSACTION).putExtra("io.github.hootkit_dev.hootbook.extra.TEMPLATE_ID",7L))
        assertEquals(QuickEntry.Entry.NewTransaction,entry)
    }
}
