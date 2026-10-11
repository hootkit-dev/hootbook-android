package io.github.hootkit_dev.hootbook.presentation.core.backup

import dev.hootkit.core.*
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.core.backup.*
import java.io.File
import android.util.AtomicFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal interface CoreBackupAccess {
    suspend fun <T> backup(block: suspend (BackupInterface) -> T): T
    suspend fun <T> legacy(block: suspend (LegacyInterface) -> T): T
    fun importCommitted() {}
}
internal class SessionBackupAccess(private val session: CoreSession,
    private val onImportCommitted: () -> Unit = {}) : CoreBackupAccess {
    override suspend fun <T> backup(block: suspend (BackupInterface) -> T): T = session.awaitCore().backup().use { block(it) }
    override suspend fun <T> legacy(block: suspend (LegacyInterface) -> T): T = session.awaitCore().legacy().use { block(it) }
    override fun importCommitted() = onImportCommitted()
}

/** Core remains the sole implementation of import rules, encryption and safety backups. */
internal class CoreBackupWorkflow(
    private val access: CoreBackupAccess,
    val files: CoreBackupFiles,
    private val journal: BackupOperationJournal,
    private val root: File,
    private val applyPlatform: (suspend (BackupOperation) -> Unit)? = null,
) {
    suspend fun recover(): List<BackupOperation> = withContext(Dispatchers.IO) {
        journal.all(root).map { operation ->
            if (operation.phase == BackupPhase.IMPORTING) operation.copy(phase = BackupPhase.UNCERTAIN).also(journal::write)
            else operation
        }
    }
    suspend fun inspect(source: StagedBackup, legacy: Boolean, password: String?): Pair<BackupInfo?, LegacyInfo?> {
        verify(source)
        return if (legacy) null to access.legacy { it.inspectBackup(source.file.path, password) }
        else access.backup { it.inspectBackup(source.file.path, password) } to null
    }
    suspend fun export(options: ExportOptions): Pair<StagedBackup, BackupInfo> {
        val id = files.newId(); val output = files.output(id)
        return try {
            val info = access.backup { it.exportBackup(output.path, options) }
            files.verify(id, output) to info
        } catch (failure: Throwable) { output.delete(); throw failure }
    }
    suspend fun preservedAssets(): List<BackupFile> = withContext(Dispatchers.IO) {
        val latest = linkedMapOf<String, BackupFile>()
        journal.all(root).filter { it.phase in setOf(BackupPhase.PLATFORM_APPLIED, BackupPhase.PLATFORM_SKIPPED, BackupPhase.CORE_COMMITTED) }.forEach { operation ->
            files.exportPlatformFiles(operation.source.operationId, operation.files).forEach { latest[it.entryName] = it }
        }
        latest.values.toList()
    }
    suspend fun restore(source: StagedBackup, legacy: Boolean, mode: ImportMode, password: String?, replaceConfirmed: Boolean): BackupOperation =
        withContext(NonCancellable) {
            require(mode != ImportMode.REPLACE_ALL || legacy || replaceConfirmed) { "请先确认替换全部本地数据及同步删除影响" }
            val pending = recover().filter { it.blocksImport }
            check(pending.isEmpty()) { "上次恢复待处理，请先检查恢复状态；禁止重复提交" }
            verify(source)
            var operation = BackupOperation(source, legacy, BackupPhase.IMPORTING)
            withContext(Dispatchers.IO) { journal.write(operation) }
            var returned = false
            try {
                operation = if (legacy) operation.copy(phase = BackupPhase.CORE_COMMITTED,
                    legacySummary = access.legacy { it.importBackup(source.file.path, LegacyOptions(password, files.platform(source.operationId).path)) })
                else operation.copy(phase = BackupPhase.CORE_COMMITTED,
                    importSummary = access.backup { it.importBackup(source.file.path, ImportOptions(mode, password, files.platform(source.operationId).path)) })
                returned = true
                withContext(Dispatchers.IO) { journal.write(operation) }
                // A best-effort app refresh is independent of the committed ledger and UI files.
                // Isolated previews and guarded migration handles use the default no-op callback.
                runCatching { access.importCommitted() }
                finishPlatform(operation)
            } catch (failure: Throwable) {
                // A typed error is not a commit receipt: the database writer's await/panic/closed
                // paths can report Storage(IO) after mutation. Only pinned preflight evidence below
                // permits FAILED; every other result retains a durable no-replay barrier.
                if (!returned) {
                    operation = operation.copy(phase = if (hasPreflightFailureEvidence(failure, legacy))
                        BackupPhase.FAILED else BackupPhase.UNCERTAIN)
                    withContext(Dispatchers.IO) { journal.write(operation) }
                }
                throw failure
            }
        }
    suspend fun finishPlatform(operation: BackupOperation): BackupOperation = withContext(Dispatchers.IO) {
        check(operation.phase == BackupPhase.CORE_COMMITTED || operation.phase == BackupPhase.PLATFORM_APPLIED)
        files.validatePlatformFiles(operation.source.operationId, operation.files)
        // Preserve opaque input for review. The production applier whitelists platform UI fields only.
        operation.preferences?.let { json ->
            val atomic = AtomicFile(CoreBackupFiles.safeChild(files.operation(operation.source.operationId), "preferences-review.json"))
            val out = atomic.startWrite()
            try { out.write(json.toByteArray(Charsets.UTF_8)); atomic.finishWrite(out) }
            catch (failure: Throwable) { atomic.failWrite(out); throw failure }
        }
        applyPlatform?.invoke(operation)
        operation.copy(phase = BackupPhase.PLATFORM_APPLIED).also(journal::write)
    }
    suspend fun skipPlatform(operation: BackupOperation): BackupOperation = withContext(Dispatchers.IO) {
        check(operation.phase == BackupPhase.CORE_COMMITTED)
        operation.copy(phase = BackupPhase.PLATFORM_SKIPPED).also(journal::write)
    }
    private suspend fun verify(source: StagedBackup) {
        val current = files.verify(source.operationId, source.file)
        require(current.sha256 == source.sha256 && current.size == source.size) { "源副本已变化，请重新选择并检查备份" }
    }
}

/** Narrow evidence from the pinned Core, not a generic rule that typed errors imply rollback.
 * backup/archive.rs and legacy/source.rs: file/password parse errors before ledger mutation.
 * legacy/mod.rs + legacy/import.rs: NOT_EMPTY before the legacy import transaction.
 * backup/import.rs read_data: SCHEMA_TOO_NEW during load, before safety backup / import.
 * Re-audit this whitelist when upgrading Core. Unknown fields/reasons must remain uncertain.
 */
internal fun hasPreflightFailureEvidence(failure: Throwable, legacy: Boolean): Boolean = when (failure) {
    is CoreException.Invalid -> when (failure.field) {
        "file" -> failure.reason == InvalidReason.BAD_FORMAT || failure.reason == InvalidReason.UNKNOWN_VALUE
        "password" -> failure.reason == InvalidReason.REQUIRED || failure.reason == InvalidReason.BAD_FORMAT
        else -> false
    }
    is CoreException.Conflict -> legacy && failure.reason == ConflictReason.NOT_EMPTY
    is CoreException.Storage -> !legacy && failure.kind == StorageErrorKind.SCHEMA_TOO_NEW
    else -> false
}

/** No storage detail strings, HTTP response body, passwords or user financial data in UI failures. */
internal fun backupFailureText(failure: Throwable): String = when (failure) {
    is CoreException.Invalid -> when {
        failure.field == "password" && failure.reason == InvalidReason.REQUIRED -> "备份需要口令，请输入后重新检查"
        failure.field == "password" -> "口令错误或加密文件损坏，请核对原始文件和口令"
        failure.reason == InvalidReason.UNKNOWN_VALUE -> "备份协议版本不受当前 Core 支持，请更新 App 或选择兼容备份"
        else -> "备份格式或参数无效：${failure.field} / ${failure.reason}"
    }
    is CoreException.Conflict -> if (failure.reason == ConflictReason.NOT_EMPTY)
        "旧备份只接受空账本；请保留原文件，先导出当前新账本，不会自动清空数据" else "备份操作冲突：${failure.reason}"
    is CoreException.Storage -> if (failure.kind == StorageErrorKind.SCHEMA_TOO_NEW)
        "备份数据库版本较新，请升级 App 后恢复" else "存储操作失败（${failure.kind}），保留原备份并检查可用空间"
    is CoreException.Platform -> "平台存储或密钥操作失败，请保留原备份后重试"
    is CancellationException, is CoreException.Cancelled -> "操作中断，请检查恢复状态；未确认结果前不要重复导入"
    is IllegalArgumentException, is IllegalStateException -> failure.message ?: "请检查文件与确认选项"
    else -> "文件操作失败，请保留原备份并检查文件访问权限与恢复状态"
}

/** Localized production failures never include raw storage detail, response body or secrets. */
internal fun backupFailureText(failure: Throwable, context: android.content.Context): String {
    val message = when (failure) {
        is CoreBackupUiException -> failure.resource
        is CoreException.Invalid -> when {
            failure.field == "password" && failure.reason == InvalidReason.REQUIRED -> io.github.hootkit_dev.hootbook.R.string.backup_err_needs_password
            failure.field == "password" -> io.github.hootkit_dev.hootbook.R.string.backup_err_wrong_password
            failure.reason == InvalidReason.UNKNOWN_VALUE -> io.github.hootkit_dev.hootbook.R.string.backup_err_unsupported
            else -> io.github.hootkit_dev.hootbook.R.string.backup_err_invalid
        }
        is CoreException.Conflict -> if(failure.reason==ConflictReason.NOT_EMPTY) io.github.hootkit_dev.hootbook.R.string.backup_err_not_empty else io.github.hootkit_dev.hootbook.R.string.backup_err_invalid
        is CoreException.Storage -> if(failure.kind==StorageErrorKind.SCHEMA_TOO_NEW) io.github.hootkit_dev.hootbook.R.string.backup_err_upgrade else io.github.hootkit_dev.hootbook.R.string.backup_err_storage
        is CancellationException, is CoreException.Cancelled -> io.github.hootkit_dev.hootbook.R.string.backup_err_interrupted
        is IllegalArgumentException, is IllegalStateException -> io.github.hootkit_dev.hootbook.R.string.backup_err_inspect_first
        else -> io.github.hootkit_dev.hootbook.R.string.core_ui_operation_failed
    }
    return context.getString(message)
}

internal class CoreBackupUiException(val resource: Int, cause: Throwable? = null) : Exception(null, cause)
