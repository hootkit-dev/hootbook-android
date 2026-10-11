package io.github.hootkit_dev.hootbook.core.migration

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.hootkit.core.*
import dev.hootkit.platform.AndroidCoreFactory
import io.github.hootkit_dev.hootbook.core.backup.*
import io.github.hootkit_dev.hootbook.core.ui.BackupPlatformApplier
import io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferencesStore
import io.github.hootkit_dev.hootbook.presentation.core.backup.*
import io.github.hootkit_dev.hootbook.presentation.core.settings.CoreLocaleController
import java.io.File
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject

enum class LegacyMigrationPhase { IDLE, REVIEW, COMPLETE, UNCERTAIN, PLATFORM_PENDING, FAILED }
data class LegacyMigrationPreview(val summary: LegacyImportSummary, val omittedTemplates: Int,
    val omittedDefaultAccount: Boolean, val missingAttachments: Int, val missingAvatars: Int,
    val services: LegacyServiceSettings.Preview)
data class LegacyMigrationUiState(val phase: LegacyMigrationPhase = LegacyMigrationPhase.IDLE,
    val busy: Boolean = false, val preview: LegacyMigrationPreview? = null,
    val result: LegacyImportSummary? = null, val error: String? = null, val exported: Boolean = false)

/** Upgrade orchestration only. Both the preview and committed conversion are performed by Core. */
@Singleton
class LegacyMigrationCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val guard: LegacyUpgradeGuard,
    private val factory: AndroidCoreFactory,
    private val preferences: CoreUiPreferencesStore,
    private val locale: CoreLocaleController,
    private val platform: Provider<BackupPlatformApplier>,
) {
    private val root get() = guard.migrationRoot
    private val files by lazy { CoreBackupFiles(root) }
    private val journal by lazy { BackupOperationJournal(files) }
    private val mutable = MutableStateFlow(LegacyMigrationUiState())
    val state = mutable.asStateFlow()
    private val commands = Mutex()
    // A failed native teardown cannot permit another handle in this process. A fresh process
    // may reconcile the durable committed journal without replaying the import.
    private var teardownFailed = false

    suspend fun load() = command { restoreState() }

    suspend fun prepare() = command {
        val pending = recover().lastOrNull { it.blocksImport }
        if (pending != null) { select(pending); return@command }
        check(guard.sourcePresent()) { "No legacy source exists" }
        val id = files.newId()
        val directory = files.operation(id)
        val recovery = LegacyDatabaseSnapshot(context).capture(directory, id)
        val compatible = File(directory, "compatible.backup")
        val exported = LegacyServiceSettings(context).readOnly()
        val metadata = exported.use { LegacyCompatibleArchive.create(recovery, compatible, it.entries) }
        metadata.put("services", serviceJson(exported.preview))
            .put("recoverySha256", checkNotNull(LegacyDatabaseSnapshot.fingerprint(recovery)))
        writeJson(File(directory, "migration.json"), metadata)
        val source = files.verify(id, compatible)
        val temporary = File(context.noBackupFilesDir, "hootkit-core/legacy-preview").also { check(it.mkdirs() || it.isDirectory) }
        var serviceStatus = exported.preview
        val summary = IsolatedLegacyPreview().preview(compatible, temporary, locale.resolveStartupTag()) { configured, hasKey ->
            serviceStatus = serviceStatus.copy(aiConfigured = configured, aiHasKey = hasKey,
                aiRequiresSetup = serviceStatus.aiPresent && (!configured || !hasKey))
        }
        metadata.put("services", serviceJson(serviceStatus))
        writeJson(File(directory, "migration.json"), metadata)
        val operation = BackupOperation(source, true, BackupPhase.PREPARED, legacySummary = summary)
        withContext(Dispatchers.IO) {
            journal.writePreview(operation)
            journal.write(operation)
            writeJson(File(root, "active.json"), JSONObject().put("id", id))
        }
        select(operation)
    }

    suspend fun migrate(acceptLosses: Boolean) = command {
        require(acceptLosses) { "Review and accept the migration omissions first" }
        check(mutable.value.phase == LegacyMigrationPhase.REVIEW && mutable.value.preview != null)
        val operation = requireNotNull(active())
        check(operation.phase == BackupPhase.PREPARED)
        verifyOriginals(operation)
        val verified = files.verify(operation.source.operationId, operation.source.file)
        check(verified.sha256 == operation.source.sha256 && verified.size == operation.source.size)
        // Activity bootstrap, workers and widgets remain gated throughout this native handle's
        // lifetime. This explicit migration handle is offline and is never the shared session.
        withContext(NonCancellable) {
            val native = factory.open(locale.resolveStartupTag(), auth = null, remote = null)
            val access = object : CoreBackupAccess {
                override suspend fun <T> backup(block: suspend (BackupInterface) -> T): T = native.backup().use { block(it) }
                override suspend fun <T> legacy(block: suspend (LegacyInterface) -> T): T = native.legacy().use { block(it) }
            }
            val workflow = workflow(access)
            var result: BackupOperation? = null
            try {
                result = workflow.restore(operation.source, legacy = true, mode = ImportMode.MERGE,
                    password = null, replaceConfirmed = false)
                // Optional AI key persistence is outside Core's financial summary. Read only
                // safe readiness flags; failure is a visible setup requirement, not import replay.
                val meta = metadata(operation)
                val old = servicePreview(meta.getJSONObject("services"))
                val status = runCatching { native.ai().use { it.settings() } }.getOrNull()
                val updated = old.copy(aiConfigured = status?.configured, aiHasKey = status?.hasKey,
                    aiRequiresSetup = old.aiPresent && (status?.configured != true || status?.hasKey != true))
                meta.put("services", serviceJson(updated))
                writeJson(File(files.operation(operation.source.operationId), "migration.json"), meta)
            } finally {
                try {
                    try { native.shutdown() } finally { native.close() }
                } catch (failure: Throwable) {
                    teardownFailed = true
                    throw failure
                }
            }
            val committed = requireNotNull(result)
            preferences.update { it.copy(onboardingFinished = true) }
            withContext(Dispatchers.IO) { guard.complete(committed) }
            select(committed)
        }
    }

    suspend fun resumePlatform() = command {
        val operation = requireNotNull(active())
        check(operation.phase == BackupPhase.CORE_COMMITTED)
        // finishPlatform never opens/replays the native import. It only resumes verified UI files.
        val result = workflow(NoNativeAccess).finishPlatform(operation)
        preferences.update { it.copy(onboardingFinished = true) }
        guard.complete(result)
        select(result)
    }

    suspend fun exportRecovery(destination: Uri) = command {
        require(destination.scheme == "content")
        val operation = active()
        if (operation == null) guard.exportRecovery(destination)
        else {
            val metadata = metadata(operation)
            val recovery = File(files.operation(operation.source.operationId), "source.backup")
            val verified = files.verify(operation.source.operationId, recovery)
            check(verified.sha256 == metadata.getString("recoverySha256"))
            // This raw recovery package excludes converted plaintext service credentials.
            files.save(context.contentResolver, destination, verified)
        }
        mutable.update { it.copy(exported = true) }
    }

    private fun workflow(access: CoreBackupAccess) = CoreBackupWorkflow(access, files, journal, root) { platform.get().apply(it) }
    private suspend fun recover(): List<BackupOperation> = workflow(NoNativeAccess).recover()
    private fun active(): BackupOperation? {
        val pointer = readJson(File(root, "active.json")) ?: return null
        return journal.read(pointer.getString("id"))
    }
    private fun metadata(operation: BackupOperation): JSONObject = requireNotNull(readJson(File(files.operation(operation.source.operationId), "migration.json")))
    private suspend fun restoreState() {
        check(!teardownFailed) { "Restart is required to reconcile native shutdown" }
        val pending = recover().lastOrNull { it.blocksImport }
        val operation = pending ?: active()
        if (operation == null) mutable.update { it.copy(phase = LegacyMigrationPhase.IDLE) }
        else {
            if (operation.phase == BackupPhase.PLATFORM_APPLIED) {
                preferences.update { it.copy(onboardingFinished = true) }
                guard.complete(operation)
            }
            select(operation)
        }
    }
    private fun select(operation: BackupOperation) {
        val meta = metadata(operation)
        val omissions = meta.getJSONObject("omissions")
        val summary = operation.legacySummary
        val previewSummary = journal.readPreview(operation.source.operationId)?.legacySummary
        val preview = previewSummary?.let { s -> LegacyMigrationPreview(s,
            omissions.getInt("templates"), omissions.getBoolean("defaultAccount"),
            omissions.getJSONArray("missingAttachments").length(), omissions.getJSONArray("missingAvatars").length(),
            servicePreview(meta.getJSONObject("services"))) }
        val phase = when (operation.phase) {
            BackupPhase.PREPARED -> LegacyMigrationPhase.REVIEW
            BackupPhase.PLATFORM_APPLIED -> LegacyMigrationPhase.COMPLETE
            BackupPhase.CORE_COMMITTED, BackupPhase.PLATFORM_SKIPPED -> LegacyMigrationPhase.PLATFORM_PENDING
            BackupPhase.IMPORTING, BackupPhase.UNCERTAIN -> LegacyMigrationPhase.UNCERTAIN
            BackupPhase.FAILED -> LegacyMigrationPhase.FAILED
        }
        mutable.update { it.copy(phase = phase, preview = preview ?: it.preview,
            result = summary.takeIf { phase in setOf(LegacyMigrationPhase.COMPLETE, LegacyMigrationPhase.PLATFORM_PENDING) }) }
    }
    private suspend fun verifyOriginals(operation: BackupOperation) = withContext(Dispatchers.IO) {
        val fingerprints = metadata(operation).getJSONObject("sourceFingerprints")
        val database = context.getDatabasePath("pennywise_database")
        listOf(database, File(database.path+"-wal"), File(database.path+"-shm")).forEach { file ->
            val expected = if (fingerprints.isNull(file.name)) null else fingerprints.getString(file.name)
            check(LegacyDatabaseSnapshot.fingerprint(file) == expected) { "Legacy source changed; prepare a new preview" }
        }
    }
    private suspend fun command(block: suspend () -> Unit) {
        if (!commands.tryLock()) return
        mutable.update { it.copy(busy = true, error = null) }
        try {
            check(!teardownFailed) { "Restart is required to reconcile native shutdown" }
            withContext(Dispatchers.IO) { block() }
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            runCatching { restoreState() }
            mutable.update { it.copy(phase = if (teardownFailed) LegacyMigrationPhase.UNCERTAIN else if (it.phase == LegacyMigrationPhase.IDLE) LegacyMigrationPhase.FAILED else it.phase,
                error = backupFailureText(failure, context)) }
        } finally { mutable.update { it.copy(busy = false) }; commands.unlock() }
    }

    private object NoNativeAccess : CoreBackupAccess {
        override suspend fun <T> backup(block: suspend (BackupInterface) -> T): T = error("Native access is unavailable in journal recovery")
        override suspend fun <T> legacy(block: suspend (LegacyInterface) -> T): T = error("Native access is unavailable in journal recovery")
    }
}

private fun serviceJson(p: LegacyServiceSettings.Preview) = JSONObject()
    .put("aiPresent",p.aiPresent).put("aiExported",p.aiExported).put("aiAccessFailed",p.aiAccessFailed).put("aiRequiresSetup",p.aiRequiresSetup)
    .put("brokeragePresent",p.brokeragePresent).put("brokerageConnections",p.brokerageConnections).put("brokerageAccounts",p.brokerageAccounts)
    .put("brokerageHoldings",p.brokerageHoldings).put("brokerageAccessFailed",p.brokerageAccessFailed)
    .put("aiConfigured",p.aiConfigured ?: JSONObject.NULL).put("aiHasKey",p.aiHasKey ?: JSONObject.NULL)
private fun servicePreview(p: JSONObject) = LegacyServiceSettings.Preview(
    p.getBoolean("aiPresent"),p.getBoolean("aiExported"),p.getBoolean("aiAccessFailed"),p.getBoolean("aiRequiresSetup"),
    p.getBoolean("brokeragePresent"),p.getInt("brokerageConnections"),p.getInt("brokerageAccounts"),p.getInt("brokerageHoldings"),p.getBoolean("brokerageAccessFailed"),
    if(p.isNull("aiConfigured")) null else p.getBoolean("aiConfigured"), if(p.isNull("aiHasKey")) null else p.getBoolean("aiHasKey"))
private fun writeJson(file: File, value: JSONObject) {
    val atomic = AtomicFile(file);val out = atomic.startWrite()
    try { out.write(value.toString().toByteArray());atomic.finishWrite(out) } catch(failure:Throwable) { atomic.failWrite(out);throw failure }
}
private fun readJson(file: File): JSONObject? {
    val atomic = AtomicFile(file)
    if (!atomic.baseFile.isFile && !File(file.path+".bak").isFile) return null
    return atomic.openRead().use { JSONObject(it.bufferedReader().readText()) }
}
