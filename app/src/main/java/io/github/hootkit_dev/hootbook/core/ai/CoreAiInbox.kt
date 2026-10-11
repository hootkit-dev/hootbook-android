package io.github.hootkit_dev.hootbook.core.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Private platform file reference, never a ledger attachment/ID. Bytes are not in SavedState. */
data class CoreAiInboxFile(val id: String, val name: String, val mimeType: String, val size: Long)
interface CoreAiInboxPort {
    val files: StateFlow<List<CoreAiInboxFile>>
    suspend fun reload()
    suspend fun remove(id: String)
    suspend fun clear()
    suspend fun bytes(id: String): ByteArray
}

/** Copies temporary URI grants before lock/navigation; survives process death in noBackup. */
@Singleton
class CoreAiInbox private constructor(private val root: File, private val context: Context?) : CoreAiInboxPort {
    @Inject constructor(@ApplicationContext context: Context) : this(
        File(context.noBackupFilesDir, "hootkit-core/ai-inbox"), context)
    internal constructor(root: File) : this(root, null)
    private val gate = Mutex()
    private val locations = mutableMapOf<String, File>()
    internal var onBatchFileWritten: ((Int) -> Unit)? = null // deterministic IO/cancellation fixture seam
    private val mutable = MutableStateFlow<List<CoreAiInboxFile>>(emptyList())
    override val files = mutable.asStateFlow()
    private fun directory(): File {
        check(root.mkdirs() || root.isDirectory) { "AI inbox storage unavailable" }
        return root
    }
    private fun path(id: String, extension: String): File {
        check(UUID.fromString(id).toString() == id) { "Invalid inbox reference" }
        val base = directory().canonicalFile
        val directory = locations[id] ?: base
        check(directory == base || directory.canonicalFile.parentFile == base) { "Invalid inbox batch" }
        return File(directory, "$id.$extension").also { check(it.canonicalFile.parentFile == directory) { "Invalid inbox path" } }
    }
    override suspend fun reload() = withContext(Dispatchers.IO) { gate.withLock { reloadLocked() } }
    private fun reloadLocked() {
        val base = directory().canonicalFile
        val directories = listOf(base) + base.listFiles().orEmpty().filter { it.isDirectory && it.name.startsWith("batch-") && it.canonicalFile.parentFile == base }
        locations.clear()
        val files = directories.flatMap { dir -> dir.listFiles().orEmpty().filter { it.extension == "json" }.sortedBy { it.name }.mapNotNull { meta ->
            try {
                val id = meta.nameWithoutExtension; require(UUID.fromString(id).toString() == id)
                locations[id] = dir.canonicalFile
                val json = JSONObject(path(id, "json").readText()); val data = path(id, "data")
                check(data.length() in 0..MAX_FILE_BYTES.toLong() && data.isFile && data.length() == json.getLong("size"))
                check(digest(data.readBytes()) == json.getString("sha256"))
                CoreAiInboxFile(id, json.getString("name"), json.getString("mime"), data.length())
            } catch (_: Exception) { null }
        } }
        mutable.value = files
    }
    suspend fun copy(uris: List<Uri>) = withContext(Dispatchers.IO) {
        val resolver = checkNotNull(context).contentResolver
        val base = directory().canonicalFile; val batch = UUID.randomUUID().toString()
        val temporary = File(base, ".pending-batch-$batch"); check(temporary.mkdir())
        var committed = false; var total = 0L
        try {
            uris.forEachIndexed { index, uri ->
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                io.github.hootkit_dev.hootbook.core.CoreUriBoundary.requireExternalDocument(checkNotNull(context), uri)
                val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                } ?: "file"
                val id = UUID.randomUUID().toString(); val out = File(temporary, "$id.data")
                val sha = MessageDigest.getInstance("SHA-256"); var size = 0L
                val input = resolver.openInputStream(uri) ?: error("File could not be opened")
                input.use { source -> out.outputStream().use { target -> val buffer = ByteArray(64 * 1024)
                    while (true) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val count = source.read(buffer); if (count < 0) break; if (count == 0) continue
                        size += count; total += count
                        check(size <= MAX_FILE_BYTES && total <= MAX_BATCH_BYTES) { "AI input batch exceeds 20 MB" }
                        sha.update(buffer, 0, count); target.write(buffer, 0, count)
                    }; target.fd.sync()
                } }
                atomic(File(temporary, "$id.json"), JSONObject().put("name", name.take(512))
                    .put("mime", (resolver.getType(uri) ?: "application/octet-stream").take(128)).put("size", size)
                    .put("sha256", sha.digest().joinToString("") { "%02x".format(it) }).toString().toByteArray())
                onBatchFileWritten?.invoke(index)
            }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            gate.withLock {
                check(temporary.renameTo(File(base, "batch-$batch"))) { "AI batch commit failed" }
                committed = true; reloadLocked()
            }
        } finally { if (!committed) temporary.deleteRecursively() } // Only this operation's private staging.
    }
    suspend fun addSharedText(text: String) = add("shared-text-${System.currentTimeMillis()}.txt", "text/plain", text.toByteArray())
    internal suspend fun add(name: String, mime: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        check(bytes.size <= MAX_FILE_BYTES) { "File is larger than 20 MB" }
        gate.withLock { install(name, mime, bytes); reloadLocked() }
    }
    private fun install(name: String, mime: String, bytes: ByteArray) {
        val id = UUID.randomUUID().toString()
        atomic(path(id, "data"), bytes)
        atomic(path(id, "json"), JSONObject().put("name", name.take(512)).put("mime", mime.take(128))
            .put("size", bytes.size).put("sha256", digest(bytes)).toString().toByteArray())
    }
    private fun atomic(file: File, data: ByteArray) {
        val atomic = AtomicFile(file); val stream = atomic.startWrite()
        try { stream.write(data); atomic.finishWrite(stream) } catch (failure: Throwable) { atomic.failWrite(stream); throw failure }
    }
    override suspend fun bytes(id: String): ByteArray = withContext(Dispatchers.IO) { gate.withLock {
        val file = mutable.value.firstOrNull { it.id == id } ?: error("Inbox file no longer exists")
        val bytes = path(id, "data").inputStream().use(::readBounded)
        val meta = JSONObject(path(id, "json").readText())
        check(bytes.size.toLong() == file.size && digest(bytes) == meta.getString("sha256")) { "Inbox file changed" }
        bytes
    } }
    override suspend fun remove(id: String) = withContext(Dispatchers.IO) { gate.withLock {
        // Delete only the selected validated reference; never operate on original shared URI.
        for (extension in listOf("json", "data")) { val file = path(id, extension); check(!file.exists() || file.delete()) { "Inbox file removal failed" } }
        reloadLocked()
    } }
    override suspend fun clear() = withContext(Dispatchers.IO) { gate.withLock {
        val files = mutable.value.toList()
        files.forEach { for (extension in listOf("json", "data")) { val file = path(it.id, extension)
            check(!file.exists() || file.delete()) { "Inbox file removal failed" } } }
        reloadLocked()
    } }
    companion object {
        const val MAX_FILE_BYTES = 20 * 1024 * 1024
        const val MAX_BATCH_BYTES = 20L * 1024 * 1024
        internal fun readBounded(input: InputStream): ByteArray {
            val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(64 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break
                check(out.size().toLong() + n <= MAX_FILE_BYTES) { "File is larger than 20 MB" }; out.write(buffer, 0, n) }
            return out.toByteArray()
        }
        private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
