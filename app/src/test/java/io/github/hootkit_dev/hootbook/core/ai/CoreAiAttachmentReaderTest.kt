package io.github.hootkit_dev.hootbook.core.ai

import android.app.Application
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import dev.hootkit.core.AiAttachmentKind
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], manifest = Config.NONE)
class CoreAiAttachmentReaderTest {
    @Test fun classificationOrderPreservesCsvMislabeledAsExcelAndRejectsBinaryDocuments() {
        assertEquals(AiAttachmentKind.TEXT, CoreAiAttachmentReader.kind("bank.CSV", "application/vnd.ms-excel"))
        assertEquals(AiAttachmentKind.PDF, CoreAiAttachmentReader.kind("statement.PDF", "application/octet-stream"))
        assertEquals(AiAttachmentKind.IMAGE, CoreAiAttachmentReader.kind("shot.png", "image/png"))
        assertEquals(AiAttachmentKind.TEXT, CoreAiAttachmentReader.kind("file", "application/octet-stream"))
        for (name in listOf("sheet.xlsx", "sheet.xls", "archive.zip", "pages.pages", "notes.docx")) {
            assertThrows(IllegalStateException::class.java) { CoreAiAttachmentReader.kind(name, "application/octet-stream") }
        }
    }
    @Test fun narrowScreenshotsUseExactTwoWidthTilesAndIntegerOverlap() {
        assertEquals(listOf(Rect(0, 0, 100, 200), Rect(0, 188, 100, 388), Rect(0, 376, 100, 400)), CoreAiAttachmentReader.tiles(100, 400))
    }
    @Test fun smallImageIsOneTileAndBoundaryOverlapIsNotAnExtraTile() {
        assertEquals(listOf(Rect(0, 0, 8, 4)), CoreAiAttachmentReader.tiles(8, 4))
        assertEquals(listOf(Rect(0, 0, 8, 16)), CoreAiAttachmentReader.tiles(8, 16))
    }
    @Test fun twelveTilesAllowedThirteenRejectedAndInvalidDimensionsRejected() {
        assertEquals(12, CoreAiAttachmentReader.tiles(8, 167).size)
        assertThrows(IllegalStateException::class.java) { CoreAiAttachmentReader.tiles(8, 182) }
        assertThrows(IllegalStateException::class.java) { CoreAiAttachmentReader.tiles(0, 10) }
        assertThrows(IllegalStateException::class.java) { CoreAiAttachmentReader.tiles(10, 0) }
    }
    @Test fun veryWideBoundsDoNotOverflowIntTileArithmetic() { assertEquals(1, CoreAiAttachmentReader.tiles(Int.MAX_VALUE, 100).size) }
    @Test fun textBytesArePassedRawForCoreUtf8Gb18030Decoding() = runTest {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(), 0x80.toByte())
        val file = CoreAiInboxFile("text-id", "bank.csv", "text/csv", bytes.size.toLong())
        val reader = CoreAiAttachmentReader(ApplicationProvider.getApplicationContext())
        val prepared = reader.prepare(listOf(file), Inbox(file, bytes), emptyMap()).single()
        assertEquals(AiAttachmentKind.TEXT, prepared.kind); assertArrayEquals(bytes, prepared.data); assertNull(prepared.pdfText)
    }
    @Test fun pdfIsReallyUnlockedWithPasswordAndOriginalEncryptedBytesRemainUntouched() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>(); PDFBoxResourceLoader.init(context)
        val original = PDDocument().use { doc -> doc.addPage(PDPage()); doc.protect(StandardProtectionPolicy("owner-secret", "user-secret", AccessPermission()).apply { encryptionKeyLength = 128 })
            ByteArrayOutputStream().also { doc.save(it) }.toByteArray() }
        val snapshot = original.copyOf(); val file = CoreAiInboxFile("pdf-id", "locked.pdf", "application/pdf", original.size.toLong())
        val reader = CoreAiAttachmentReader(context); val inbox = Inbox(file, original)
        try { reader.prepare(listOf(file), inbox, emptyMap()); fail("Expected password prompt") }
        catch (required: CoreAiPdfPasswordRequired) { assertEquals(file.id, required.fileId) }
        val prepared = reader.prepare(listOf(file), inbox, mapOf(file.id to "user-secret")).single()
        assertEquals(AiAttachmentKind.PDF, prepared.kind); assertNotNull(prepared.pdfText)
        PDDocument.load(prepared.data).use { assertFalse(it.isEncrypted); assertEquals(1, it.numberOfPages) }
        assertArrayEquals(snapshot, original)
    }
    @Test fun wholeBatchPreparationFailsOnUnsupportedFileRatherThanSendingPartialInput() = runTest {
        val good = CoreAiInboxFile("good", "good.txt", "text/plain", 3); val bad = CoreAiInboxFile("bad", "bad.xlsx", "application/vnd.ms-excel", 3)
        val inbox = object : CoreAiInboxPort {
            override val files = MutableStateFlow(listOf(good, bad)); override suspend fun bytes(id: String) = byteArrayOf(1, 2, 3)
            override suspend fun reload() = Unit; override suspend fun remove(id: String) = Unit; override suspend fun clear() = Unit
        }
        try { CoreAiAttachmentReader(ApplicationProvider.getApplicationContext()).prepare(listOf(good, bad), inbox, emptyMap()); fail("Expected format error") }
        catch (_: IllegalStateException) { }
    }
    @Test fun preparationRejectsAggregateBeforeReadingOrSendingAnyBytes() = runTest {
        var reads=0;val files=listOf(CoreAiInboxFile("a","a.txt","text/plain",12L*1024*1024),CoreAiInboxFile("b","b.txt","text/plain",12L*1024*1024))
        val inbox=object:CoreAiInboxPort {
            override val files=MutableStateFlow(files);override suspend fun bytes(id:String):ByteArray{reads++;return byteArrayOf(1)}
            override suspend fun reload()=Unit;override suspend fun remove(id:String)=Unit;override suspend fun clear()=Unit
        }
        try{CoreAiAttachmentReader(ApplicationProvider.getApplicationContext()).prepare(files,inbox,emptyMap());fail()}catch(_:IllegalArgumentException){}
        assertEquals(0,reads)
    }
    private class Inbox(file: CoreAiInboxFile, private val data: ByteArray) : CoreAiInboxPort {
        override val files = MutableStateFlow(listOf(file)); override suspend fun bytes(id: String) = data
        override suspend fun reload() = Unit; override suspend fun remove(id: String) = Unit; override suspend fun clear() = Unit
    }
}
