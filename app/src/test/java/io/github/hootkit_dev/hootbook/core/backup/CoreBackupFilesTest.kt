package io.github.hootkit_dev.hootbook.core.backup

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
class CoreBackupFilesTest {
    @get:Rule val temp = TemporaryFolder()
    private fun files(limit: Long = 1024L) = CoreBackupFiles(temp.newFolder(), limit)
    @Test fun stagesRealBytesWithHashAndRepeatSaveKeepsSource() = runTest {
        val bridge = files(); val bytes = "backup-content".toByteArray()
        val staged = bridge.stage { ByteArrayInputStream(bytes) }
        assertArrayEquals(bytes, staged.file.readBytes()); assertEquals(bytes.size.toLong(), staged.size)
        assertEquals(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, staged.sha256)
        repeat(2) { val out = ByteArrayOutputStream(); bridge.save(staged) { out }; assertArrayEquals(bytes, out.toByteArray()) }
        assertTrue(staged.file.exists())
    }
    @Test fun oversizedInputDeletesOnlyStagedCopyAndClosesStream() = runTest {
        val bridge = files(3); var closed = false
        val stream = object : ByteArrayInputStream(ByteArray(4)) { override fun close() { closed = true; super.close() } }
        try { bridge.stage { stream }; fail() } catch (_: IllegalArgumentException) { }
        assertTrue(closed)
    }
    @Test fun emptyInputRejectedAndStreamClosed() = runTest {
        var closed = false
        try { files().stage { object : ByteArrayInputStream(byteArrayOf()) { override fun close() { closed = true } } }; fail() }
        catch (_: IllegalArgumentException) { }
        assertTrue(closed)
    }
    @Test fun partialReadFailureRemovesPrivateOutputNotOriginal() = runTest {
        val original = temp.newFile().apply { writeText("original") }; val root = temp.newFolder(); val bridge = CoreBackupFiles(root)
        try { bridge.stage { object : ByteArrayInputStream(original.readBytes()) { override fun read(b: ByteArray, off: Int, len: Int): Int { throw java.io.IOException() } } }; fail() }
        catch (_: java.io.IOException) { }
        assertEquals("original", original.readText()); assertTrue(root.walkTopDown().none { it.isFile })
    }
    @Test fun mutationRejectedBeforeOpeningDestination() = runTest {
        val bridge = files(); val staged = bridge.stage { ByteArrayInputStream("first".toByteArray()) }; staged.file.writeText("second")
        var opened = false
        try { bridge.save(staged) { opened = true; ByteArrayOutputStream() }; fail() } catch (_: IllegalArgumentException) { }
        assertFalse(opened)
    }
    @Test fun uriBoundaryAcceptsOnlyGrantedContentNotFileHttpOrCustom() = runTest {
        val resolver = ApplicationProvider.getApplicationContext<Application>().contentResolver
        for (uri in listOf("file:///etc/passwd", "https://host/file", "custom:backup")) {
            try { files().stage(resolver, Uri.parse(uri)); fail() } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun traversalAbsoluteBackslashAndEmptySegmentsRejected() {
        for (name in listOf("../outside", "/absolute", "a/../../outside", "a\\outside", "a//b", "./file", "")) {
            assertThrows(IllegalArgumentException::class.java) { CoreBackupFiles.safeChild(temp.root, name) }
        }
    }
    @Test fun symlinkEscapeRejected() {
        val root = temp.newFolder(); val other = temp.newFolder()
        Files.createSymbolicLink(File(root, "link").toPath(), other.toPath())
        assertThrows(IllegalArgumentException::class.java) { CoreBackupFiles.safeChild(root, "link/private") }
    }
    @Test fun unknownPlatformFilesRetainedAndExportedWithoutRenaming() {
        val bridge = files(); val id = bridge.newId()
        val file = File(bridge.platform(id), "unknown/icon.bin").apply { parentFile.mkdirs(); writeText("opaque") }
        val result = bridge.exportPlatformFiles(id, listOf("unknown/icon.bin"))
        assertEquals("platform/unknown/icon.bin", result.single().entryName); assertEquals(file.path, result.single().path)
        assertEquals("opaque", file.readText())
    }
    @Test fun missingPlatformAssetFailsRatherThanSilentlyDropping() {
        val bridge = files()
        assertThrows(IllegalArgumentException::class.java) { bridge.validatePlatformFiles(bridge.newId(), listOf("profile/missing.jpg")) }
    }
    @Test fun outputProviderFailurePreservesSource() = runTest {
        val bridge = files(); val staged = bridge.stage { ByteArrayInputStream("backup".toByteArray()) }
        try { bridge.save(staged) { throw java.io.IOException() }; fail() } catch (_: java.io.IOException) { }
        assertEquals("backup", staged.file.readText())
    }
}
