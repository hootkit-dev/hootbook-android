package io.github.hootkit_dev.hootbook.core.attachments

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Filesystem boundary only. The root is exactly AndroidCoreFactory's CoreConfig.attachments_dir.
 * Core owns attachment references; this store never reads SQL or guesses whether an old file is unused.
 */
@Singleton
class CoreAttachmentStore @Inject constructor(@ApplicationContext context: Context) {
    private val context = context.applicationContext
    private val directory = File(this.context.noBackupFilesDir, "hootkit-core/attachments")
    private val ownership = Any()
    private val draftOwned = mutableSetOf<String>()

    suspend fun importFrom(uri: Uri): String = withContext(Dispatchers.IO) {
        io.github.hootkit_dev.hootbook.core.CoreUriBoundary.requireExternalDocument(context, uri)
        val extension = when (context.contentResolver.getType(uri)) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "application/pdf" -> "pdf"
            "text/plain" -> "txt"
            "text/csv" -> "csv"
            else -> "bin"
        }
        val input = context.contentResolver.openInputStream(uri) ?: error("Document stream unavailable")
        importStream(input, extension)
    }

    internal suspend fun importStream(input: InputStream, extension: String = "bin"): String = withContext(Dispatchers.IO) {
        require(extension.matches(Regex("[a-z0-9]{1,8}")))
        check(directory.isDirectory || directory.mkdirs()) { "Attachment directory unavailable" }
        val reference = "${UUID.randomUUID()}.$extension"
        val temporary = File(directory, ".pending-${UUID.randomUUID()}")
        val target = File(directory, reference)
        var installed = false
        try {
            input.use { source -> temporary.outputStream().use { output ->
                val bytes = ByteArray(32 * 1024)
                var count = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = source.read(bytes)
                    if (read < 0) break
                    if (read == 0) continue
                    count += read
                    require(count <= MAX_ATTACHMENT_BYTES) { "Attachment exceeds 64 MiB" }
                    output.write(bytes, 0, read)
                }
                output.fd.sync()
            } }
            currentCoroutineContext().ensureActive()
            check(temporary.renameTo(target)) { "Attachment installation failed" }
            synchronized(ownership) { draftOwned += reference }
            installed = true
            reference
        } finally {
            temporary.delete()
            if (!installed) target.delete()
        }
    }

    /** Traversal/absolute paths/symlinks out of the Core root never resolve. */
    fun resolve(reference: String): File? {
        if (reference.isBlank() || reference.indexOf('\u0000') >= 0 || '\\' in reference || File(reference).isAbsolute) return null
        if (reference.split('/').any { it.isBlank() || it == "." || it == ".." }) return null
        return runCatching {
            val root = directory.canonicalFile
            File(directory, reference).canonicalFile.takeIf { file ->
                file.path.startsWith(root.path + File.separator) && file.isFile
            }
        }.getOrNull()
    }

    /** Call BEFORE a Core mutation. Unknown/committed refs are never candidates for draft cleanup. */
    fun retainForCore(references: Collection<String>) {
        synchronized(ownership) { draftOwned.removeAll(references.toSet()) }
    }

    /** Only files imported by this live store and not retained may be deleted; no global orphan scan. */
    suspend fun discardDraft(references: Collection<String>) = withContext(Dispatchers.IO) {
        for (reference in references.distinct()) synchronized(ownership) {
            if (draftOwned.remove(reference)) resolve(reference)?.delete()
        }
    }

    /** Copy for a narrow FileProvider path: core_attachment_share / cache/core-attachment-share/. */
    suspend fun copyForSharing(reference: String): File = withContext(Dispatchers.IO) {
        val source = resolve(reference) ?: error("Attachment unavailable")
        val cache = File(context.cacheDir, "core-attachment-share")
        check(cache.isDirectory || cache.mkdirs())
        val extension = source.extension.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) } ?: "bin"
        val target = File(cache, "${UUID.randomUUID()}.$extension")
        var copied = false
        try {
            source.inputStream().use { input -> target.outputStream().use { output ->
                val bytes = ByteArray(32 * 1024)
                var count = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(bytes)
                    if (read < 0) break
                    if (read == 0) continue
                    count += read
                    require(count <= MAX_ATTACHMENT_BYTES) { "Attachment exceeds 64 MiB" }
                    output.write(bytes, 0, read)
                }
            } }
            copied = true
            target
        } finally { if (!copied) target.delete() }
    }

    /** Exporters stream into this private cache file; publish it only after their full export succeeds. */
    suspend fun createShareFile(extension: String): File = withContext(Dispatchers.IO) {
        require(extension.matches(Regex("[a-z0-9]{1,8}")))
        val cache = File(context.cacheDir, "core-attachment-share")
        check(cache.isDirectory || cache.mkdirs())
        File(cache, "${UUID.randomUUID()}.$extension").also { check(it.createNewFile()) }
    }

    companion object { const val MAX_ATTACHMENT_BYTES: Long = 64L * 1024 * 1024 }
}
