package io.github.hootkit_dev.hootbook.core.ai

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], manifest = Config.NONE)
class CoreAiInboxTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun copiedBytesAndMetadataSurviveNewInboxInstanceWithoutSourceModification() = runTest {
        val root = temp.newFolder(); val original = temp.newFile().apply { writeText("original CSV contents") }
        val inbox = CoreAiInbox(root); inbox.add("bank.csv", "text/csv", original.readBytes())
        val file = inbox.files.value.single(); assertEquals("bank.csv", file.name); assertArrayEquals(original.readBytes(), inbox.bytes(file.id))
        val reopened = CoreAiInbox(root); reopened.reload(); assertEquals(file, reopened.files.value.single())
        reopened.remove(file.id); assertTrue(reopened.files.value.isEmpty()); assertEquals("original CSV contents", original.readText())
    }
    @Test fun sharedTextIsPrivatePersistentRawUtf8TextNotParsedLedger() = runTest {
        val inbox = CoreAiInbox(temp.newFolder()); inbox.addSharedText("咖啡 £12")
        val file = inbox.files.value.single(); assertTrue(file.name.startsWith("shared-text-")); assertEquals("text/plain", file.mimeType)
        assertEquals("咖啡 £12", String(inbox.bytes(file.id), Charsets.UTF_8))
    }
    @Test fun removeAndStartOverDeleteOnlyInboxCopies() = runTest {
        val root = temp.newFolder(); val outside = temp.newFile().apply { writeText("keep original") }; val inbox = CoreAiInbox(root)
        inbox.add("../../${outside.name}", "text/plain", "copy".toByteArray()); inbox.add("second.txt", "text/plain", byteArrayOf(1, 2))
        inbox.remove(inbox.files.value.first().id); assertEquals(1, inbox.files.value.size)
        inbox.clear(); assertTrue(inbox.files.value.isEmpty()); assertEquals("keep original", outside.readText()); assertTrue(root.listFiles().orEmpty().isEmpty())
    }
    @Test fun tamperedBytesAreRejectedAndNotExposedAfterReopen() = runTest {
        val root = temp.newFolder(); val inbox = CoreAiInbox(root); inbox.add("file.csv", "text/csv", byteArrayOf(1, 2, 3))
        val id = inbox.files.value.single().id; File(root, "$id.data").writeBytes(byteArrayOf(7, 8, 9))
        try { inbox.bytes(id); fail("Expected integrity rejection") } catch (expected: IllegalStateException) { assertTrue(expected.message!!.contains("changed")) }
        val reopened = CoreAiInbox(root); reopened.reload(); assertTrue(reopened.files.value.isEmpty())
        assertTrue(File(root, "$id.data").exists()) // Keep uncertain/corrupt original copy, never upload it.
    }
    @Test fun traversalAndSymlinkReferencesNeverReadOrDeleteOutsideInbox() = runTest {
        val root = temp.newFolder(); val inbox = CoreAiInbox(root); inbox.add("name", "text/plain", byteArrayOf(1)); val id = inbox.files.value.single().id
        val outside = temp.newFile().apply { writeText("keep outside") }; val data = File(root, "$id.data"); assertTrue(data.delete())
        Files.createSymbolicLink(data.toPath(), outside.toPath())
        try { inbox.bytes(id); fail("Expected path rejection") } catch (_: IllegalStateException) { }
        try { inbox.remove("../../outside"); fail("Expected ID rejection") } catch (_: IllegalArgumentException) { }
        assertEquals("keep outside", outside.readText())
    }
    @Test fun exactTwentyMibAllowedButOneExtraByteRejectedBeforeWrite() = runTest {
        assertEquals(CoreAiInbox.MAX_FILE_BYTES, CoreAiInbox.readBounded(ByteArrayInputStream(ByteArray(CoreAiInbox.MAX_FILE_BYTES))).size)
        try { CoreAiInbox.readBounded(ByteArrayInputStream(ByteArray(CoreAiInbox.MAX_FILE_BYTES + 1))); fail("Expected size limit") }
        catch (expected: IllegalStateException) { assertTrue(expected.message!!.contains("20 MB")) }
    }
    @Test fun diskFailureOnSecondStagedFileExposesNoPartialBatchAfterReopen() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>(); val inbox = CoreAiInbox(context); inbox.clear()
        inbox.add("previous.txt", "text/plain", "previous".toByteArray()); val previous = inbox.files.value.single()
        val one=Uri.parse("content://ai-test/disk-one"); val two=Uri.parse("content://ai-test/disk-two")
        for (uri in listOf(one,two)) Shadows.shadowOf(context.contentResolver).registerInputStream(uri,ByteArrayInputStream("new".toByteArray()))
        inbox.onBatchFileWritten={index->if(index==1)error("disk failure")}
        try{inbox.copy(listOf(one,two));fail()}catch(_:IllegalStateException){}
        val reopened=CoreAiInbox(context);reopened.reload();assertEquals(listOf(previous),reopened.files.value)
    }
    @Test fun cancellationBeforeBatchCommitLeavesOtherInboxAndNoPublishedPartial() = runTest {
        val context=ApplicationProvider.getApplicationContext<Application>();val inbox=CoreAiInbox(context);inbox.clear()
        inbox.add("keep.txt","text/plain",byteArrayOf(1));val previous=inbox.files.value.single()
        val uri=Uri.parse("content://ai-test/cancel");Shadows.shadowOf(context.contentResolver).registerInputStream(uri,ByteArrayInputStream(byteArrayOf(2)))
        inbox.onBatchFileWritten={throw kotlinx.coroutines.CancellationException("cancel before commit")}
        try{inbox.copy(listOf(uri));fail()}catch(_:kotlinx.coroutines.CancellationException){}
        val reopened=CoreAiInbox(context);reopened.reload();assertEquals(listOf(previous),reopened.files.value)
    }
    @Test fun aggregateSizeOverflowRejectsWholeBatchRatherThanPublishingFirstDocument() = runTest {
        val context=ApplicationProvider.getApplicationContext<Application>();val inbox=CoreAiInbox(context);inbox.clear()
        val one=Uri.parse("content://ai-test/size-one");val two=Uri.parse("content://ai-test/size-two")
        Shadows.shadowOf(context.contentResolver).registerInputStream(one,ByteArrayInputStream(ByteArray(12*1024*1024)))
        Shadows.shadowOf(context.contentResolver).registerInputStream(two,ByteArrayInputStream(ByteArray(12*1024*1024)))
        try{inbox.copy(listOf(one,two));fail()}catch(_:IllegalStateException){}
        val reopened=CoreAiInbox(context);reopened.reload();assertTrue(reopened.files.value.isEmpty())
    }
    @Test fun failedUriBatchDoesNotInstallFirstFileOrDeleteSource() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>(); val inbox = CoreAiInbox(context); inbox.clear()
        val first = Uri.parse("content://ai-test/one.txt"); val missing = Uri.parse("content://ai-test/missing.txt")
        Shadows.shadowOf(context.contentResolver).registerInputStream(first, ByteArrayInputStream("private".toByteArray()))
        try { inbox.copy(listOf(first, missing)); fail("Expected open failure") } catch (_: Exception) { }
        assertTrue(inbox.files.value.isEmpty())
    }
}
