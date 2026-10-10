package io.github.hootkit_dev.hootbook.data.service

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for handling transaction attachment file operations.
 * Manages saving, deleting, and retrieving attachment files.
 */
@Singleton
class AttachmentService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val ATTACHMENTS_DIR = "attachments"
        private const val AVATARS_DIR = "avatars"
    }

    private val attachmentsDir: File
        get() = File(context.filesDir, ATTACHMENTS_DIR).also { 
            if (!it.exists()) it.mkdirs() 
        }

    private val avatarsDir: File
        get() = File(context.filesDir, AVATARS_DIR).also {
            if (!it.exists()) it.mkdirs()
        }

    /**
     * Save an attachment file from a content URI to internal storage.
     * @param uri The content URI of the file to save
     * @param transactionId The transaction ID this attachment belongs to
     * @return The relative path of the saved file, or null if save failed
     */
    fun saveAttachment(uri: Uri, transactionId: Long): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val mimeType = context.contentResolver.getType(uri)
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "bin"
            
            // Generate unique filename: transactionId_uuid.extension
            val fileName = "${transactionId}_${UUID.randomUUID()}.$extension"
            val outputFile = File(attachmentsDir, fileName)
            
            FileOutputStream(outputFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            inputStream.close()
            
            // Return relative path for storage in database
            "$ATTACHMENTS_DIR/$fileName"
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Save a raw byte array as an attachment file in internal storage.
     * Used when an attachment is downloaded (e.g. from a backup import) rather
     * than picked from a content URI.
     * @param data The raw file bytes
     * @param mimeType The MIME type of the file
     * @return The relative path of the saved file, or null if save failed
     */
    fun saveAttachmentBytes(data: ByteArray, mimeType: String): String? {
        return try {
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "bin"
            val fileName = "${System.currentTimeMillis()}_${UUID.randomUUID()}.$extension"
            val outputFile = File(attachmentsDir, fileName)
            FileOutputStream(outputFile).use { outputStream ->
                outputStream.write(data)
            }
            "$ATTACHMENTS_DIR/$fileName"
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Delete an attachment file from internal storage.
     * @param relativePath The relative path of the file to delete
     * @return true if deletion was successful
     */
    fun deleteAttachment(relativePath: String): Boolean {
        return try {
            val file = File(context.filesDir, relativePath)
            if (file.exists()) file.delete() else true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Get a content URI for viewing an attachment file.
     * Uses FileProvider for secure access.
     * @param relativePath The relative path of the file
     * @return Content URI for the file, or null if file doesn't exist
     */
    fun getAttachmentUri(relativePath: String): Uri? {
        return try {
            val file = File(context.filesDir, relativePath)
            if (file.exists()) {
                val authority = "${context.packageName}.fileprovider"
                FileProvider.getUriForFile(context, authority, file)
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Get the MIME type of an attachment file.
     * @param relativePath The relative path of the file
     * @return MIME type string, or "application/octet-stream" as fallback
     */
    fun getAttachmentMimeType(relativePath: String): String {
        val extension = relativePath.substringAfterLast('.', "")
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }

    /**
     * Check if a file is an image based on its MIME type.
     * @param relativePath The relative path of the file
     * @return true if the file is an image
     */
    fun isImage(relativePath: String): Boolean {
        return getAttachmentMimeType(relativePath).startsWith("image/")
    }

    /**
     * Check if the attachment path is a remote URL.
     * @param path The path or URL
     * @return true if it starts with http:// or https://
     */
    fun isUrl(path: String): Boolean {
        return path.startsWith("http://") || path.startsWith("https://")
    }

    /**
     * Parse comma-separated attachment paths into a list.
     * @param attachments The comma-separated string from database
     * @return List of attachment paths
     */
    fun parseAttachments(attachments: String): List<String> {
        return if (attachments.isBlank()) emptyList()
        else attachments.split(",").filter { it.isNotBlank() }
    }

    /**
     * Join attachment paths into a comma-separated string for database storage.
     * @param paths List of attachment paths
     * @return Comma-separated string
     */
    fun joinAttachments(paths: List<String>): String {
        return paths.joinToString(",")
    }

    /**
     * Persist a person avatar image into app-internal storage.
     *
     * The picked image is copied from its transient content:// Uri into the app's
     * avatars directory. A file:// Uri into internal storage stays valid across
     * app updates and reinstalls, unlike a content:// image pick.
     * @param contentUri The content Uri picked from the gallery
     * @return The file:: Uri string of the saved avatar, or null if save failed
     */
    fun persistAvatar(contentUri: Uri): String? {
        return saveAvatar(contentUri)
    }

    /**
     * Save an avatar file from a content Uri into the avatars directory.
     * @param uri The content Uri of the avatar
     * @return The file:// Uri string of the saved avatar, or null if save failed
     */
    fun saveAvatar(uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val mimeType = context.contentResolver.getType(uri)
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"

            val fileName = "avatar_${UUID.randomUUID()}.$extension"
            val outputFile = File(avatarsDir, fileName)

            FileOutputStream(outputFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            inputStream.close()

            Uri.fromFile(outputFile).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Check whether a stored avatar value points to a file inside the internal
     * avatars directory.
     * @param avatarValue The stored avatar value (file:// Uri string)
     * @return true if the avatar refers to an internal avatar file
     */
    fun isInternalAvatar(avatarValue: String?): Boolean {
        if (avatarValue.isNullOrBlank()) return false
        return try {
            val uri = Uri.parse(avatarValue)
            if (uri.scheme != "file") return false
            val path = uri.path ?: return false
            path.startsWith(avatarsDir.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Delete an internal avatar file.
     * @param avatarValue The stored avatar value (file:// Uri string)
     * @return true if the file was deleted (or nothing to delete)
     */
    fun deleteAvatar(avatarValue: String?): Boolean {
        if (!isInternalAvatar(avatarValue)) return true
        return try {
            val uri = Uri.parse(avatarValue)
            val file = File(uri.path ?: return true)
            !file.exists() || file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

}
