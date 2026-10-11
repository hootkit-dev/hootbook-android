package io.github.hootkit_dev.hootbook.presentation.core.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.hootkit.core.*
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.core.backup.*
import io.github.hootkit_dev.hootbook.core.CoreCurrencyRefreshCoordinator
import io.github.hootkit_dev.hootbook.core.ui.BackupPlatformApplier
import io.github.hootkit_dev.hootbook.R
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CoreBackupUiState(
    val busy: Boolean = true,
    val source: StagedBackup? = null,
    val legacy: Boolean = false,
    val info: BackupInfo? = null,
    val legacyInfo: LegacyInfo? = null,
    val exportFile: StagedBackup? = null,
    val exportInfo: BackupInfo? = null,
    val operations: List<BackupOperation> = emptyList(),
    val result: BackupOperation? = null,
    val shareFile: File? = null,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class CoreBackupViewModel private constructor(
    private val context: Context,
    private val workflow: CoreBackupWorkflow,
    private val platform: BackupPlatformApplier? = null,
) : ViewModel() {
    @Inject constructor(@ApplicationContext context: Context, session: CoreSession, platform: BackupPlatformApplier,
        currencies: CoreCurrencyRefreshCoordinator) : this(context,
        File(context.noBackupFilesDir, "hootkit-core/backup-ui").let { root ->
            val files = CoreBackupFiles(root)
            CoreBackupWorkflow(SessionBackupAccess(session, currencies::importCommitted), files, BackupOperationJournal(files), root) { operation ->
                try { platform.apply(operation) } catch(cancelled: CancellationException) { throw cancelled }
                catch(failure: Exception) { throw CoreBackupUiException(R.string.backup_err_platform_failed, failure) }
            }
        }, platform)
    internal constructor(context: Context, access: CoreBackupAccess, root: File) : this(context,
        CoreBackupFiles(root).let { files -> CoreBackupWorkflow(access, files, BackupOperationJournal(files), root) })
    private val mutable = MutableStateFlow(CoreBackupUiState())
    val state = mutable.asStateFlow()
    private var inspectedPassword: String? = null
    init {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    File(context.cacheDir, "core-backup-share").listFiles().orEmpty()
                        .filter { it.isFile && System.currentTimeMillis() - it.lastModified() > 24 * 60 * 60 * 1000L }
                        .forEach { it.delete() }
                }
                mutable.update { it.copy(operations = workflow.recover()) }
            } catch (failure: Exception) { mutable.update { it.copy(error = backupFailureText(failure, context)) } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun canBack(): Boolean = !mutable.value.busy
    fun clearError() = mutable.update { it.copy(error = null) }
    fun clearMessage() = mutable.update { it.copy(message = null) }
    /** Opens a fresh export form: the file made last time is not offered again. */
    fun clearExport() { if (!mutable.value.busy) mutable.update { it.copy(exportFile = null, exportInfo = null, message = null, error = null) } }
    fun invalidateInspection() {
        if (!mutable.value.busy) { inspectedPassword = null; mutable.update { it.copy(info = null, legacyInfo = null) } }
    }
    fun select(uri: Uri?, legacy: Boolean, password: String?) {
        if (uri == null) return // System picker cancellation leaves all originals and current state intact.
        command {
            io.github.hootkit_dev.hootbook.core.CoreUriBoundary.requireExternalDocument(context, uri)
            val source = workflow.files.stage(context.contentResolver, uri)
            inspectedPassword = null
            mutable.update { it.copy(source = source, legacy = legacy, info = null, legacyInfo = null, result = null) }
            inspectSource(source, legacy, password)
        }
    }
    fun inspect(password: String?) {
        val snapshot = mutable.value; val source = snapshot.source ?: return
        command { inspectSource(source, snapshot.legacy, password) }
    }
    private suspend fun inspectSource(source: StagedBackup, legacy: Boolean, password: String?) {
        val (info, legacyInfo) = workflow.inspect(source, legacy, password)
        inspectedPassword = password
        mutable.update { it.copy(info = info, legacyInfo = legacyInfo) }
    }
    fun export(includeAttachments: Boolean, includeAi: Boolean, includeBrokerage: Boolean, password: String?, secretsConfirmed: Boolean) {
        command {
            if ((includeAi || includeBrokerage) && password.isNullOrBlank() && !secretsConfirmed)
                throw CoreBackupUiException(R.string.backup_err_plain_secrets)
            val snapshot = platform?.exportSnapshot()
            val platformFiles = (workflow.preservedAssets() + snapshot?.files.orEmpty()).associateBy { it.entryName }.values.toList()
            val (file, info) = workflow.export(ExportOptions(includeAttachments, includeAi, includeBrokerage,
                password, preferencesJson = snapshot?.preferencesJson, platformFiles = platformFiles, device = android.os.Build.MODEL))
            mutable.update { it.copy(exportFile = file, exportInfo = info, message = context.getString(R.string.backup_msg_created)) }
        }
    }
    fun restore(mode: ImportMode, password: String?, replaceConfirmed: Boolean, sensitiveConfirmed: Boolean) {
        val snapshot = mutable.value; val source = snapshot.source ?: return
        command {
            if ((snapshot.info == null && snapshot.legacyInfo == null) || inspectedPassword != password)
                throw CoreBackupUiException(R.string.backup_err_inspect_first)
            if ((snapshot.legacy || snapshot.info?.includes?.ai == true || snapshot.info?.includes?.brokerage == true) && !sensitiveConfirmed)
                throw CoreBackupUiException(R.string.backup_err_sensitive)
            val result = workflow.restore(source, snapshot.legacy, mode, password, replaceConfirmed)
            inspectedPassword = null
            mutable.update { it.copy(result = result, info = null, legacyInfo = null,
                message = context.getString(R.string.backup_msg_applied)) }
        }
    }
    fun finishPlatform(operation: BackupOperation) { command {
        val result = workflow.finishPlatform(operation)
        mutable.update { it.copy(result = result, message = context.getString(R.string.backup_msg_platform_applied)) }
    } }
    fun skipPlatform(operation: BackupOperation) { command {
        val result = workflow.skipPlatform(operation)
        mutable.update { it.copy(result = result, message = context.getString(R.string.backup_msg_platform_skipped)) }
    } }
    fun save(uri: Uri?) {
        if (uri == null) return
        val file = mutable.value.exportFile ?: return
        command { workflow.files.save(context.contentResolver, uri, file); mutable.update { it.copy(message = context.getString(R.string.backup_msg_saved)) } }
    }
    fun savePreferences(uri: Uri?) {
        if (uri == null) return
        val operation = mutable.value.result ?: mutable.value.operations.lastOrNull { it.phase == BackupPhase.PLATFORM_APPLIED } ?: return
        if (operation.preferences == null) return
        command {
            val file = CoreBackupFiles.safeChild(workflow.files.operation(operation.source.operationId), "preferences-review.json")
            val verified = workflow.files.verify(operation.source.operationId, file)
            workflow.files.save(context.contentResolver, uri, verified)
            mutable.update { it.copy(message = context.getString(R.string.backup_msg_preferences_saved)) }
        }
    }
    fun saveSource(uri: Uri?, operationId: String) {
        if (uri == null) return
        val operation = mutable.value.operations.find { it.source.operationId == operationId } ?: return
        command { workflow.files.save(context.contentResolver, uri, operation.source)
            mutable.update { it.copy(message = context.getString(R.string.backup_msg_source_saved)) } }
    }
    fun share() {
        val file = mutable.value.exportFile ?: return
        command {
            val output = withContext(Dispatchers.IO) {
                val root = File(context.cacheDir, "core-backup-share").also { check(it.mkdirs() || it.isDirectory) }
                CoreBackupFiles.safeChild(root, workflow.files.newId() + ".hkbak")
            }
            try { workflow.files.save(file) { output.outputStream() }; mutable.update { it.copy(shareFile = output) } }
            catch (failure: Throwable) { output.delete(); throw failure }
        }
    }
    fun shareHandled(error: Boolean = false) { mutable.update { it.copy(shareFile = null,
        error = if (error) context.getString(R.string.backup_err_share) else it.error) } }
    private fun command(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            try { block() }
            catch (failure: CancellationException) { throw failure }
            catch (failure: Exception) { mutable.update { it.copy(error = backupFailureText(failure, context)) } }
            finally {
                withContext(NonCancellable) {
                    try { mutable.update { it.copy(operations = workflow.recover()) } }
                    catch (failure: Exception) { mutable.update { it.copy(error = context.getString(R.string.backup_err_journal)) } }
                    mutable.update { it.copy(busy = false) }
                }
            }
        }
    }
}
