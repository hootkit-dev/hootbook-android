package io.github.hootkit_dev.hootbook.data.cloud

import java.io.File

/**
 * Interface defining operations required for cloud backup and multi-device synchronization.
 */
interface CloudStorageProvider {
    val providerType: CloudProviderType
    val providerName: String

    /**
     * Verify credentials or acquire authentication tokens
     */
    suspend fun authenticate(config: CloudProviderConfig): Result<Boolean>

    /**
     * Test if connection and authentication succeed against the provider endpoint
     */
    suspend fun testConnection(config: CloudProviderConfig): Result<Boolean>

    /**
     * Ensure that a directory (e.g. cashiro_backups) exists on the remote storage
     */
    suspend fun ensureDirectoryExists(folderPath: String, config: CloudProviderConfig): Result<Boolean>

    /**
     * List files within a remote folder path
     */
    suspend fun listFiles(folderPath: String, config: CloudProviderConfig): Result<List<CloudFileInfo>>

    /**
     * Upload a local file to the specified remote path
     */
    suspend fun uploadFile(
        localFile: File,
        remotePath: String,
        config: CloudProviderConfig,
        progressListener: ((Int) -> Unit)? = null
    ): Result<CloudFileInfo>

    /**
     * Download a remote file to a local destination file
     */
    suspend fun downloadFile(
        remotePath: String,
        localDestination: File,
        config: CloudProviderConfig,
        progressListener: ((Int) -> Unit)? = null
    ): Result<File>

    /**
     * Delete a remote file or folder
     */
    suspend fun deleteFile(remotePath: String, config: CloudProviderConfig): Result<Boolean>
}
