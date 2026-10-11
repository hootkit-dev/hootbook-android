package dev.hootkit.platform

import android.content.Context
import dev.hootkit.core.AuthProvider
import dev.hootkit.core.CoreConfig
import dev.hootkit.core.HootKitCore
import dev.hootkit.core.RemoteStore
import dev.hootkit.core.openCore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** No Firebase dependency. A flavor-specific app binding supplies auth/remote, or both null. */
class AndroidCoreFactory(
    context: Context,
    private val changes: ChangeBus,
    private val secureStore: KeystoreSecureStore,
    private val http: OkHttpTransport,
    private val logger: LogcatLogger = LogcatLogger(),
) {
    private val context = context.applicationContext

    suspend fun open(locale: String, auth: AuthProvider? = null, remote: RemoteStore? = null): HootKitCore =
        withContext(Dispatchers.IO) {
            require((auth == null) == (remote == null)) { "Provide auth and remote together" }
            val database = context.getDatabasePath("hootkit.sqlite")
            database.parentFile?.let { ensureDirectory(it) }
            // Explicit user backups still work; Android automatic backup never copies these files.
            val root = File(context.noBackupFilesDir, "hootkit-core")
            val backups = ensureDirectory(File(root, "backups"))
            val attachments = ensureDirectory(File(root, "attachments"))
            openCore(
                CoreConfig(database.absolutePath, backups.absolutePath, attachments.absolutePath, locale, null),
                changes, logger, secureStore, http, auth, remote,
            )
        }

    private fun ensureDirectory(directory: File): File {
        check(directory.isDirectory || directory.mkdirs()) { "Core directory creation failed" }
        return directory
    }
}
