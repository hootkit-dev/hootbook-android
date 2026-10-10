package io.github.hootkit_dev.hootbook.data.cloud.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import io.github.hootkit_dev.hootbook.data.backup.BackupConfiguration
import io.github.hootkit_dev.hootbook.data.backup.BackupExporter
import io.github.hootkit_dev.hootbook.data.backup.BackupImporter
import io.github.hootkit_dev.hootbook.data.backup.ExportResult
import io.github.hootkit_dev.hootbook.data.backup.ImportResult
import io.github.hootkit_dev.hootbook.data.backup.ImportStrategy
import io.github.hootkit_dev.hootbook.data.cloud.CloudFileInfo
import io.github.hootkit_dev.hootbook.data.cloud.CloudProviderConfig
import io.github.hootkit_dev.hootbook.data.cloud.CloudProviderType
import io.github.hootkit_dev.hootbook.data.cloud.CloudStorageProvider
import io.github.hootkit_dev.hootbook.data.cloud.providers.GoogleDriveStorageProvider
import io.github.hootkit_dev.hootbook.data.cloud.providers.WebDavStorageProvider
import io.github.hootkit_dev.hootbook.data.cloud.security.BackupEncryptionEngine
import io.github.hootkit_dev.hootbook.data.cloud.security.CloudCredentialStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates cloud backups, retention pruning, and snapshot restores across active cloud storage providers.
 */
@Singleton
class CloudBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupExporter: BackupExporter,
    private val backupImporter: BackupImporter,
    private val webDavProvider: WebDavStorageProvider,
    private val googleDriveProvider: GoogleDriveStorageProvider,
    private val cloudCredentialStore: CloudCredentialStore,
    private val encryptionEngine: BackupEncryptionEngine
) {

    companion object {
        const val BACKUPS_FOLDER = "cashiro_backups"
    }

    /**
     * Resolves the active provider and valid credentials configuration.
     */
    fun getActiveProvider(): Pair<CloudStorageProvider, CloudProviderConfig>? {
        return when (cloudCredentialStore.getActiveProviderType()) {
            CloudProviderType.WEBDAV -> {
                val config = cloudCredentialStore.getWebDavConfig()
                if (config.isConfigured) webDavProvider to config else null
            }
            CloudProviderType.GOOGLE_DRIVE -> {
                val config = cloudCredentialStore.getGoogleDriveConfig()
                if (config.isConfigured) googleDriveProvider to config else null
            }
            CloudProviderType.LOCAL_ONLY -> null
        }
    }

    /**
     * Export complete application snapshot and upload to active cloud provider folder.
     */
    suspend fun performBackup(
        config: BackupConfiguration = BackupConfiguration(),
        progressListener: ((Int) -> Unit)? = null
    ): Result<CloudFileInfo> = withContext(Dispatchers.IO) {
        val (provider, providerConfig) = getActiveProvider()
            ?: return@withContext Result.failure(IllegalStateException("No active or configured cloud storage provider selected."))

        try {
            progressListener?.invoke(5)
            val passphrase = if (cloudCredentialStore.isE2eEncryptionEnabled()) {
                cloudCredentialStore.getE2ePassphrase().takeIf { it.isNotBlank() }
            } else null
            // Brokerage tokens only travel inside an end-to-end encrypted file
            val exportResult = backupExporter.exportBackup(config.copy(includeBrokerageCredentials = passphrase != null, includeAiKey = passphrase != null))
            if (exportResult !is ExportResult.Success) {
                val errorMsg = if (exportResult is ExportResult.Error) exportResult.message else "Backup export failed"
                return@withContext Result.failure(Exception(errorMsg))
            }

            var fileToUpload = exportResult.file
            if (passphrase != null) {
                val encryptedFile = File(context.cacheDir, "${fileToUpload.nameWithoutExtension}.enc")
                val encResult = encryptionEngine.encryptFile(fileToUpload, encryptedFile, passphrase)
                if (encResult.isSuccess && encResult.getOrNull() != null) {
                    // Clean up plaintext zip
                    fileToUpload.delete()
                    fileToUpload = encResult.getOrNull()!!
                } else {
                    fileToUpload.delete()
                    return@withContext Result.failure(Exception("Failed to encrypt backup file before upload."))
                }
            }

            progressListener?.invoke(25)
            val remotePath = "$BACKUPS_FOLDER/${fileToUpload.name}"
            val uploadResult = provider.uploadFile(fileToUpload, remotePath, providerConfig) { uploadProgress ->
                // Map 0-100 upload progress to 25-95 total progress
                val mapped = 25 + ((uploadProgress * 70) / 100)
                progressListener?.invoke(mapped)
            }

            // Cleanup local temporary export file after successful upload
            if (fileToUpload.exists()) {
                fileToUpload.delete()
            }

            if (uploadResult.isSuccess) {
                cloudCredentialStore.setLastBackupTimestamp(System.currentTimeMillis())
                // Enforce retention limit after successful upload
                enforceRetentionPolicy(cloudCredentialStore.getRetentionLimit(), provider, providerConfig)
                progressListener?.invoke(100)
                Result.success(uploadResult.getOrNull()!!)
            } else {
                Result.failure(uploadResult.exceptionOrNull() ?: Exception("Cloud upload failed"))
            }
        } catch (e: Exception) {
            Log.e("CloudBackupManager", "Perform backup error", e)
            Result.failure(e)
        }
    }

    /**
     * List all remote backup snapshots stored inside cashiro_backups folder.
     */
    suspend fun listRemoteBackups(): Result<List<CloudFileInfo>> = withContext(Dispatchers.IO) {
        val (provider, providerConfig) = getActiveProvider()
            ?: return@withContext Result.failure(IllegalStateException("No active or configured cloud storage provider."))

        try {
            val listResult = provider.listFiles(BACKUPS_FOLDER, providerConfig)
            if (listResult.isSuccess) {
                val backups = listResult.getOrNull() ?: emptyList()
                // Backups made by Cashiro before the rename are still listed and restorable.
                val filtered = backups.filter {
                    (it.name.startsWith("Hootbook_Backup_") || it.name.startsWith("Cashiro_Backup_")) && (it.name.endsWith(".zip") || it.name.endsWith(".enc"))
                }.sortedByDescending { it.lastModified }
                Result.success(filtered)
            } else {
                Result.failure(listResult.exceptionOrNull() ?: Exception("Failed to list backups"))
            }
        } catch (e: Exception) {
            Log.e("CloudBackupManager", "List backups error", e)
            Result.failure(e)
        }
    }

    /**
     * Download a remote backup snapshot and restore it into Hootbook Room database.
     */
    suspend fun restoreBackup(
        fileInfo: CloudFileInfo,
        progressListener: ((Int) -> Unit)? = null
    ): Result<ImportResult> = withContext(Dispatchers.IO) {
        val (provider, providerConfig) = getActiveProvider()
            ?: return@withContext Result.failure(IllegalStateException("No active or configured cloud storage provider."))

        try {
            val cacheDir = File(context.cacheDir, "cloud_restores")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val downloadedFile = File(cacheDir, fileInfo.name)
            progressListener?.invoke(10)

            val downloadResult = provider.downloadFile(fileInfo.path, downloadedFile, providerConfig) { downProgress ->
                val mapped = 10 + ((downProgress * 60) / 100)
                progressListener?.invoke(mapped)
            }

            if (downloadResult.isFailure || !downloadedFile.exists()) {
                return@withContext Result.failure(downloadResult.exceptionOrNull() ?: Exception("Failed to download remote backup file."))
            }

            progressListener?.invoke(75)
            var importFile = downloadedFile

            // Check if encrypted
            if (fileInfo.name.endsWith(".enc") || encryptionEngine.isEncryptedBackup(downloadedFile)) {
                val passphrase = cloudCredentialStore.getE2ePassphrase()
                if (passphrase.isBlank()) {
                    downloadedFile.delete()
                    return@withContext Result.failure(IllegalArgumentException("This backup is encrypted. Please set your E2E Passphrase in Cloud Backup settings to restore."))
                }
                val decryptedFile = File(cacheDir, "${fileInfo.name.removeSuffix(".enc")}.zip")
                val decryptResult = encryptionEngine.decryptFile(downloadedFile, decryptedFile, passphrase)
                if (decryptResult.isSuccess && decryptedFile.exists()) {
                    downloadedFile.delete()
                    importFile = decryptedFile
                } else {
                    downloadedFile.delete()
                    return@withContext Result.failure(IllegalArgumentException("Failed to decrypt backup archive. Ensure your E2E passphrase matches the passphrase used to create this backup."))
                }
            }

            progressListener?.invoke(80)
            // What is here now is kept locally before it is replaced
            keepSafetyCopy()
            progressListener?.invoke(85)
            val importResult = backupImporter.importBackup(Uri.fromFile(importFile), ImportStrategy.REPLACE_ALL)
            progressListener?.invoke(100)

            // Cleanup temp files
            if (importFile.exists()) importFile.delete()

            when (importResult) {
                is ImportResult.Success -> Result.success(importResult)
                is ImportResult.Error -> Result.failure(Exception(importResult.message))
            }
        } catch (e: Exception) {
            Log.e("CloudBackupManager", "Restore error", e)
            Result.failure(e)
        }
    }

    /**
     * Saves the current data to `files/pre_restore/` before a restore replaces it, keeping the
     * last three such copies. They stay on the device only.
     */
    private suspend fun keepSafetyCopy() {
        val export = backupExporter.exportBackup(BackupConfiguration())
        if (export !is ExportResult.Success) throw IllegalStateException("Could not save the current data before restoring.")
        val dir = File(context.filesDir, "pre_restore").apply { mkdirs() }
        val copy = File(dir, "before-restore-${System.currentTimeMillis()}.zip")
        export.file.copyTo(copy, overwrite = true)
        export.file.delete()
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(3)?.forEach { it.delete() }
    }

    /**
     * Delete a remote backup snapshot.
     */
    suspend fun deleteBackup(fileInfo: CloudFileInfo): Result<Boolean> = withContext(Dispatchers.IO) {
        val (provider, providerConfig) = getActiveProvider()
            ?: return@withContext Result.failure(IllegalStateException("No active or configured cloud storage provider."))

        provider.deleteFile(fileInfo.path, providerConfig)
    }

    /**
     * Prune older backups keeping only up to maxBackups snapshots.
     */
    private suspend fun enforceRetentionPolicy(
        maxBackups: Int,
        provider: CloudStorageProvider,
        config: CloudProviderConfig
    ) {
        if (maxBackups <= 0) return
        try {
            val listResult = provider.listFiles(BACKUPS_FOLDER, config)
            if (listResult.isSuccess) {
                val files = listResult.getOrNull() ?: return
                // Only this app's own backups: old Cashiro ones are listed for restore, never pruned.
                val backups = files.filter {
                    it.name.startsWith("Hootbook_Backup_") && (it.name.endsWith(".zip") || it.name.endsWith(".enc"))
                }.sortedByDescending { it.lastModified }

                if (backups.size > maxBackups) {
                    val toDelete = backups.drop(maxBackups)
                    for (oldFile in toDelete) {
                        provider.deleteFile(oldFile.path, config)
                        Log.d("CloudBackupManager", "Retention pruned old backup: ${oldFile.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("CloudBackupManager", "Failed to enforce retention limit", e)
        }
    }
}
