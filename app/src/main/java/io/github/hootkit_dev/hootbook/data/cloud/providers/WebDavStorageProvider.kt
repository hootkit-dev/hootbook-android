package io.github.hootkit_dev.hootbook.data.cloud.providers

import android.util.Log
import io.github.hootkit_dev.hootbook.data.cloud.CloudFileInfo
import io.github.hootkit_dev.hootbook.data.cloud.CloudProviderConfig
import io.github.hootkit_dev.hootbook.data.cloud.CloudProviderType
import io.github.hootkit_dev.hootbook.data.cloud.CloudStorageProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [CloudStorageProvider] for Nextcloud, OwnCloud, and generic WebDAV servers.
 */
@Singleton
class WebDavStorageProvider @Inject constructor() : CloudStorageProvider {

    override val providerType = CloudProviderType.WEBDAV
    override val providerName = CloudProviderType.WEBDAV.displayName

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    override suspend fun authenticate(config: CloudProviderConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        val webDavConfig = config as? CloudProviderConfig.WebDavConfig
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid config type for WebDAV"))

        if (!webDavConfig.isConfigured) {
            return@withContext Result.failure(IllegalArgumentException("WebDAV credentials not fully configured"))
        }

        try {
            val url = cleanUrl(webDavConfig.url)
            val request = Request.Builder()
                .url(url)
                .method("PROPFIND", "".toRequestBody("application/xml".toMediaTypeOrNull()))
                .header("Depth", "0")
                .header("Authorization", buildAuthHeader(webDavConfig))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code in 200..299 || response.code == 207) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("Authentication failed: HTTP ${response.code} ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Log.e("WebDavProvider", "Authentication error", e)
            Result.failure(e)
        }
    }

    override suspend fun testConnection(config: CloudProviderConfig): Result<Boolean> {
        return authenticate(config)
    }

    override suspend fun ensureDirectoryExists(folderPath: String, config: CloudProviderConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        val webDavConfig = config as? CloudProviderConfig.WebDavConfig
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid config for WebDAV"))

        val folderUrl = buildFullUrl(webDavConfig.url, folderPath).let { if (it.endsWith("/")) it else "$it/" }
        val authHeader = buildAuthHeader(webDavConfig)

        try {
            // First check if directory already exists
            val propfindRequest = Request.Builder()
                .url(folderUrl)
                .method("PROPFIND", "".toRequestBody("application/xml".toMediaTypeOrNull()))
                .header("Depth", "0")
                .header("Authorization", authHeader)
                .build()

            client.newCall(propfindRequest).execute().use { response ->
                if (response.code in 200..299 || response.code == 207) {
                    return@withContext Result.success(true)
                }
            }

            // Execute MKCOL if not found
            val mkcolRequest = Request.Builder()
                .url(folderUrl)
                .method("MKCOL", null)
                .header("Authorization", authHeader)
                .build()

            client.newCall(mkcolRequest).execute().use { response ->
                if (response.code == 201 || response.code == 405 || response.code in 200..299) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("Could not create directory $folderPath: HTTP ${response.code} ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Log.e("WebDavProvider", "Ensure directory error", e)
            Result.failure(e)
        }
    }

    override suspend fun listFiles(folderPath: String, config: CloudProviderConfig): Result<List<CloudFileInfo>> = withContext(Dispatchers.IO) {
        val webDavConfig = config as? CloudProviderConfig.WebDavConfig
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid config for WebDAV"))

        val folderUrl = buildFullUrl(webDavConfig.url, folderPath).let { if (it.endsWith("/")) it else "$it/" }
        val authHeader = buildAuthHeader(webDavConfig)

        try {
            val requestBody = """
                <?xml version="1.0" encoding="utf-8" ?>
                <d:propfind xmlns:d="DAV:">
                    <d:prop>
                        <d:displayname/>
                        <d:getlastmodified/>
                        <d:getcontentlength/>
                        <d:resourcetype/>
                    </d:prop>
                </d:propfind>
            """.trimIndent().toRequestBody("application/xml; charset=utf-8".toMediaTypeOrNull())

            val request = Request.Builder()
                .url(folderUrl)
                .method("PROPFIND", requestBody)
                .header("Depth", "1")
                .header("Authorization", authHeader)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code != 207 && response.code !in 200..299) {
                    return@withContext Result.failure(Exception("Failed to list files: HTTP ${response.code} ${response.message}"))
                }

                val xmlContent = response.body?.string() ?: ""
                val files = parseWebDavMultiStatus(xmlContent, folderUrl)
                Result.success(files)
            }
        } catch (e: Exception) {
            Log.e("WebDavProvider", "List files error", e)
            Result.failure(e)
        }
    }

    override suspend fun uploadFile(
        localFile: File,
        remotePath: String,
        config: CloudProviderConfig,
        progressListener: ((Int) -> Unit)?
    ): Result<CloudFileInfo> = withContext(Dispatchers.IO) {
        val webDavConfig = config as? CloudProviderConfig.WebDavConfig
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid config for WebDAV"))

        if (!localFile.exists()) {
            return@withContext Result.failure(FileNotExistsException("Local file does not exist: ${localFile.path}"))
        }

        // Ensure parent folder exists
        val parentFolder = remotePath.substringBeforeLast("/", "")
        if (parentFolder.isNotEmpty()) {
            ensureDirectoryExists(parentFolder, config)
        }

        val fileUrl = buildFullUrl(webDavConfig.url, remotePath)
        val authHeader = buildAuthHeader(webDavConfig)

        try {
            val totalSize = localFile.length()
            // Using standard RequestBody with simple reporting
            val fileBody = localFile.asRequestBody("application/octet-stream".toMediaTypeOrNull())

            val request = Request.Builder()
                .url(fileUrl)
                .put(fileBody)
                .header("Authorization", authHeader)
                .build()

            progressListener?.invoke(10) // Start
            client.newCall(request).execute().use { response ->
                progressListener?.invoke(100) // Finished
                if (response.code in 200..299 || response.code == 201 || response.code == 204) {
                    val fileInfo = CloudFileInfo(
                        id = remotePath,
                        name = localFile.name,
                        path = remotePath,
                        size = totalSize,
                        lastModified = System.currentTimeMillis(),
                        providerType = CloudProviderType.WEBDAV
                    )
                    Result.success(fileInfo)
                } else {
                    Result.failure(Exception("Failed to upload file: HTTP ${response.code} ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Log.e("WebDavProvider", "Upload error", e)
            Result.failure(e)
        }
    }

    override suspend fun downloadFile(
        remotePath: String,
        localDestination: File,
        config: CloudProviderConfig,
        progressListener: ((Int) -> Unit)?
    ): Result<File> = withContext(Dispatchers.IO) {
        val webDavConfig = config as? CloudProviderConfig.WebDavConfig
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid config for WebDAV"))

        val fileUrl = buildFullUrl(webDavConfig.url, remotePath)
        val authHeader = buildAuthHeader(webDavConfig)

        try {
            val request = Request.Builder()
                .url(fileUrl)
                .get()
                .header("Authorization", authHeader)
                .build()

            progressListener?.invoke(5)
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful || response.body == null) {
                    return@withContext Result.failure(Exception("Failed to download file: HTTP ${response.code} ${response.message}"))
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
                            val progress = ((totalBytesRead * 100) / contentLength).toInt().coerceIn(5, 95)
                            progressListener.invoke(progress)
                        }
                    }
                }
                progressListener?.invoke(100)
                Result.success(localDestination)
            }
        } catch (e: Exception) {
            Log.e("WebDavProvider", "Download error", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteFile(remotePath: String, config: CloudProviderConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        val webDavConfig = config as? CloudProviderConfig.WebDavConfig
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid config for WebDAV"))

        val fileUrl = buildFullUrl(webDavConfig.url, remotePath)
        val authHeader = buildAuthHeader(webDavConfig)

        try {
            val request = Request.Builder()
                .url(fileUrl)
                .delete()
                .header("Authorization", authHeader)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code in 200..299 || response.code == 404) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("Failed to delete remote file: HTTP ${response.code} ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Log.e("WebDavProvider", "Delete error", e)
            Result.failure(e)
        }
    }

    private fun buildAuthHeader(config: CloudProviderConfig.WebDavConfig): String {
        return if (config.username.isNotBlank() && config.passwordOrToken.isNotBlank()) {
            Credentials.basic(config.username, config.passwordOrToken)
        } else if (config.passwordOrToken.isNotBlank()) {
            "Bearer ${config.passwordOrToken}"
        } else {
            ""
        }
    }

    private fun cleanUrl(baseUrl: String): String {
        return baseUrl.trim().removeSuffix("/")
    }

    private fun buildFullUrl(baseUrl: String, relativePath: String): String {
        val cleanBase = cleanUrl(baseUrl)
        val cleanRelative = relativePath.trim().removePrefix("/")
        return "$cleanBase/$cleanRelative"
    }

    private fun parseWebDavMultiStatus(xmlContent: String, folderUrl: String): List<CloudFileInfo> {
        val results = mutableListOf<CloudFileInfo>()
        // Simple regex parser to extract response blocks without heavy XML/SAX overhead
        val responseBlocks = xmlContent.split(Regex("(?i)<[a-z0-9_-]*:?response>"))
        val rfc1123Format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)

        for (block in responseBlocks) {
            if (block.isBlank() || !block.contains(Regex("(?i)</[a-z0-9_-]*:?response>"))) continue

            // Extract href
            val hrefMatch = Regex("(?i)<[a-z0-9_-]*:?href>([^<]+)</[a-z0-9_-]*:?href>").find(block)
            val href = hrefMatch?.groupValues?.getOrNull(1)?.trim() ?: continue

            // Ignore self/directory reference
            if (href.endsWith("/") || folderUrl.endsWith(href)) continue

            // Check if directory (collection)
            val isCollection = block.contains(Regex("(?i)<[a-z0-9_-]*:?collection\\s*/>"))
            if (isCollection) continue

            // Extract display name or filename from href
            val displayNameMatch = Regex("(?i)<[a-z0-9_-]*:?displayname>([^<]+)</[a-z0-9_-]*:?displayname>").find(block)
            val name = displayNameMatch?.groupValues?.getOrNull(1)?.trim()
                ?: href.substringAfterLast("/").replace("%20", " ")

            if (name.isBlank() || name == "." || name == "..") continue

            // Extract size
            val sizeMatch = Regex("(?i)<[a-z0-9_-]*:?getcontentlength>([^<]+)</[a-z0-9_-]*:?getcontentlength>").find(block)
            val size = sizeMatch?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L

            // Extract modified time
            val timeMatch = Regex("(?i)<[a-z0-9_-]*:?getlastmodified>([^<]+)</[a-z0-9_-]*:?getlastmodified>").find(block)
            val timeStr = timeMatch?.groupValues?.getOrNull(1)?.trim()
            val lastModified = try {
                if (timeStr != null) rfc1123Format.parse(timeStr)?.time ?: System.currentTimeMillis()
                else System.currentTimeMillis()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }

            results.add(
                CloudFileInfo(
                    id = href,
                    name = name,
                    path = href,
                    size = size,
                    lastModified = lastModified,
                    providerType = CloudProviderType.WEBDAV
                )
            )
        }
        return results
    }

    class FileNotExistsException(message: String) : Exception(message)
}
