package io.github.hootkit_dev.hootbook.core.attachments

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [28])
class CoreAttachmentStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    @Test fun importUsesExactlyCoreAttachmentDirectoryAndRelativeUniqueReferences() = runTest {
        val store = CoreAttachmentStore(context)
        val first = store.importStream(ByteArrayInputStream(byteArrayOf(1, 2, 3)), "png")
        val second = store.importStream(ByteArrayInputStream(byteArrayOf(4)), "png")
        assertNotEquals(first, second)
        assertFalse(File(first).isAbsolute)
        assertEquals(File(context.noBackupFilesDir, "hootkit-core/attachments").canonicalFile, store.resolve(first)!!.parentFile)
        assertArrayEquals(byteArrayOf(1, 2, 3), store.resolve(first)!!.readBytes())
        store.discardDraft(listOf(first, second))
    }
    @Test fun retainingBeforeCoreWritePermanentlyPreventsDraftDeletionEvenAfterUncertainFailure() = runTest {
        val store = CoreAttachmentStore(context)
        val ref = store.importStream(ByteArrayInputStream(byteArrayOf(1)))
        store.retainForCore(listOf(ref))
        store.discardDraft(listOf(ref))
        assertNotNull(store.resolve(ref))
    }
    @Test fun cleanupNeverDeletesExistingCoreFilesOrAnotherStoreDrafts() = runTest {
        val first = CoreAttachmentStore(context)
        val ref = first.importStream(ByteArrayInputStream(byteArrayOf(1)))
        val other = CoreAttachmentStore(context)
        other.discardDraft(listOf(ref))
        assertNotNull(first.resolve(ref))
        first.discardDraft(listOf(ref)); assertNull(first.resolve(ref))
    }
    @Test fun traversalAndAbsolutePathsNeverResolve() {
        val store = CoreAttachmentStore(context)
        listOf("../outside", "/etc/hosts", "a/../b", "a//b", "a\\b", "\u0000", "").forEach { assertNull(store.resolve(it)) }
    }
    @Test fun symlinkOutsideCoreRootNeverResolvesOrDeletesTarget() = runTest {
        val root = File(context.noBackupFilesDir, "hootkit-core/attachments").apply { mkdirs() }
        val outside = File(context.cacheDir, "outside-fixture.txt").apply { writeText("preserve") }
        val link = File(root, "symlink-fixture")
        if (!Files.exists(link.toPath())) Files.createSymbolicLink(link.toPath(), outside.toPath())
        val store = CoreAttachmentStore(context)
        assertNull(store.resolve(link.name)); store.discardDraft(listOf(link.name))
        assertEquals("preserve", outside.readText())
    }
    @Test fun failedStreamCopyClosesInputAndRemovesPartialFiles() = runTest {
        var closed = false
        val root = File(context.noBackupFilesDir, "hootkit-core/attachments").apply { mkdirs() }
        val before = root.listFiles().orEmpty().map { it.name }.toSet()
        val input = object : InputStream() {
            override fun read(): Int = throw java.io.IOException("fixture")
            override fun close() { closed = true }
        }
        try { CoreAttachmentStore(context).importStream(input); fail("expected read error") } catch (_: java.io.IOException) {}
        assertTrue(closed)
        assertEquals(before, root.listFiles().orEmpty().map { it.name }.toSet())
    }
    @Test fun sharingCreatesCacheCopyWithoutExposingCorePrivateRoot() = runTest {
        val store = CoreAttachmentStore(context)
        val ref = store.importStream(ByteArrayInputStream(byteArrayOf(5, 6)), "pdf")
        val shared = store.copyForSharing(ref)
        assertEquals(File(context.cacheDir, "core-attachment-share").canonicalFile, shared.parentFile.canonicalFile)
        assertArrayEquals(byteArrayOf(5, 6), shared.readBytes())
        assertNotEquals(store.resolve(ref)!!.canonicalFile, shared.canonicalFile)
        store.discardDraft(listOf(ref)); assertTrue(shared.isFile)
    }
}
