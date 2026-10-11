package io.github.hootkit_dev.hootbook.core.sync

import android.content.Context
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class CoreIdentityDecision { ACCEPTED, DIFFERENT_UID_BLOCKED, MISSING_IDENTITY_BLOCKED, STORAGE_FAILED }
/** Installation login restriction, NOT evidence of ledger ownership or first-sync completion. */
@Singleton
class CoreSyncIdentityStore private constructor(private val file: File, ledgerWasAbsentBeforeCoreOpened: Boolean) {
    // Both flavor capabilities construct this before CoreSession.open. Only filesystem
    // existence is observed, never SQL, cursor/count, or private Core sync state.
    @Inject constructor(@ApplicationContext context: Context) : this(
        File(context.noBackupFilesDir, "hootkit-core/sync-platform/install-auth.json"),
        !context.getDatabasePath("hootkit.sqlite").exists())
    internal constructor(file: File, freshLedger: Boolean, @Suppress("UNUSED_PARAMETER") test: Unit) : this(file, freshLedger)
    private val gate = Mutex()
    private var initializationFailed = false
    init {
        // Durably remember the fresh-install observation even if the first session stays
        // offline. Later cold starts can bind once; absent/corrupt witnesses are never inferred.
        try {
            if (ledgerWasAbsentBeforeCoreOpened && !file.exists() && !File(file.path + ".bak").exists()) {
                persist(JSONObject().put("schema", 1).put("freshBeforeCoreOpen", true).put("boundUid", JSONObject.NULL))
            }
        } catch (_: Exception) { initializationFailed = true }
    }
    suspend fun accept(uid: String): CoreIdentityDecision = withContext(Dispatchers.IO) { gate.withLock {
        if (uid.isBlank() || '/' in uid || initializationFailed) return@withLock CoreIdentityDecision.STORAGE_FAILED
        try {
            if (!file.exists() && !File(file.path + ".bak").exists()) return@withLock CoreIdentityDecision.MISSING_IDENTITY_BLOCKED
            val json = JSONObject(AtomicFile(file).openRead().use { it.readBytes().toString(Charsets.UTF_8) })
            check(json.opt("schema") == 1 && json.has("boundUid"))
            if (json.isNull("boundUid")) {
                if (json.opt("freshBeforeCoreOpen") != true) return@withLock CoreIdentityDecision.MISSING_IDENTITY_BLOCKED
                persist(json.put("boundUid", uid)) // Finish fsync before any Auth snapshot/gate opens.
                return@withLock CoreIdentityDecision.ACCEPTED
            }
            val bound = json.opt("boundUid") as? String ?: error("Invalid identity value"); check(bound.isNotBlank() && '/' !in bound)
            if (bound == uid) CoreIdentityDecision.ACCEPTED else CoreIdentityDecision.DIFFERENT_UID_BLOCKED
        } catch (_: Exception) { CoreIdentityDecision.STORAGE_FAILED }
    } }
    private fun persist(json: JSONObject) {
        check(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory)
        val atomic = AtomicFile(file); val stream = atomic.startWrite()
        try { stream.write(json.toString().toByteArray()); atomic.finishWrite(stream) }
        catch (error: Throwable) { atomic.failWrite(stream); throw error }
    }
}
