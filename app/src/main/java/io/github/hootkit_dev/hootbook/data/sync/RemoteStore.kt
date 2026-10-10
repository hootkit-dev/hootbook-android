package io.github.hootkit_dev.hootbook.data.sync

import android.content.Context

/** A server time (Firestore `Timestamp`): seconds since the epoch and nanoseconds within. */
data class RemoteTime(val seconds: Long, val nanos: Int) : Comparable<RemoteTime> {
    override fun compareTo(other: RemoteTime): Int =
        compareValuesBy(this, other, RemoteTime::seconds, RemoteTime::nanos)

    val millis: Long get() = seconds * 1000 + nanos / 1_000_000
}

/** Where a pull continues: after the record [docId] written at [time] (records are ordered by both). */
data class RemoteCursor(val time: RemoteTime, val docId: String) : Comparable<RemoteCursor> {
    override fun compareTo(other: RemoteCursor): Int =
        compareValuesBy(this, other, RemoteCursor::time, RemoteCursor::docId)
}

/**
 * One document of `users/{uid}/records` (docs/sync.md, "Wire protocol"). [updatedAt] is set by the
 * server on every write; it is null only on a record about to be written.
 */
data class RemoteRecord(
    val table: String,
    val syncId: String,
    val deleted: Boolean,
    /** base64 of nonce || ciphertext || tag; null on a tombstone */
    val payload: String?,
    val deviceId: String,
    val version: Int = SyncSchema.VERSION,
    /** On a tombstone that resolved a duplicate: the sync id of the record that replaces this one */
    val replacedBy: String? = null,
    val updatedAt: RemoteTime? = null,
) {
    val docId: String get() = SyncSchema.docId(table, syncId)
    val cursor: RemoteCursor? get() = updatedAt?.let { RemoteCursor(it, docId) }
}

/**
 * The records of one signed-in user. The Firebase implementation (standard flavor) maps it onto
 * Firestore; tests use an in-memory one. Every call may throw on network or permission errors.
 */
interface RemoteStore {
    /** `meta/crypto`, or null when the account has none yet. */
    suspend fun readCrypto(): CryptoParams?

    /**
     * Stores [params] unless the account already has parameters (another device was first), in one
     * server transaction; returns the parameters that are stored.
     */
    suspend fun createCrypto(params: CryptoParams): CryptoParams

    /** Writes [records] (each replacing its document whole); the server stamps `updatedAt`. */
    suspend fun write(records: List<RemoteRecord>)

    /** Up to [limit] records written after [cursor] (all when null), ordered by `updatedAt`, then document id. */
    suspend fun fetchAfter(cursor: RemoteCursor?, limit: Int): List<RemoteRecord>

    /** One record by document id, or null. */
    suspend fun get(docId: String): RemoteRecord?

    /** Whether the account has no records at all, tombstones included. */
    suspend fun isEmpty(): Boolean

    /**
     * Calls [onChange] whenever records are written after [cursor] by a device other than
     * [ownDevice], until closed. Used while the app is in the foreground; the engine then pulls.
     */
    fun listen(cursor: RemoteCursor?, ownDevice: String, onChange: () -> Unit): AutoCloseable
}

/** The signed-in account. */
data class SyncAccount(val uid: String, val email: String?)

/**
 * Sign-in and the remote store behind it. The standard flavor implements it with Firebase Auth,
 * Google sign-in (Credential Manager) and Firestore; the F-Droid flavor has none of that and
 * reports [available] false.
 */
interface SyncBackend {
    val available: Boolean

    /** The backend's project, shown in the Sync page's status and debug section. */
    val projectId: String? get() = null

    /** The account signed in now, if any. */
    fun currentAccount(): SyncAccount?

    /** Google sign-in; [activityContext] must be an Activity (the account picker is shown over it). */
    suspend fun signIn(activityContext: Context): SyncAccount

    suspend fun signOut()

    /** The records of [account]. */
    fun store(account: SyncAccount): RemoteStore
}

/** A remote failure, classified for the Sync screen. Implementations wrap their own errors in it. */
class SyncRemoteException(val problem: SyncProblem, cause: Throwable? = null) : Exception(problem.name, cause)

/** Thrown when the user closes the sign-in sheet; not an error worth showing. */
class SignInCancelledException(cause: Throwable? = null) : Exception("Sign-in cancelled", cause)

/** Why the last sync did not finish, shown on the Sync screen. */
enum class SyncProblem {
    /** No connection, or the server could not be reached */
    NETWORK,
    /** The server refused (signed out elsewhere, rules) */
    PERMISSION,
    /** Some records could not be decrypted: they were written with another passphrase */
    UNREADABLE,
    /** Some records come from a newer version of the app */
    NEWER_VERSION,
    /** Anything else */
    OTHER,
}
