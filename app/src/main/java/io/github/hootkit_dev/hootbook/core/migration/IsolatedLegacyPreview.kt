package io.github.hootkit_dev.hootbook.core.migration

import dev.hootkit.core.ChangeListener
import dev.hootkit.core.ChangeSet
import dev.hootkit.core.CoreConfig
import dev.hootkit.core.HttpRequest
import dev.hootkit.core.HttpResponse
import dev.hootkit.core.HttpTransport
import dev.hootkit.core.LegacyImportSummary
import dev.hootkit.core.LegacyOptions
import dev.hootkit.core.LogLevel
import dev.hootkit.core.Logger
import dev.hootkit.core.PlatformException
import dev.hootkit.core.SecureStore
import dev.hootkit.core.openCore
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.attribute.BasicFileAttributes
import java.util.ArrayDeque
import java.util.Collections
import java.util.IdentityHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Obtains the frozen Core's actual import result without opening any production services.
 * [temporaryRoot] is an existing, caller-owned private directory. Each call owns only a new
 * child of that directory; neither the parent nor the input backup is ever deleted.
 */
class IsolatedLegacyPreview internal constructor(
    private val opener: LegacyPreviewCoreOpener,
    private val dispatcher: CoroutineDispatcher,
) {
    constructor() : this(NativeLegacyPreviewCoreOpener, Dispatchers.IO)

    suspend fun preview(
        path: File,
        temporaryRoot: File,
        locale: String,
        onAiStatus: (configured: Boolean, hasKey: Boolean) -> Unit = { _, _ -> },
    ): LegacyImportSummary =
        withContext(dispatcher) {
            val caller = currentCoroutineContext()
            caller.ensureActive()
            val source = path.canonicalFile
            require(source.isFile) { "Legacy preview requires an existing backup" }
            val parent = temporaryRoot.canonicalFile
            require(parent.isDirectory) { "Legacy preview requires a private temporary directory" }
            val root = Files.createTempDirectory(parent.toPath(), "legacy-preview-")
            val callbacks = LegacyPreviewCallbacks()
            var core: LegacyPreviewCore? = null
            var primaryFailure: Throwable? = null
            val summary = try {
                val database = Files.createDirectory(root.resolve("database")).resolve("preview.sqlite")
                val backups = Files.createDirectory(root.resolve("backups"))
                val attachments = Files.createDirectory(root.resolve("attachments"))
                val platform = Files.createDirectory(root.resolve("platform"))
                val config = CoreConfig(
                    database.toString(), backups.toString(), attachments.toString(), locale, null,
                )
                // Cancelling a UniFFI future does not necessarily stop its native write. Await
                // open/import completely so neither a returned handle nor an in-flight import
                // can outlive these directories. Cancellation still propagates after cleanup.
                withContext(NonCancellable) {
                    val opened = opener.open(config, callbacks)
                    core = opened
                    caller.ensureActive()
                    val result = opened.importBackup(
                        source.path, LegacyOptions(password = null, platformDir = platform.toString()),
                    ).immutableSnapshot()
                    caller.ensureActive()
                    // Read only safe status flags; never return the restored key or AI settings.
                    opened.aiStatus()?.let { (configured, hasKey) -> onAiStatus(configured, hasKey) }
                    caller.ensureActive()
                    result
                }
            } catch (failure: Throwable) {
                primaryFailure = failure
                throw failure
            } finally {
                withContext(NonCancellable) {
                    var cleanupFailure: Throwable? = null
                    fun record(failure: Throwable) {
                        val previous = cleanupFailure
                        if (previous == null) cleanupFailure = failure
                        else previous.addCleanupFailure(failure)
                    }
                    var shutdownFinished = core == null
                    var closeFinished = core == null
                    try {
                        core?.shutdown()
                        shutdownFinished = true
                    } catch (failure: Throwable) {
                        record(failure)
                    }
                    try {
                        core?.close()
                        closeFinished = true
                    } catch (failure: Throwable) {
                        record(failure)
                    }
                    // Keep the whole callback bundle strongly reachable through shutdown and
                    // close. If either fails, leave the private files alone rather than race
                    // an uncertain native lifetime. Import/open failures still clean up here.
                    if (shutdownFinished && closeFinished) {
                        callbacks.clear()
                        try {
                            deletePreviewDirectory(root)
                        } catch (failure: Throwable) {
                            record(failure)
                        }
                    }
                    cleanupFailure?.let { failure ->
                        val original = primaryFailure
                        if (original == null) throw failure
                        original.addCleanupFailure(failure)
                    }
                }
            }
            caller.ensureActive()
            summary
        }
}

/** An injectable ownership boundary; host tests do not instantiate a UniFFI handle. */
internal fun interface LegacyPreviewCoreOpener {
    suspend fun open(config: CoreConfig, callbacks: LegacyPreviewCallbacks): LegacyPreviewCore
}

internal interface LegacyPreviewCore : AutoCloseable {
    suspend fun importBackup(path: String, options: LegacyOptions): LegacyImportSummary
    suspend fun aiStatus(): Pair<Boolean, Boolean>? = null
    suspend fun shutdown()
    override fun close()
}

private object NativeLegacyPreviewCoreOpener : LegacyPreviewCoreOpener {
    override suspend fun open(config: CoreConfig, callbacks: LegacyPreviewCallbacks): LegacyPreviewCore {
        val core = openCore(
            config, callbacks.changes, callbacks.logger, callbacks.secureStore, callbacks.http,
            auth = null, remote = null,
        )
        return object : LegacyPreviewCore {
            override suspend fun importBackup(path: String, options: LegacyOptions): LegacyImportSummary =
                core.legacy().use { legacy -> legacy.importBackup(path, options) }

            override suspend fun aiStatus(): Pair<Boolean, Boolean> =
                core.ai().use { ai -> ai.settings().let { it.configured to it.hasKey } }

            override suspend fun shutdown() = core.shutdown()
            override fun close() = core.close()
        }
    }
}

internal class LegacyPreviewCallbacks {
    val changes: ChangeListener = object : ChangeListener {
        override fun onChange(change: ChangeSet) = Unit
    }
    val logger: Logger = object : Logger {
        override fun log(level: LogLevel, target: String, message: String) = Unit
    }
    val secureStore = LegacyPreviewSecureStore()
    val http: HttpTransport = object : HttpTransport {
        override suspend fun send(request: HttpRequest): HttpResponse =
            throw PlatformException.Network("Network access is disabled during legacy preview")
    }

    fun clear() = secureStore.clear()
}

/** No Keystore, disk, production keys, or caller-owned byte arrays are used. */
internal class LegacyPreviewSecureStore : SecureStore {
    private val values = mutableMapOf<String, ByteArray>()

    @Synchronized
    override fun get(key: String): ByteArray? = values[key]?.copyOf()

    @Synchronized
    override fun set(key: String, value: ByteArray) {
        val copy = value.copyOf()
        values.put(key, copy)?.fill(0)
    }

    @Synchronized
    override fun remove(key: String) {
        values.remove(key)?.fill(0)
    }

    @Synchronized
    fun clear() {
        values.values.forEach { it.fill(0) }
        values.clear()
    }
}

private fun LegacyImportSummary.immutableSnapshot(): LegacyImportSummary = copy(
    imported = Collections.unmodifiableList(ArrayList(imported)),
    dropped = Collections.unmodifiableList(ArrayList(dropped)),
    platformFiles = Collections.unmodifiableList(ArrayList(platformFiles)),
    renamedCurrencies = Collections.unmodifiableMap(LinkedHashMap(renamedCurrencies)),
)

/** Stack-trace recovery can discard a caught exception copy on the next coroutine boundary. */
private fun Throwable.addCleanupFailure(failure: Throwable) {
    val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    var next: Throwable? = this
    while (next != null) {
        val current = next
        if (!visited.add(current)) break
        // Preserve the diagnostic on the underlying original as well as any recovered copy.
        // Never add a self-reference, duplicate edge, or a cycle through causes/suppressed errors.
        if (!failure.references(current) && current.suppressed.none { it === failure }) {
            current.addSuppressed(failure)
        }
        next = current.cause
    }
}

private fun Throwable.references(target: Throwable): Boolean {
    val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    val pending = ArrayDeque<Throwable>()
    pending.addLast(this)
    while (pending.isNotEmpty()) {
        val current = pending.removeLast()
        if (current === target) return true
        if (!visited.add(current)) continue
        current.cause?.let(pending::addLast)
        current.suppressed.forEach(pending::addLast)
    }
    return false
}

private fun deletePreviewDirectory(root: Path) {
    // Do not follow symlinks, even if an imported payload were to create one in its sandbox.
    Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            Files.delete(file)
            return FileVisitResult.CONTINUE
        }

        override fun postVisitDirectory(directory: Path, failure: IOException?): FileVisitResult {
            if (failure != null) throw failure
            Files.delete(directory)
            return FileVisitResult.CONTINUE
        }
    })
}
