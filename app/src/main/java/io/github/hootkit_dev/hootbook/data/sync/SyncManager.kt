package io.github.hootkit_dev.hootbook.data.sync

import android.content.Context
import android.util.Log
import io.github.hootkit_dev.hootbook.data.backup.BackupConfiguration
import io.github.hootkit_dev.hootbook.data.backup.BackupExporter
import io.github.hootkit_dev.hootbook.data.backup.ExportResult
import io.github.hootkit_dev.hootbook.data.database.HootbookDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Runs sync for the app (docs/sync.md, "Engine"): sign-in, the passphrase, the first sync, and
 * afterwards a push a few seconds after local changes, a pull when the app comes to the
 * foreground and whenever another device writes while it is there, and WorkManager retries when
 * offline. Nothing here needs the UI, so the app lock does not stop it.
 */
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: HootbookDatabase,
    private val backend: SyncBackend,
    private val settings: SyncSettings,
    private val backupExporter: BackupExporter,
) {
    enum class Stage {
        /** This build has no sync (F-Droid) */
        UNAVAILABLE,
        SIGNED_OUT,
        /** Signed in; the passphrase is to be set (no account parameters yet) or entered */
        PASSPHRASE,
        /** Both this device and the cloud have data: replace or merge */
        CHOICE,
        ACTIVE,
    }

    data class State(
        val stage: Stage = Stage.SIGNED_OUT,
        val email: String? = null,
        /** In [Stage.PASSPHRASE]: whether the account already has a passphrase; null while unknown */
        val passphraseExists: Boolean? = null,
        val busy: Boolean = false,
        val lastSyncAt: Long = 0,
        val problem: SyncProblem? = null,
        val pending: Int = 0,
        val held: Int = 0,
        /** Signed in and set up, but switched off: nothing is pushed or pulled */
        val paused: Boolean = false,
        val uid: String? = null,
        val deviceId: String? = null,
        /** The last pull's position: `document id @ seconds.nanos`; null before the first pull */
        val cursor: String? = null,
        val projectId: String? = null,
        val protocolVersion: Int = SyncSchema.VERSION,
        /** The last failure as "Type: message" */
        val lastError: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var engine: SyncEngine? = null
    private var engineFor: Pair<String, ByteArray>? = null
    private var listener: AutoCloseable? = null
    @Volatile private var foreground = false
    private var started = false
    private val pullRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** False in a build without sync (F-Droid); known at once, before [state] is first published. */
    val available: Boolean
        get() = backend.available

    private val active: Boolean
        get() = backend.available && settings.enabled && !settings.paused && settings.account != null && settings.key != null

    /** Starts watching the outbox; call once when the app starts. */
    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        // A session that ended elsewhere shows as a permission problem on the next sync; signing
        // out and in again fixes it. Nothing is cleared here, so a slow auth start costs nothing.
        scope.launch { publish() }
        scope.launch {
            combine(database.syncDao().pendingCountFlow(), database.syncDao().inboxCountFlow()) { p, h -> p to h }
                .collect { (pending, held) -> _state.update { it.copy(pending = pending, held = held) } }
        }
        scope.launch {
            database.syncDao().pendingCountFlow().debounce(PUSH_DELAY_MS).collect { pending ->
                if (pending > 0 && active) sync(pull = false)
            }
        }
        scope.launch {
            pullRequests.debounce(PULL_DELAY_MS).collect { if (active && foreground) sync(push = false) }
        }
    }

    fun onForeground() {
        foreground = true
        // Off the main thread: reading the key opens the keystore
        scope.launch {
            if (!active) return@launch
            sync()
            listen()
        }
    }

    fun onBackground() {
        foreground = false
        stopListening()
        scope.launch {
            if (active && database.syncDao().pendingCount() > 0) SyncWorker.enqueue(context)
        }
    }

    @Synchronized
    private fun listen() {
        if (listener != null || !foreground) return
        val account = settings.account ?: return
        listener = runCatching { backend.store(account).listen(settings.cursor, settings.deviceId) { pullRequests.tryEmit(Unit) } }
            .onFailure { Log.w(TAG, "Listening failed", it) }.getOrNull()
    }

    @Synchronized
    private fun stopListening() {
        runCatching { listener?.close() }
        listener = null
    }

    private fun engine(): SyncEngine? {
        val account = settings.account ?: return null
        val key = settings.key ?: return null
        val current = engineFor
        if (engine == null || current == null || current.first != account.uid || !current.second.contentEquals(key)) {
            engine = SyncEngine(database, backend.store(account), key, settings.deviceId, settings)
            engineFor = account.uid to key
        }
        return engine
    }

    private fun publish() {
        val account = settings.account
        val stage = when {
            !backend.available -> Stage.UNAVAILABLE
            account == null -> Stage.SIGNED_OUT
            settings.key == null -> Stage.PASSPHRASE
            !settings.enabled -> Stage.CHOICE
            else -> Stage.ACTIVE
        }
        _state.update {
            it.copy(
                stage = stage,
                email = account?.email,
                passphraseExists = if (stage == Stage.PASSPHRASE) it.passphraseExists else null,
                lastSyncAt = settings.lastSyncAt,
                problem = settings.lastProblem,
                paused = settings.paused,
                uid = account?.uid,
                deviceId = if (backend.available) settings.deviceId else null,
                cursor = settings.cursor?.let { "${it.docId} @ ${it.time.seconds}.${it.time.nanos.toString().padStart(9, '0')}" },
                projectId = backend.projectId,
                lastError = settings.lastError,
            )
        }
    }

    /**
     * Switches sync off ([paused]) or back on. Off keeps the account, the key and the cursor;
     * on syncs at once and listens again while the app is in the foreground.
     */
    fun setPaused(paused: Boolean) {
        _state.update { it.copy(paused = paused) }
        // Off the main thread: publishing reads the key, which opens the keystore
        scope.launch {
            settings.paused = paused
            if (paused) stopListening()
            publish()
            if (!paused && foreground) onForeground()
        }
    }

    private fun recordError(e: Throwable) {
        settings.lastError = describe(e)
    }

    private inline fun <T> busy(block: () -> T): T {
        _state.update { it.copy(busy = true) }
        try {
            return block()
        } finally {
            _state.update { it.copy(busy = false) }
            publish()
        }
    }

    // ---- sign-in and passphrase ------------------------------------------------------------

    /** Google sign-in over [activityContext]; then the passphrase is asked. */
    suspend fun signIn(activityContext: Context) = mutex.withLock {
        busy {
            val account = try {
                backend.signIn(activityContext)
            } catch (e: Exception) {
                recordError(e)
                throw e
            }
            if (settings.account?.uid != account.uid) settings.clearAccount()
            settings.account = account
            engine = null
        }
        refreshPassphraseState()
    }

    /** Looks up whether the account already has a passphrase (another device set it). */
    suspend fun refreshPassphraseState() {
        val account = settings.account ?: return
        if (settings.key != null) return
        val exists = try {
            backend.store(account).readCrypto() != null
        } catch (e: Exception) {
            Log.w(TAG, "Reading the account's parameters failed", e)
            settings.lastProblem = classify(e)
            recordError(e)
            publish()
            return
        }
        settings.lastProblem = null
        publish()
        _state.update { it.copy(passphraseExists = exists) }
    }

    /**
     * Sets (first device) or checks the passphrase, then runs the first sync when nothing needs
     * choosing. Throws [WrongPassphraseException] for a passphrase that does not match.
     */
    suspend fun submitPassphrase(passphrase: String) = mutex.withLock {
        busy {
            val account = settings.account ?: return@busy
            val store = backend.store(account)
            val key = withContext(Dispatchers.Default) {
                val params = store.readCrypto()
                if (params == null) {
                    val (created, key) = SyncCrypto.create(passphrase)
                    val stored = store.createCrypto(created)
                    if (stored == created) key else SyncCrypto.unlock(passphrase, stored)
                } else {
                    SyncCrypto.unlock(passphrase, params)
                }
            }
            settings.key = key
            val engine = engine() ?: return@busy
            when (engine.firstSyncCase()) {
                FirstSyncCase.CLOUD_EMPTY -> { engine.uploadAll(); activate(null) }
                FirstSyncCase.LOCAL_PRISTINE -> activate(engine.replaceLocalWithCloud())
                FirstSyncCase.BOTH_HAVE_DATA -> Unit
            }
        }
        if (active) onForeground()
    }

    /** Both have data: back up this device's data, then replace it with the cloud's. Returns the backup's file name. */
    suspend fun replaceLocalWithCloud(): String = mutex.withLock {
        busy {
            val engine = engine() ?: error("not signed in")
            val backup = backupLocal()
            activate(engine.replaceLocalWithCloud())
            backup
        }
    }.also { if (active) onForeground() }

    /** Both have data: keep both (records both have become one). */
    suspend fun merge() = mutex.withLock {
        busy {
            val engine = engine() ?: error("not signed in")
            activate(engine.merge())
        }
    }.also { if (active) onForeground() }

    private fun activate(result: PullResult?) {
        settings.enabled = true
        settings.lastSyncAt = System.currentTimeMillis()
        settings.lastProblem = result?.let(::problemOf)
    }

    /** Signs out and forgets the key and sync state; the data on this device stays. */
    suspend fun signOut() = mutex.withLock {
        busy {
            stopListening()
            runCatching { backend.signOut() }.onFailure { Log.w(TAG, "Sign-out failed", it) }
            settings.clearAccount()
            database.syncDao().clearInbox()
            engine = null
            engineFor = null
            _state.update { it.copy(passphraseExists = null) }
        }
    }

    // ---- syncing ---------------------------------------------------------------------------

    /** Sync now (the button): push, then pull. Returns false when it failed. */
    suspend fun syncNow(): Boolean = sync()

    /** One full sync for [SyncWorker]; true when done or when there is nothing to do. */
    suspend fun runOnce(): Boolean = !active || sync()

    private suspend fun sync(push: Boolean = true, pull: Boolean = true): Boolean = mutex.withLock {
        if (!active) return@withLock true
        val engine = engine() ?: return@withLock true
        busy {
            try {
                if (push) engine.push()
                val problem = if (pull) problemOf(engine.pull()) else settings.lastProblem.takeIf {
                    it == SyncProblem.UNREADABLE || it == SyncProblem.NEWER_VERSION
                }
                settings.lastSyncAt = System.currentTimeMillis()
                settings.lastProblem = problem
                settings.lastError = null
                true
            } catch (e: Exception) {
                Log.w(TAG, "Sync failed", e)
                settings.lastProblem = classify(e)
                recordError(e)
                SyncWorker.enqueue(context)
                false
            }
        }
    }

    private fun problemOf(result: PullResult): SyncProblem? = when {
        result.unreadable > 0 -> SyncProblem.UNREADABLE
        result.newerVersion > 0 -> SyncProblem.NEWER_VERSION
        else -> null
    }

    private fun classify(e: Throwable): SyncProblem = when (e) {
        is SyncRemoteException -> e.problem
        is IOException -> SyncProblem.NETWORK
        else -> SyncProblem.OTHER
    }

    /**
     * Exports this device's data with the backup exporter into app storage
     * (`files/sync_backups`, the last three kept) before it is replaced.
     */
    private suspend fun backupLocal(): String {
        val result = backupExporter.exportBackup(BackupConfiguration())
        if (result !is ExportResult.Success) error("The backup before replacing failed")
        val dir = File(context.filesDir, BACKUP_DIR).apply { mkdirs() }
        val target = File(dir, result.file.name)
        result.file.copyTo(target, overwrite = true)
        result.file.delete()
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(KEPT_BACKUPS)?.forEach { it.delete() }
        return target.name
    }

    internal companion object {
        /** "Type: message" of [e] and its causes, for the debug section; no stack trace. */
        fun describe(e: Throwable): String = generateSequence(e) { it.cause }.take(3)
            .joinToString(" ← ") { t -> listOfNotNull(t.javaClass.simpleName, t.message?.takeIf { it.isNotBlank() }).joinToString(": ") }

        const val TAG = "SyncManager"
        const val PUSH_DELAY_MS = 3_000L
        const val PULL_DELAY_MS = 1_000L
        const val BACKUP_DIR = "sync_backups"
        const val KEPT_BACKUPS = 3
    }
}
