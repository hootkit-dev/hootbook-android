package io.github.hootkit_dev.hootbook.core.sync.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.*
import dev.hootkit.core.PlatformException
import dev.hootkit.core.RemoteCursor
import dev.hootkit.core.RemoteTime
import io.github.hootkit_dev.hootbook.core.sync.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** Actual SDK bridge. Server reads/Firestore transactions, never cached ledger snapshots. */
@Singleton
class CoreFirebaseV2Backend @Inject constructor(private val client: CoreFirebaseClient) : CoreV2Backend {
    private val firestore get() = client.firestore
    override suspend fun getServer(documentPath: String): CoreV2Document? = sdk {
        firestore.document(documentPath).get(Source.SERVER).awaitCoreFirebase().asCoreDocument()
    }
    override suspend fun queryServer(collectionPath: String, cursor: RemoteCursor?, limit: Long, orderByTimestamp: Boolean): List<CoreV2Document> = sdk {
        var query: Query = firestore.collection(collectionPath)
        if (orderByTimestamp) {
            query = query.orderBy("updatedAt", Query.Direction.ASCENDING).orderBy(FieldPath.documentId(), Query.Direction.ASCENDING)
            if (cursor != null) query = query.startAfter(Timestamp(cursor.time.seconds, cursor.time.nanos.toInt()), cursor.docId)
        } else query = query.orderBy(FieldPath.documentId(), Query.Direction.ASCENDING)
        query.limit(limit).get(Source.SERVER).awaitCoreFirebase().documents.map { checkNotNull(it.asCoreDocument()) }
    }
    override suspend fun <T> transaction(block: (CoreV2Transaction) -> T): T = sdk {
        firestore.runTransaction { transaction -> block(object : CoreV2Transaction {
            override fun get(documentPath: String) = transaction.get(firestore.document(documentPath)).asCoreDocument()
            override fun set(documentPath: String, fields: Map<String, Any?>) { transaction.set(firestore.document(documentPath), fields.mapValues { encode(it.value) }) }
            override fun delete(documentPath: String) { transaction.delete(firestore.document(documentPath)) }
        }) }.awaitCoreFirebase()
    }
    override suspend fun deleteBatch(documentPaths: List<String>) = sdk {
        firestore.batch().apply { documentPaths.forEach { delete(firestore.document(it)) } }.commit().awaitCoreFirebase(); Unit
    }
    override fun listenHead(documentPath: String, listener: (CoreV2Snapshot?, Throwable?) -> Unit): CoreV2Registration {
        val registration = firestore.document(documentPath).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, failure ->
            try {
                if (failure != null) listener(null, PlatformException.Network("Head listener interrupted"))
                else if (snapshot != null) listener(CoreV2Snapshot(snapshot.asCoreDocument(), snapshot.metadata.isFromCache, snapshot.metadata.hasPendingWrites()), null)
            } catch (_: Throwable) { }
        }
        return CoreV2Registration { try { registration.remove() } catch (_: Throwable) { } }
    }
    private fun DocumentSnapshot.asCoreDocument(): CoreV2Document? = if (!exists()) null else CoreV2Document(id,
        checkNotNull(data).mapValues { decode(it.value) })
    private fun decode(value: Any?): Any? = when (value) {
        is Timestamp -> RemoteTime(value.seconds, value.nanoseconds.toUInt())
        is Map<*, *> -> value.entries.associate { it.key.toString() to decode(it.value) }
        is List<*> -> value.map(::decode)
        else -> value
    }
    private fun encode(value: Any?): Any? = when (value) {
        CoreServerTimestamp -> FieldValue.serverTimestamp()
        is RemoteTime -> Timestamp(value.seconds, value.nanos.toInt())
        else -> value
    }
    private suspend fun <T> sdk(block: suspend () -> T): T {
        try { return withTimeout(30_000) { block() } }
        catch (_: TimeoutCancellationException) { throw PlatformException.Network("Remote operation timed out") }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Throwable) { throw coreFirebaseFailure(failure) }
    }
}
/** Error-only SDK seam; no RPC, auth state or document data is exposed by this helper. */
internal fun coreFirebaseFailure(failure: Throwable): PlatformException {
    var current: Throwable? = failure; val chain = mutableListOf<Throwable>()
    while (current != null && current !in chain) { chain += current; current = current.cause }
    chain.filterIsInstance<PlatformException>().firstOrNull()?.let { return it }
    chain.filterIsInstance<FirebaseFirestoreException>().firstOrNull()?.let { firestoreError ->
        return when (firestoreError.code) {
            FirebaseFirestoreException.Code.UNAUTHENTICATED, FirebaseFirestoreException.Code.PERMISSION_DENIED -> PlatformException.Unauthorized()
            FirebaseFirestoreException.Code.NOT_FOUND -> PlatformException.NotFound()
            FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> PlatformException.Network("Remote connection unavailable")
            else -> PlatformException.Other("Remote service failure")
        }
    }
    return PlatformException.Network("Remote operation failed")
}
