package io.github.hootkit_dev.hootbook.core.backup

import android.content.ContentResolver
import android.net.Uri
import dev.hootkit.core.BackupFile
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class StagedBackup(val operationId: String, val file: File, val sha256: String, val size: Long)

/** File transport only. Core owns format parsing, cryptography and ledger mutation. */
class CoreBackupFiles(private val root: File, private val limit: Long = MAX_BACKUP_BYTES) {
    init { require(limit > 0); check(root.mkdirs() || root.isDirectory) }
    fun operation(id: String): File {
        require(id.matches(Regex("[0-9a-f-]{36}"))) { "Invalid operation identifier" }
        return safeChild(root, id).also { check(it.mkdirs() || it.isDirectory) }
    }
    fun newId(): String = UUID.randomUUID().toString()
    fun output(id: String): File = safeChild(operation(id), "backup.hkbak")
    fun platform(id: String): File = safeChild(operation(id), "platform").also { check(it.mkdirs() || it.isDirectory) }
    suspend fun stage(resolver: ContentResolver, uri: Uri): StagedBackup {
        require(uri.scheme == "content") { "请通过系统文件选择器选择备份" }
        return stage { resolver.openInputStream(uri) ?: error("文件提供方未返回可读文件") }
    }
    suspend fun stage(open: () -> InputStream): StagedBackup = withContext(Dispatchers.IO) {
        val id = newId()
        val file = safeChild(operation(id), "source.backup")
        try {
            open().use { source -> file.outputStream().use { target -> transfer(source, target) } }
            verify(id, file)
        } catch (failure: Throwable) { file.delete(); throw failure }
    }
    suspend fun verify(id: String, file: File): StagedBackup = withContext(Dispatchers.IO) {
        require(file.canonicalFile.parentFile == operation(id).canonicalFile && file.isFile)
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        file.inputStream().use { source ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = source.read(buffer)
                if (read < 0) break
                size += read; require(size <= limit) { "备份超过大小限制" }; digest.update(buffer, 0, read)
            }
        }
        require(size > 0) { "备份文件为空" }
        StagedBackup(id, file, digest.digest().joinToString("") { "%02x".format(it) }, size)
    }
    suspend fun save(resolver: ContentResolver, uri: Uri, source: StagedBackup) {
        require(uri.scheme == "content") { "请选择系统文件保存位置" }
        save(source) { resolver.openOutputStream(uri, "wt") ?: error("文件提供方未返回可写文件") }
    }
    suspend fun save(source: StagedBackup, open: () -> OutputStream) = withContext(Dispatchers.IO) {
        val verified = verify(source.operationId, source.file)
        require(verified.sha256 == source.sha256 && verified.size == source.size) { "暂存文件已变化，请重新检查" }
        open().use { target -> source.file.inputStream().use { transfer(it, target) }; target.flush() }
    }
    private suspend fun transfer(source: InputStream, target: OutputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE); var size = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = source.read(buffer); if (read < 0) return
            size += read; require(size <= limit) { "备份超过大小限制" }; target.write(buffer, 0, read)
        }
    }
    /** Validate every returned platform asset. Known UI assets are installed by BackupPlatformApplier. */
    fun validatePlatformFiles(id: String, names: List<String>): List<File> = names.map { name ->
        safeChild(platform(id), name).also { require(it.isFile) { "恢复的平台文件缺失：$name" } }
    }
    /** Unknown/restored assets remain portable; active preferences are exported by the platform applier. */
    fun exportPlatformFiles(id: String, names: List<String>): List<BackupFile> =
        validatePlatformFiles(id, names).map { file ->
            BackupFile("platform/" + file.relativeTo(platform(id)).invariantSeparatorsPath, file.path)
        }
    companion object {
        const val MAX_BACKUP_BYTES = 512L * 1024 * 1024
        fun safeChild(root: File, relative: String): File {
            require(relative.isNotBlank() && !relative.startsWith("/") && !relative.contains('\\') &&
                relative.split('/').none { it == ".." || it == "." || it.isEmpty() }) { "平台文件路径无效" }
            val base = root.canonicalFile
            val child = File(base, relative)
            require(child.canonicalPath.startsWith(base.path + File.separator)) { "平台文件路径越界" }
            return child
        }
    }
}
