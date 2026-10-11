package io.github.hootkit_dev.hootbook.core.migration

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** A recovery export is not a commit receipt and never marks a legacy migration complete. */
@Singleton
class LegacyUpgradeGuard @Inject constructor(@ApplicationContext private val context: Context) {
    private val mutex = Mutex()
    internal val migrationRoot: File get() = File(context.noBackupFilesDir, "hootkit-core/legacy-migration")
    internal fun sourcePresent(): Boolean = context.getDatabasePath("pennywise_database").isFile ||
        File(context.getDatabasePath("pennywise_database").path + "-wal").isFile
    fun required(): Boolean = sourcePresent() && !completed()
    private fun completed(): Boolean = runCatching {
        val marker = AtomicFile(File(migrationRoot, "completed.json"))
        if (!marker.baseFile.exists() && !File(marker.baseFile.path+".bak").exists()) return@runCatching false
        val json = marker.openRead().use { JSONObject(it.bufferedReader().readText()) }
        val id = json.getString("operationId")
        require(id.matches(Regex("[0-9a-f-]{36}")))
        val receipt = AtomicFile(File(File(migrationRoot,id), "journal.json")).openRead().use { JSONObject(it.bufferedReader().readText()) }
        context.getDatabasePath("hootkit.sqlite").isFile && receipt.getString("id") == id &&
            receipt.getBoolean("legacy") && receipt.getString("phase") == "PLATFORM_APPLIED" &&
            receipt.getString("sha256") == json.getString("sha256") && receipt.has("legacySummary")
    }.getOrDefault(false)
    internal fun complete(operation: io.github.hootkit_dev.hootbook.core.backup.BackupOperation) {
        check(operation.phase == io.github.hootkit_dev.hootbook.core.backup.BackupPhase.PLATFORM_APPLIED && operation.legacySummary != null)
        check(context.getDatabasePath("hootkit.sqlite").isFile)
        val marker = AtomicFile(File(migrationRoot, "completed.json"))
        val out = marker.startWrite()
        try { out.write(JSONObject().put("operationId",operation.source.operationId).put("sha256",operation.source.sha256).toString().toByteArray()); marker.finishWrite(out) }
        catch(failure:Throwable) { marker.failWrite(out);throw failure }
    }

    /** Covers workers/widgets that may run before Activity bootstrap. No native handle opens first. */
    fun requireCoreReady() {
        check(!required()) { "Legacy recovery is required before Core can open" }
    }

    suspend fun exportRecovery(destination: Uri) = withContext(Dispatchers.IO) { mutex.withLock {
        require(destination.scheme == "content") { "请通过系统文件保存位置导出" }
        val root = File(context.noBackupFilesDir, "hootkit-core/legacy-recovery").also { check(it.mkdirs() || it.isDirectory) }
        val id = UUID.randomUUID().toString()
        val directory = File(root,id).also { check(it.mkdirs()) }
        val file = LegacyDatabaseSnapshot(context).capture(directory,id)
        val digest = checkNotNull(LegacyDatabaseSnapshot.fingerprint(file))
        val atomic = AtomicFile(File(directory,"recovery.json"))
        val out = atomic.startWrite()
        try {
            out.write(JSONObject().put("version",1).put("operationId",id).put("sha256",digest)
                .put("bytes",file.length()).put("state","CAPTURED_NOT_IMPORTED").toString().toByteArray())
            atomic.finishWrite(out)
        } catch (failure:Throwable) { atomic.failWrite(out); throw failure }
        check(LegacyDatabaseSnapshot.fingerprint(file) == digest)
        val target = context.contentResolver.openOutputStream(destination,"wt") ?: error("无法打开导出位置")
        target.use { output -> file.inputStream().use { it.copyTo(output) }; output.flush() }
        check(LegacyDatabaseSnapshot.fingerprint(file) == digest)
        // Original DB, WAL, preferences, platform files and the private recovery package remain intact.
    } }
}
