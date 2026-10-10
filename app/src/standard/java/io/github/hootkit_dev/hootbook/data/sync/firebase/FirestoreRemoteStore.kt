package io.github.hootkit_dev.hootbook.data.sync.firebase

import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import io.github.hootkit_dev.hootbook.data.sync.CryptoParams
import io.github.hootkit_dev.hootbook.data.sync.RemoteCursor
import io.github.hootkit_dev.hootbook.data.sync.RemoteRecord
import io.github.hootkit_dev.hootbook.data.sync.RemoteStore
import io.github.hootkit_dev.hootbook.data.sync.RemoteTime
import io.github.hootkit_dev.hootbook.data.sync.SyncProblem
import io.github.hootkit_dev.hootbook.data.sync.SyncRemoteException
import java.util.Base64
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

/**
 * The wire protocol of docs/sync.md on Firestore: `users/{uid}/records/{table}_{syncId}` and
 * `users/{uid}/meta/crypto`. Reads go to the server (Source.SERVER); offline, calls fail and the
 * engine retries later.
 */
internal class FirestoreRemoteStore(firestore: FirebaseFirestore, uid: String) : RemoteStore {
    private val user = firestore.collection("users").document(uid)
    private val records = user.collection("records")
    private val crypto = user.collection("meta").document("crypto")
    private val db = firestore

    override suspend fun readCrypto(): CryptoParams? = firebaseCall {
        crypto.get(Source.SERVER).await().takeIf { it.exists() }?.let(::cryptoOf)
    }

    override suspend fun createCrypto(params: CryptoParams): CryptoParams = firebaseCall {
        db.runTransaction { tx ->
            val current = tx.get(crypto)
            if (current.exists()) cryptoOf(current)
            else {
                tx.set(
                    crypto,
                    mapOf(
                        F_KDF to params.kdf,
                        F_SALT to Base64.getEncoder().encodeToString(params.salt),
                        F_ITERATIONS to params.iterations.toLong(),
                        F_CIPHER to params.cipher,
                        F_KEY_CHECK to params.keyCheck,
                        F_VERSION to params.version.toLong(),
                        F_CREATED_AT to FieldValue.serverTimestamp(),
                    )
                )
                params
            }
        }.await()
    }

    override suspend fun write(records: List<RemoteRecord>) {
        if (records.isEmpty()) return
        firebaseCall {
            val batch = db.batch()
            records.forEach { record ->
                val fields = mutableMapOf<String, Any>(
                    F_TABLE to record.table,
                    F_SYNC_ID to record.syncId,
                    F_UPDATED_AT to FieldValue.serverTimestamp(),
                    F_DELETED to record.deleted,
                    F_DEVICE_ID to record.deviceId,
                    F_VERSION to record.version.toLong(),
                )
                record.payload?.takeIf { !record.deleted }?.let { fields[F_PAYLOAD] = it }
                record.replacedBy?.let { fields[F_REPLACED_BY] = it }
                // set() without merge: the document is replaced whole, so a tombstone has no payload
                batch.set(this.records.document(record.docId), fields)
            }
            batch.commit().await()
        }
    }

    override suspend fun fetchAfter(cursor: RemoteCursor?, limit: Int): List<RemoteRecord> = firebaseCall {
        var query: Query = records.orderBy(F_UPDATED_AT).orderBy(FieldPath.documentId())
        if (cursor != null) query = query.startAfter(Timestamp(cursor.time.seconds, cursor.time.nanos), cursor.docId)
        query.limit(limit.toLong()).get(Source.SERVER).await().documents.map(::recordOf)
    }

    override suspend fun get(docId: String): RemoteRecord? = firebaseCall {
        records.document(docId).get(Source.SERVER).await().takeIf { it.exists() }?.let(::recordOf)
    }

    override suspend fun isEmpty(): Boolean = firebaseCall {
        records.limit(1).get(Source.SERVER).await().isEmpty
    }

    override fun listen(cursor: RemoteCursor?, ownDevice: String, onChange: () -> Unit): AutoCloseable {
        var query: Query = records.orderBy(F_UPDATED_AT)
        if (cursor != null) query = query.startAfter(Timestamp(cursor.time.seconds, cursor.time.nanos))
        val registration = query.addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
            if (error != null || snapshot == null || snapshot.metadata.hasPendingWrites()) return@addSnapshotListener
            if (snapshot.documentChanges.any { it.document.getString(F_DEVICE_ID) != ownDevice }) onChange()
        }
        return AutoCloseable { registration.remove() }
    }

    private fun recordOf(doc: DocumentSnapshot): RemoteRecord {
        val table = doc.getString(F_TABLE) ?: doc.id.substringBeforeLast('_', "")
        val time = doc.getTimestamp(F_UPDATED_AT)
        return RemoteRecord(
            table = table,
            syncId = doc.getString(F_SYNC_ID) ?: doc.id.substringAfterLast('_'),
            deleted = doc.getBoolean(F_DELETED) ?: false,
            payload = doc.getString(F_PAYLOAD)?.takeIf { it.isNotEmpty() },
            deviceId = doc.getString(F_DEVICE_ID).orEmpty(),
            version = doc.getLong(F_VERSION)?.toInt() ?: 1,
            replacedBy = doc.getString(F_REPLACED_BY),
            updatedAt = time?.let { RemoteTime(it.seconds, it.nanoseconds) },
        )
    }

    private fun cryptoOf(doc: DocumentSnapshot) = CryptoParams(
        salt = Base64.getDecoder().decode(doc.getString(F_SALT).orEmpty()),
        iterations = doc.getLong(F_ITERATIONS)?.toInt() ?: 0,
        keyCheck = doc.getString(F_KEY_CHECK).orEmpty(),
        kdf = doc.getString(F_KDF).orEmpty(),
        cipher = doc.getString(F_CIPHER).orEmpty(),
        version = doc.getLong(F_VERSION)?.toInt() ?: 1,
    )

    private companion object {
        const val F_TABLE = "table"
        const val F_SYNC_ID = "syncId"
        const val F_UPDATED_AT = "updatedAt"
        const val F_DELETED = "deleted"
        const val F_PAYLOAD = "payload"
        const val F_DEVICE_ID = "deviceId"
        const val F_VERSION = "v"
        const val F_REPLACED_BY = "replacedBy"
        const val F_KDF = "kdf"
        const val F_SALT = "salt"
        const val F_ITERATIONS = "iterations"
        const val F_CIPHER = "cipher"
        const val F_KEY_CHECK = "keyCheck"
        const val F_CREATED_AT = "createdAt"
    }
}

private const val TIMEOUT_MS = 30_000L

/** Runs a Firebase call with a timeout, turning its failures into [SyncRemoteException]. */
internal suspend fun <T> firebaseCall(block: suspend () -> T): T = try {
    withTimeout(TIMEOUT_MS) { block() }
} catch (e: TimeoutCancellationException) {
    throw SyncRemoteException(SyncProblem.NETWORK, e)
} catch (e: FirebaseNetworkException) {
    throw SyncRemoteException(SyncProblem.NETWORK, e)
} catch (e: FirebaseFirestoreException) {
    throw SyncRemoteException(
        when (e.code) {
            FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> SyncProblem.NETWORK
            FirebaseFirestoreException.Code.PERMISSION_DENIED, FirebaseFirestoreException.Code.UNAUTHENTICATED -> SyncProblem.PERMISSION
            else -> SyncProblem.OTHER
        },
        e
    )
}

/** Awaits a Play Services task. */
internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        val error = task.exception
        @Suppress("UNCHECKED_CAST")
        if (error == null && !task.isCanceled) continuation.resume(task.result as T)
        else continuation.resumeWithException(error ?: java.util.concurrent.CancellationException("Task cancelled"))
    }
}
