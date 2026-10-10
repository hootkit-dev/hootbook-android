package io.github.hootkit_dev.hootbook.data.backup

import android.accounts.AccountManager
import android.content.Context
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.github.hootkit_dev.hootbook.data.cloud.security.CloudCredentialStore
import io.github.hootkit_dev.hootbook.data.service.AttachmentService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads Cashew attachment files.
 *
 * Cashew stores transaction attachments as Google Drive links embedded inside
 * the transaction's `note` text. On import those files are downloaded (using the
 * user's Google account so private receipts work) and saved into the app's local
 * attachment storage. If no Google account/token is available the public
 * download endpoint is tried as a fallback for files that were shared publicly.
 */
@Singleton
class CashewAttachmentImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val attachmentService: AttachmentService,
    private val cloudCredentialStore: CloudCredentialStore
) {

    private val tag = "CashewAttachmentImporter"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // Cache of downloaded fileId -> relative path to avoid duplicate downloads
    // when the same attachment is referenced by several transactions.
    private val downloadedPaths = mutableMapOf<String, String>()

    companion object {
        private const val DRIVE_API_BASE = "https://www.googleapis.com/drive/v3"
        private const val DRIVE_READONLY_SCOPE = "oauth2:https://www.googleapis.com/auth/drive.readonly"
        private const val PUBLIC_DOWNLOAD_BASE = "https://drive.google.com/uc?export=download"
    }

    data class AttachmentImportResult(
        val savedPaths: List<String>,
        val linkCount: Int
    )

    /**
     * Extracts any Google Drive attachment links from the given Cashew note and
     * downloads them into local attachment storage.
     *
     * @param note The Cashew transaction note (may contain Drive links)
     * @param token A resolved Drive OAuth token, or null to fall back to public download
     * @return The downloaded attachment paths plus how many links were found in total
     */
    suspend fun importAttachmentsFromNote(note: String?, token: String?): AttachmentImportResult = withContext(Dispatchers.IO) {
        if (note.isNullOrBlank()) return@withContext AttachmentImportResult(emptyList(), 0)

        val fileIds = extractDriveFileIds(note)
        if (fileIds.isEmpty()) return@withContext AttachmentImportResult(emptyList(), 0)

        val savedPaths = mutableListOf<String>()
        fileIds.forEach { fileId ->
            val cached = downloadedPaths[fileId]
            if (cached != null) {
                if (File(context.filesDir, cached).exists()) {
                    savedPaths.add(cached)
                } else {
                    downloadedPaths.remove(fileId)
                }
                return@forEach
            }

            val path = if (token != null) {
                // Authenticated access first (handles private receipts), then fall
                // back to the public endpoint in case consent/token is insufficient.
                downloadFromDriveApi(fileId, token) ?: downloadFromPublic(fileId)
            } else {
                downloadFromPublic(fileId)
            }

            if (path != null) {
                downloadedPaths[fileId] = path
                savedPaths.add(path)
            } else {
                // Fallback: If we couldn't download the file (e.g. private or too large),
                // we store the original Drive link. This allows the user to still
                // access the file by clicking it (opens in browser).
                val driveLink = "https://drive.google.com/open?id=$fileId"
                savedPaths.add(driveLink)
            }
        }
        AttachmentImportResult(savedPaths.distinct(), fileIds.size)
    }

    /**
     * Resolves a Google account email that owns the Cashew Drive files.
     * Prefers the account configured for Google Drive backup, then the last
     * signed-in account, then any Google account registered on the device.
     */
    fun resolveGoogleEmail(): String? {
        cloudCredentialStore.getGoogleDriveConfig().accountEmail
            .takeIf { it.isNotBlank() }
            ?.let { return it }

        GoogleSignIn.getLastSignedInAccount(context)?.email
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return try {
            AccountManager.get(context)
                .getAccountsByType("com.google")
                .firstOrNull { it.name.contains("@") }?.name
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Resolves an OAuth token for Drive attachment downloads.
     * Returns null when no account is available or consent is required.
     */
    fun resolveDriveToken(): String? {
        val email = resolveGoogleEmail() ?: return null
        return try {
            GoogleAuthUtil.getToken(context, email, DRIVE_READONLY_SCOPE)
        } catch (e: UserRecoverableAuthException) {
            Log.w(tag, "Drive attachment access requires user consent: ${e.message}")
            null
        } catch (e: Exception) {
            Log.w(tag, "Failed to obtain Google Drive token", e)
            null
        }
    }

    private fun extractDriveFileIds(note: String): List<String> {
        val ids = mutableSetOf<String>()
        // drive.google.com/file/d/{id}/view?usp=sharing
        Regex("""drive\.google\.com/file/d/([A-Za-z0-9_-]{6,})""")
            .findAll(note)
            .forEach { match -> match.groupValues[1].let { ids.add(it) } }
        // drive.google.com/open?id={id} / docs.google.com/uc?...&id={id} form
        Regex("""(?:drive\.google\.com|docs\.google\.com|googleusercontent\.com)[^\s"'<>]*?[?&]id=([A-Za-z0-9_-]{6,})""")
            .findAll(note)
            .forEach { match -> match.groupValues[1].let { ids.add(it) } }
        return ids.toList()
    }

    private fun downloadFromDriveApi(fileId: String, token: String): String? {
        return try {
            val metadataRequest = Request.Builder()
                .url("$DRIVE_API_BASE/files/$fileId?fields=id,name,mimeType")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            val mimeType = client.newCall(metadataRequest).execute().use { response ->
                if (!response.isSuccessful || response.body == null) return null
                val obj = gson.fromJson(response.body!!.string(), JsonObject::class.java)
                obj.get("mimeType")?.asString ?: "application/octet-stream"
            }

            val mediaRequest = Request.Builder()
                .url("$DRIVE_API_BASE/files/$fileId?alt=media")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(mediaRequest).execute().use { response ->
                if (!response.isSuccessful || response.body == null) return null
                val bytes = response.body!!.bytes()
                if (bytes.isEmpty()) return null

                // Double check we didn't get an HTML error page even via API
                val contentType = response.body!!.contentType()
                if (contentType?.subtype?.contains("html", ignoreCase = true) == true) {
                    Log.w(tag, "Drive API returned HTML for $fileId media request. Skipping.")
                    return null
                }

                attachmentService.saveAttachmentBytes(bytes, mimeType)
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to download Drive file $fileId via API", e)
            null
        }
    }

    private fun downloadFromPublic(fileId: String): String? {
        return try {
            val request = Request.Builder()
                .url("$PUBLIC_DOWNLOAD_BASE&id=$fileId")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful || response.body == null) return null
                val body = response.body!!
                val bytes = body.bytes()
                if (bytes.isEmpty()) return null

                val contentType = body.contentType()
                val mime = contentType?.let { "${it.type}/${it.subtype}" }
                    ?: "application/octet-stream"

                // Google can respond with an HTML "virus scan" interstitial page or login page
                // instead of the file. Detect that and bail to avoid saving garbage.
                if (mime.contains("html", ignoreCase = true)) {
                    val contentSample = if (bytes.size > 2048) bytes.sliceArray(0 until 2048) else bytes
                    val sampleStr = contentSample.decodeToString()
                    if (sampleStr.contains("google", ignoreCase = true) || 
                        sampleStr.trimStart().startsWith("<html", ignoreCase = true)) {
                        Log.w(tag, "Public download for $fileId returned HTML (likely login/interstitial). Skipping.")
                        return null
                    }
                }

                attachmentService.saveAttachmentBytes(bytes, mime)
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to download Drive file $fileId publicly", e)
            null
        }
    }
}