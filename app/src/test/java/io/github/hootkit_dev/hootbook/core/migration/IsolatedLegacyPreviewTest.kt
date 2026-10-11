package io.github.hootkit_dev.hootbook.core.migration

import dev.hootkit.core.ChangeOrigin
import dev.hootkit.core.ChangeSet
import dev.hootkit.core.CoreConfig
import dev.hootkit.core.HttpMethod
import dev.hootkit.core.HttpRequest
import dev.hootkit.core.LegacyCount
import dev.hootkit.core.LegacyImportSummary
import dev.hootkit.core.LegacyOptions
import dev.hootkit.core.LogLevel
import dev.hootkit.core.PlatformException
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.assertFailsWith
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class IsolatedLegacyPreviewTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun usesOnlyNewSandboxAndSnapshotsAllSummaryCollectionsBeforeClosing() = runTest {
        val parent = temporary.newFolder()
        val source = File(parent, "source.backup").apply { writeText("source remains untouched") }
        val unrelated = File(parent, "production.sqlite").apply { writeText("production remains untouched") }
        val imported = mutableListOf(LegacyCount("accounts", 3u))
        val dropped = mutableListOf(LegacyCount("transactions", 2u))
        val platformFiles = mutableListOf("avatars/contact.png")
        val currencies = linkedMapOf("CUSTOM" to "X-CUSTOM")
        val nativeSummary = LegacyImportSummary(imported, dropped, "CNY", "{}", platformFiles, currencies)
        val events = mutableListOf<String>()
        lateinit var root: File
        lateinit var callbacks: WeakReference<LegacyPreviewCallbacks>
        val preview = IsolatedLegacyPreview(LegacyPreviewCoreOpener { config, adapters ->
            root = File(config.databasePath).parentFile!!.parentFile!!
            callbacks = WeakReference(adapters)
            assertEquals(parent.canonicalFile, root.parentFile)
            assertEquals("zh-CN", config.locale)
            assertNull(config.fixedNow)
            assertTrue(File(config.databasePath).parentFile!!.isDirectory)
            assertFalse(File(config.databasePath).exists())
            assertEquals(File(root, "backups"), File(config.backupDir))
            assertEquals(File(root, "attachments"), File(config.attachmentsDir))
            assertTrue(File(config.backupDir).isDirectory)
            assertTrue(File(config.attachmentsDir).isDirectory)
            adapters.secureStore.set("preview-key", byteArrayOf(7))
            FakeCore(
                onImport = { input, options ->
                    events += "import"
                    assertEquals(source.canonicalPath, input)
                    assertNull(options.password)
                    assertEquals(File(root, "platform").path, options.platformDir)
                    assertTrue(File(options.platformDir!!).isDirectory)
                    callbacks.get()!!.changes.onChange(ChangeSet(emptyList(), emptyList(), ChangeOrigin.IMPORT))
                    callbacks.get()!!.logger.log(LogLevel.INFO, "preview", "must not be logged")
                    assertArrayEquals(byteArrayOf(7), callbacks.get()!!.secureStore.get("preview-key"))
                    nativeSummary
                },
                onShutdown = {
                    events += "shutdown"
                    assertTrue(root.exists())
                    assertArrayEquals(byteArrayOf(7), callbacks.get()!!.secureStore.get("preview-key"))
                },
                onClose = {
                    events += "close"
                    assertTrue(root.exists())
                    assertArrayEquals(byteArrayOf(7), callbacks.get()!!.secureStore.get("preview-key"))
                    imported.clear(); dropped.clear(); platformFiles.clear(); currencies.clear()
                },
            )
        }, StandardTestDispatcher(testScheduler))

        val result = preview.preview(source, parent, "zh-CN")
        assertEquals(listOf("import", "shutdown", "close"), events)
        assertEquals(listOf(LegacyCount("accounts", 3u)), result.imported)
        assertEquals(listOf(LegacyCount("transactions", 2u)), result.dropped)
        assertEquals(listOf("avatars/contact.png"), result.platformFiles)
        assertEquals(mapOf("CUSTOM" to "X-CUSTOM"), result.renamedCurrencies)
        assertEquals("CNY", result.mainCurrency)
        assertEquals("{}", result.preferencesJson)
        assertFailsWith<UnsupportedOperationException> { (result.imported as MutableList<*>).clear() }
        assertFailsWith<UnsupportedOperationException> { (result.dropped as MutableList<*>).clear() }
        assertFailsWith<UnsupportedOperationException> { (result.platformFiles as MutableList<*>).clear() }
        assertFailsWith<UnsupportedOperationException> { (result.renamedCurrencies as MutableMap<*, *>).clear() }
        assertNull(callbacks.get()?.secureStore?.get("preview-key"))
        assertFalse(root.exists())
        assertEquals("source remains untouched", source.readText())
        assertEquals("production remains untouched", unrelated.readText())
    }

    @Test fun importFailureStillShutsDownClosesAndDeletesOnlyItsSandbox() = runTest {
        val parent = temporary.newFolder()
        val source = File(parent, "input").apply { writeText("original") }
        val events = mutableListOf<String>()
        val failure = IOException("invalid test backup")
        val preview = IsolatedLegacyPreview(LegacyPreviewCoreOpener { _, _ ->
            FakeCore(
                onImport = { _, _ -> throw failure },
                onShutdown = { events += "shutdown" },
                onClose = { events += "close" },
            )
        }, StandardTestDispatcher(testScheduler))

        val thrown = assertFailsWith<IOException> { preview.preview(source, parent, "en") }
        assertFailurePreserved(failure, thrown)
        assertEquals(listOf("shutdown", "close"), events)
        assertEquals(listOf(source), parent.listFiles()!!.toList())
        assertEquals("original", source.readText())
    }

    @Test fun openingFailureClearsItsEphemeralKeysAndRemovesSandbox() = runTest {
        val parent = temporary.newFolder()
        val source = File(parent, "input").apply { writeText("original") }
        lateinit var callbacks: LegacyPreviewCallbacks
        val preview = IsolatedLegacyPreview(LegacyPreviewCoreOpener { _, adapters ->
            callbacks = adapters
            adapters.secureStore.set("key", byteArrayOf(1))
            throw IOException("failed opening")
        }, StandardTestDispatcher(testScheduler))

        assertFailsWith<IOException> { preview.preview(source, parent, "en") }
        assertNull(callbacks.secureStore.get("key"))
        assertEquals(listOf(source), parent.listFiles()!!.toList())
    }

    @Test fun cancellationDuringOpeningWaitsForHandleThenClosesWithoutImporting() = runTest {
        val parent = temporary.newFolder()
        val source = File(parent, "input").apply { writeText("original") }
        val opened = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        lateinit var root: File
        val preview = IsolatedLegacyPreview(LegacyPreviewCoreOpener { config, _ ->
            root = sandbox(config)
            events += "opening"
            opened.await()
            FakeCore(
                onImport = { _, _ -> error("cancelled preview must not begin importing") },
                onShutdown = { events += "shutdown" },
                onClose = { events += "close" },
            )
        }, StandardTestDispatcher(testScheduler))
        val job = launch { preview.preview(source, parent, "en") }
        runCurrent()
        job.cancel()
        runCurrent()
        assertFalse(job.isCompleted)
        assertTrue(root.isDirectory)
        opened.complete(Unit)
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(listOf("opening", "shutdown", "close"), events)
        assertFalse(root.exists())
        assertTrue(source.isFile)
    }

    @Test fun cancellationDuringImportWaitsForImportAndShutdownBeforeDeleting() = runTest {
        val parent = temporary.newFolder()
        val source = File(parent, "input").apply { writeText("original") }
        val imported = CompletableDeferred<Unit>()
        val shutDown = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        lateinit var root: File
        lateinit var callbacks: WeakReference<LegacyPreviewCallbacks>
        val preview = IsolatedLegacyPreview(LegacyPreviewCoreOpener { config, adapters ->
            root = sandbox(config)
            callbacks = WeakReference(adapters)
            adapters.secureStore.set("key", byteArrayOf(9))
            FakeCore(
                onImport = { _, _ -> events += "import"; imported.await(); emptySummary() },
                onShutdown = {
                    events += "shutdown"
                    assertTrue(root.isDirectory)
                    assertArrayEquals(byteArrayOf(9), callbacks.get()!!.secureStore.get("key"))
                    shutDown.await()
                },
                onClose = {
                    events += "close"
                    assertTrue(root.isDirectory)
                    assertArrayEquals(byteArrayOf(9), callbacks.get()!!.secureStore.get("key"))
                },
            )
        }, StandardTestDispatcher(testScheduler))
        val job = launch { preview.preview(source, parent, "en") }
        runCurrent()
        job.cancel()
        runCurrent()
        assertFalse(job.isCompleted)
        assertEquals(listOf("import"), events)
        assertTrue(root.isDirectory)
        imported.complete(Unit)
        runCurrent()
        assertEquals(listOf("import", "shutdown"), events)
        assertFalse(job.isCompleted)
        assertTrue(root.isDirectory)
        shutDown.complete(Unit)
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(listOf("import", "shutdown", "close"), events)
        assertFalse(root.exists())
        assertNull(callbacks.get()?.secureStore?.get("key"))
        assertEquals("original", source.readText())
    }

    @Test fun shutdownFailureStillClosesButDoesNotDeletePossiblyLiveNativeFiles() = runTest {
        val parent = temporary.newFolder()
        val source = File(parent, "input").apply { writeText("original") }
        lateinit var root: File
        var closed = false
        val importFailure = IOException("failed import")
        val shutdownFailure = IOException("failed shutdown")
        val preview = IsolatedLegacyPreview(LegacyPreviewCoreOpener { config, _ ->
            root = sandbox(config)
            FakeCore(
                onImport = { _, _ -> throw importFailure },
                onShutdown = { throw shutdownFailure },
                onClose = { closed = true },
            )
        }, StandardTestDispatcher(testScheduler))

        val thrown = assertFailsWith<IOException> { preview.preview(source, parent, "en") }
        assertFailurePreserved(importFailure, thrown)
        // Coroutine stack-trace recovery may wrap a copy around the original failure. Verify
        // that teardown is still suppressed beneath the import error, wherever that copy lives.
        val suppressed = causeChain(thrown).flatMap { it.suppressed.toList() }
        assertEquals(setOf(shutdownFailure.javaClass to shutdownFailure.message),
            suppressed.map { it.javaClass to it.message }.toSet())
        assertTrue(closed)
        assertTrue(root.isDirectory)
        assertEquals("original", source.readText())
    }

    @Test fun closeFailurePropagatesAndRetainsSandbox() = runTest {
        val parent = temporary.newFolder()
        val source = File(parent, "input").apply { writeText("original") }
        lateinit var root: File
        val failure = IOException("failed close")
        val preview = IsolatedLegacyPreview(LegacyPreviewCoreOpener { config, _ ->
            root = sandbox(config)
            FakeCore(onClose = { throw failure })
        }, StandardTestDispatcher(testScheduler))

        val thrown = assertFailsWith<IOException> { preview.preview(source, parent, "en") }
        assertFailurePreserved(failure, thrown)
        assertTrue(root.isDirectory)
        assertTrue(source.isFile)
    }

    @Test fun eachPreviewHasFreshStorageAndCallbacksAndCleanupNeverFollowsSymlinks() = runTest {
        val parent = temporary.newFolder()
        val source = File(parent, "input").apply { writeText("original") }
        val outside = temporary.newFolder()
        val protectedFile = File(outside, "keep").apply { writeText("keep me") }
        val roots = mutableListOf<File>()
        val bundles = mutableListOf<LegacyPreviewCallbacks>()
        val preview = IsolatedLegacyPreview(LegacyPreviewCoreOpener { config, callbacks ->
            val root = sandbox(config)
            roots += root
            bundles += callbacks
            assertNull(callbacks.secureStore.get("key"))
            callbacks.secureStore.set("key", byteArrayOf(1))
            Files.createSymbolicLink(File(root, "external-link").toPath(), outside.toPath())
            FakeCore()
        }, StandardTestDispatcher(testScheduler))

        repeat(2) { preview.preview(source, parent, "en") }
        assertNotEquals(roots[0], roots[1])
        assertNotSame(bundles[0], bundles[1])
        assertTrue(roots.none(File::exists))
        assertTrue(bundles.all { it.secureStore.get("key") == null })
        assertEquals("keep me", protectedFile.readText())
        assertEquals("original", source.readText())
    }

    @Test fun networkTransportRejectsEveryRequestWithoutDisclosingItsContents() = runTest {
        val callbacks = LegacyPreviewCallbacks()
        for (method in HttpMethod.entries) {
            val failure = assertFailsWith<PlatformException.Network> {
                callbacks.http.send(HttpRequest(
                    method, "https://example.invalid/private-secret", emptyList(),
                    "secret body".toByteArray(), 1000u,
                ))
            }
            assertEquals("Network access is disabled during legacy preview", failure.detail)
            assertFalse(failure.message.contains("private-secret"))
            assertFalse(failure.message.contains("secret body"))
        }
    }

    @Test fun memorySecureStoreCopiesValuesAndNeverSharesKeysBetweenPreviews() {
        val first = LegacyPreviewCallbacks().secureStore
        val second = LegacyPreviewCallbacks().secureStore
        val input = byteArrayOf(1, 2, 3)
        first.set("key", input)
        input.fill(0)
        assertArrayEquals(byteArrayOf(1, 2, 3), first.get("key"))
        first.get("key")!!.fill(9)
        assertArrayEquals(byteArrayOf(1, 2, 3), first.get("key"))
        assertNull(second.get("key"))
        first.set("key", byteArrayOf(4))
        assertArrayEquals(byteArrayOf(4), first.get("key"))
        first.remove("key")
        assertNull(first.get("key"))
        first.set("other", byteArrayOf(5))
        first.clear()
        assertNull(first.get("other"))
    }

    @Test fun missingSourceOrParentDoesNotOpenCoreOrCreateAnything() = runTest {
        val parent = temporary.newFolder()
        val preview = IsolatedLegacyPreview(LegacyPreviewCoreOpener { _, _ ->
            error("invalid input must not open Core")
        }, StandardTestDispatcher(testScheduler))
        assertFailsWith<IllegalArgumentException> { preview.preview(File(parent, "absent"), parent, "en") }
        assertTrue(parent.listFiles()!!.isEmpty())
        val source = File(parent, "input").apply { writeText("original") }
        assertFailsWith<IllegalArgumentException> { preview.preview(source, File(parent, "missing"), "en") }
        assertEquals(listOf(source), parent.listFiles()!!.toList())
    }

    @Test fun reportsOnlyActualAiFlagsAfterImportAndBeforeShutdown() = runTest {
        val parent = temporary.newFolder()
        val source = File(parent, "input").apply { writeText("original") }
        val events = mutableListOf<String>()
        val preview = IsolatedLegacyPreview(LegacyPreviewCoreOpener { _, _ ->
            FakeCore(
                onImport = { _, _ -> events += "import"; emptySummary() },
                onAiStatus = { events += "status"; true to false },
                onShutdown = { events += "shutdown" },
                onClose = { events += "close" },
            )
        }, StandardTestDispatcher(testScheduler))
        val statuses = mutableListOf<Pair<Boolean, Boolean>>()
        preview.preview(source, parent, "en") { configured, hasKey ->
            events += "callback"
            statuses += configured to hasKey
        }
        assertEquals(listOf(true to false), statuses)
        assertEquals(listOf("import", "status", "callback", "shutdown", "close"), events)
        assertEquals(listOf(source), parent.listFiles()!!.toList())
    }

    @Test fun aiStatusCallbackFailureStillShutsDownClosesAndCleansSandbox() = runTest {
        val parent = temporary.newFolder()
        val source = File(parent, "input").apply { writeText("original") }
        val events = mutableListOf<String>()
        val callbackFailure = IOException("synthetic status consumer failed")
        val preview = IsolatedLegacyPreview(LegacyPreviewCoreOpener { _, _ ->
            FakeCore(
                onAiStatus = { true to true },
                onShutdown = { events += "shutdown" },
                onClose = { events += "close" },
            )
        }, StandardTestDispatcher(testScheduler))
        val thrown = assertFailsWith<IOException> {
            preview.preview(source, parent, "en") { _, _ -> throw callbackFailure }
        }
        assertFailurePreserved(callbackFailure, thrown)
        assertEquals(listOf("shutdown", "close"), events)
        assertEquals(listOf(source), parent.listFiles()!!.toList())
        assertEquals("original", source.readText())
    }

    private fun sandbox(config: CoreConfig): File = File(config.databasePath).parentFile!!.parentFile!!

    private fun assertFailurePreserved(expected: Throwable, actual: Throwable) {
        // withContext can recover stack traces by copying exceptions; object identity is not
        // part of this API's failure contract. Type and the original message must survive.
        assertEquals(expected.javaClass, actual.javaClass)
        assertEquals(expected.message, actual.message)
    }

    private fun causeChain(error: Throwable): List<Throwable> {
        val chain = mutableListOf<Throwable>()
        var next: Throwable? = error
        while (next != null) {
            val current = next
            if (chain.any { it === current }) break
            chain += current
            next = current.cause
        }
        return chain
    }

    private class FakeCore(
        private val onImport: suspend (String, LegacyOptions) -> LegacyImportSummary = { _, _ -> emptySummary() },
        private val onAiStatus: suspend () -> Pair<Boolean, Boolean>? = { null },
        private val onShutdown: suspend () -> Unit = {},
        private val onClose: () -> Unit = {},
    ) : LegacyPreviewCore {
        override suspend fun importBackup(path: String, options: LegacyOptions) = onImport(path, options)
        override suspend fun aiStatus() = onAiStatus()
        override suspend fun shutdown() = onShutdown.invoke()
        override fun close() = onClose.invoke()
    }

    companion object {
        private fun emptySummary() = LegacyImportSummary(emptyList(), emptyList(), "CNY", null, emptyList(), emptyMap())
    }
}
