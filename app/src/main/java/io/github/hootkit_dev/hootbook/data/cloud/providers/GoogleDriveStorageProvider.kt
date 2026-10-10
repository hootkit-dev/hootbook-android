package io.github.hootkit_dev.hootbook.data.cloud.providers

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.github.hootkit_dev.hootbook.data.cloud.CloudFileInfo
import io.github.hootkit_dev.hootbook.data.cloud.CloudProviderConfig
import io.github.hootkit_dev.hootbook.data.cloud.CloudProviderType
import io.github.hootkit_dev.hootbook.data.cloud.CloudStorageProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [CloudStorageProvider] for Google Drive using the Drive REST API v3
 * targeting the secure appDataFolder space.
 *
 * Automatically refreshes OAuth tokens via [GoogleAuthUtil] when the account is registered
 * on the device, falling back to the stored access token if unavailable.
 */
@Singleton
class GoogleDriveStorageProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : CloudStorageProvider {

    override val providerType = CloudProviderType.GOOGLE_DRIVE
    override val providerName = CloudProviderType.GOOGLE_DRIVE.displayName

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    companion object {
        private const val DRIVE_API_BASE = "https://www.googleapis.com/drive/v3"
        private const val DRIVE_UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"
        private const val SPACE_APP_DATA = "appDataFolder"
        private const val DRIVE_SCOPE = "oauth2:https://www.googleapis.com/auth/drive.appdata"
    }

    /**
     * Resolves a fresh OAuth 2.0 access token for the configured Google account.
     *
     * Uses [GoogleAuthUtil] to obtain a cached or newly-refreshed token when the
     * account is registered on the device. Falls back to the stored [CloudProviderConfig.GoogleDriveConfig.accessToken]
     * if the account is unavailable or Google Play Services is not present.
     */
    private suspend fun resolveAccessToken(config: CloudProviderConfig): String {
        val driveConfig = config as CloudProviderConfig.GoogleDriveConfig
        if (driveConfig.accountEmail.isNotBlank()) {
            try {
                return GoogleAuthUtil.getToken(
                    context,
                    driveConfig.accountEmail,
                    DRIVE_SCOPE
                )
            } catch (_: Exception) {
                // GoogleAuthUtil may fail when the account is removed, Play Services is
                // unavailable, or there is no network. Fall through to the stored token.
            }
        }
        return driveConfig.accessToken
    }

    override suspend fun authenticate(config: CloudProviderConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        val driveConfig = config as? CloudProviderConfig.GoogleDriveConfig
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid config type for Google Drive"))

        val token = resolveAccessToken(config)
        if (token.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Google Drive access token is missing or invalid"))
        }

        try {
            val request = Request.Builder()
                .url("$DRIVE_API_BASE/about?fields=user")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("Google Drive token invalid or expired: HTTP ${response.code} ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveProvider", "Authentication error", e)
            Result.failure(e)
        }
    }

    override suspend fun testConnection(config: CloudProviderConfig): Result<Boolean> {
        return authenticate(config)
    }

    override suspend fun ensureDirectoryExists(folderPath: String, config: CloudProviderConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        val driveConfig = config as? CloudProviderConfig.GoogleDriveConfig
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid config for Google Drive"))

        try {
            val token = resolveAccessToken(config)
            val folderId = getOrCreateFolderId(folderPath, token)
            if (folderId != null) Result.success(true)
            else Result.failure(Exception("Failed to ensure directory exists in Google Drive"))
        } catch (e: Exception) {
            Log.e("GoogleDriveProvider", "Ensure directory error", e)
            Result.failure(e)
        }
    }

    override suspend fun listFiles(folderPath: String, config: CloudProviderConfig): Result<List<CloudFileInfo>> = withContext(Dispatchers.IO) {
        val driveConfig = config as? CloudProviderConfig.GoogleDriveConfig
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid config for Google Drive"))

        try {
            val token = resolveAccessToken(config)
            val parentId = getOrCreateFolderId(folderPath, token)
                ?: return@withContext Result.failure(Exception("Could not find or create folder $folderPath"))

            val query = "'$parentId' in parents and trashed = false and mimeType != 'application/vnd.google-apps.folder'"
            val request = Request.Builder()
                .url("$DRIVE_API_BASE/files?q=${java.net.URLEncoder.encode(query, "UTF-8")}&spaces=$SPACE_APP_DATA&fields=files(id,name,size,modifiedTime)")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful || response.body == null) {
                    return@withContext Result.failure(Exception("Failed to list Google Drive files: HTTP ${response.code}"))
                }

                val jsonStr = response.body!!.string()
                val jsonObj = gson.fromJson(jsonStr, JsonObject::class.java)
                val filesArray = jsonObj.getAsJsonArray("files")
                val resultList = mutableListOf<CloudFileInfo>()

                if (filesArray != null) {
                    for (element in filesArray) {
                        val fileObj = element.asJsonObject
                        val id = fileObj.get("id")?.asString ?: continue
                        val name = fileObj.get("name")?.asString ?: "Unknown"
                        val size = fileObj.get("size")?.asLong ?: 0L
                        val modifiedTimeStr = fileObj.get("modifiedTime")?.asString
                        val lastModified = try {
                            if (modifiedTimeStr != null) Instant.parse(modifiedTimeStr).toEpochMilli()
                            else System.currentTimeMillis()
                        } catch (e: Exception) {
                            System.currentTimeMillis()
                        }

                        resultList.add(
                            CloudFileInfo(
                                id = id,
                                name = name,
                                path = id, // For Drive, path stores the unique fileId
                                size = size,
                                lastModified = lastModified,
                                providerType = CloudProviderType.GOOGLE_DRIVE
                            )
                        )
                    }
                }
                Result.success(resultList)
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveProvider", "List files error", e)
            Result.failure(e)
        }
    }

    override suspend fun uploadFile(
        localFile: File,
        remotePath: String,
        config: CloudProviderConfig,
        progressListener: ((Int) -> Unit)?
    ): Result<CloudFileInfo> = withContext(Dispatchers.IO) {
        val driveConfig = config as? CloudProviderConfig.GoogleDriveConfig
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid config for Google Drive"))

        if (!localFile.exists()) {
            return@withContext Result.failure(Exception("Local file does not exist: ${localFile.path}"))
        }

        try {
            val token = resolveAccessToken(config)
            val folderPath = remotePath.substringBeforeLast("/", "")
            val fileName = localFile.name
            val parentId = if (folderPath.isNotEmpty()) {
                getOrCreateFolderId(folderPath, token) ?: SPACE_APP_DATA
            } else {
                SPACE_APP_DATA
            }

            // Check if file already exists in this folder to overwrite (update) or create new
            val existingFileId = findFileByNameInFolder(fileName, parentId, token)

            progressListener?.invoke(10)

            val fileInfo = if (existingFileId != null) {
                // Update existing file content
                updateExistingFile(existingFileId, fileName, localFile, token)
            } else {
                // Create new multipart upload
                createNewFile(fileName, parentId, localFile, token)
            }

            progressListener?.invoke(100)
            if (fileInfo != null) Result.success(fileInfo)
            else Result.failure(Exception("Failed to upload file to Google Drive"))
        } catch (e: Exception) {
            Log.e("GoogleDriveProvider", "Upload error", e)
            Result.failure(e)
        }
    }

    override suspend fun downloadFile(
        remotePath: String,
        localDestination: File,
        config: CloudProviderConfig,
        progressListener: ((Int) -> Unit)?
    ): Result<File> = withContext(Dispatchers.IO) {
        val driveConfig = config as? CloudProviderConfig.GoogleDriveConfig
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid config for Google Drive"))

        try {
            val token = resolveAccessToken(config)
            val fileId = remotePath // For Google Drive, remotePath holds the file ID
            val request = Request.Builder()
                .url("$DRIVE_API_BASE/files/$fileId?alt=media")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            progressListener?.invoke(10)
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful || response.body == null) {
                    return@withContext Result.failure(Exception("Failed to download Google Drive file: HTTP ${response.code}"))
                }

                val body = response.body!!
                val contentLength = body.contentLength()
                val inputStream = body.byteStream()

                localDestination.parentFile?.mkdirs()
                FileOutputStream(localDestination).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        if (contentLength > 0 && progressListener != null) {
                            val progress = ((totalBytesRead * 100) / contentLength).toInt().coerceIn(10, 95)
                            progressListener.invoke(progress)
                        }
                    }
                }
                progressListener?.invoke(100)
                Result.success(localDestination)
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveProvider", "Download error", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteFile(remotePath: String, config: CloudProviderConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        val driveConfig = config as? CloudProviderConfig.GoogleDriveConfig
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid config for Google Drive"))

        try {
            val token = resolveAccessToken(config)
            val fileId = remotePath
            val request = Request.Builder()
                .url("$DRIVE_API_BASE/files/$fileId")
                .header("Authorization", "Bearer $token")
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code in 200..299 || response.code == 404) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("Failed to delete Google Drive file: HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveProvider", "Delete error", e)
            Result.failure(e)
        }
    }

    private fun getOrCreateFolderId(folderName: String, accessToken: String): String? {
        // Look up folder in appDataFolder
        val query = "mimeType = 'application/vnd.google-apps.folder' and name = '$folderName' and '$SPACE_APP_DATA' in parents and trashed = false"
        val listReq = Request.Builder()
            .url("$DRIVE_API_BASE/files?q=${java.net.URLEncoder.encode(query, "UTF-8")}&spaces=$SPACE_APP_DATA&fields=files(id)")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        try {
            client.newCall(listReq).execute().use { response ->
                if (response.isSuccessful && response.body != null) {
                    val jsonObj = gson.fromJson(response.body!!.string(), JsonObject::class.java)
                    val filesArray = jsonObj.getAsJsonArray("files")
                    if (filesArray != null && filesArray.size() > 0) {
                        return filesArray.get(0).asJsonObject.get("id").asString
                    }
                }
            }

            // Create if not found
            val metadataJson = """
                {
                    "name": "$folderName",
                    "mimeType": "application/vnd.google-apps.folder",
                    "parents": ["$SPACE_APP_DATA"]
                }
            """.trimIndent()

            val createReq = Request.Builder()
                .url("$DRIVE_API_BASE/files?fields=id")
                .header("Authorization", "Bearer $accessToken")
                .post(metadataJson.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            client.newCall(createReq).execute().use { response ->
                if (response.isSuccessful && response.body != null) {
                    val jsonObj = gson.fromJson(response.body!!.string(), JsonObject::class.java)
                    return jsonObj.get("id")?.asString
                }
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveProvider", "Folder lookup/create failed", e)
        }
        return null
    }

    private fun findFileByNameInFolder(fileName: String, parentId: String, accessToken: String): String? {
        val query = "name = '$fileName' and '$parentId' in parents and trashed = false"
        val request = Request.Builder()
            .url("$DRIVE_API_BASE/files?q=${java.net.URLEncoder.encode(query, "UTF-8")}&spaces=$SPACE_APP_DATA&fields=files(id)")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful && response.body != null) {
                    val jsonObj = gson.fromJson(response.body!!.string(), JsonObject::class.java)
                    val filesArray = jsonObj.getAsJsonArray("files")
                    if (filesArray != null && filesArray.size() > 0) {
                        return filesArray.get(0).asJsonObject.get("id").asString
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveProvider", "Find file failed", e)
        }
        return null
    }

    private fun createNewFile(fileName: String, parentId: String, localFile: File, accessToken: String): CloudFileInfo? {
        val metadataJson = """
            {
                "name": "$fileName",
                "parents": ["$parentId"]
            }
        """.trimIndent()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("metadata", null, metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaTypeOrNull()))
            .addFormDataPart("file", fileName, localFile.asRequestBody("application/octet-stream".toMediaTypeOrNull()))
            .build()

        val request = Request.Builder()
            .url("$DRIVE_UPLOAD_BASE/files?uploadType=multipart&fields=id,name,size,modifiedTime")
            .header("Authorization", "Bearer $accessToken")
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful && response.body != null) {
                    val jsonObj = gson.fromJson(response.body!!.string(), JsonObject::class.java)
                    val id = jsonObj.get("id")?.asString ?: return null
                    return CloudFileInfo(
                        id = id,
                        name = fileName,
                        path = id,
                        size = localFile.length(),
                        lastModified = System.currentTimeMillis(),
                        providerType = CloudProviderType.GOOGLE_DRIVE
                    )
                } else {
                    Log.e("GoogleDriveProvider", "Create file error: HTTP ${response.code} ${response.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveProvider", "Create file exception", e)
        }
        return null
    }

    private fun updateExistingFile(fileId: String, fileName: String, localFile: File, accessToken: String): CloudFileInfo? {
        val requestBody = localFile.asRequestBody("application/octet-stream".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("$DRIVE_UPLOAD_BASE/files/$fileId?uploadType=media&fields=id,name,size,modifiedTime")
            .header("Authorization", "Bearer $accessToken")
            .patch(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful && response.body != null) {
                    return CloudFileInfo(
                        id = fileId,
                        name = fileName,
                        path = fileId,
                        size = localFile.length(),
                        lastModified = System.currentTimeMillis(),
                        providerType = CloudProviderType.GOOGLE_DRIVE
                    )
                } else {
                    Log.e("GoogleDriveProvider", "Update file error: HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveProvider", "Update file exception", e)
        }
        return null
    }
}
