package io.github.hootkit_dev.hootbook.core.sync

import dev.hootkit.core.*
import java.util.concurrent.CancellationException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoreV2RemoteStoreTest {
    private fun fixture(uid: String = "uid-A"): Triple<CoreV2RemoteStore, MemoryBackend, CoreSyncGate> {
        val backend = MemoryBackend(); val gate = CoreSyncGate(); gate.publish(SyncAccount(uid, null)); gate.open()
        return Triple(CoreV2RemoteStore(backend, gate) {}, backend, gate)
    }
    @Test fun cryptoReadsServerAndCreateIsAtomicCreateIfAbsentNotOverwrite() = runTest {
        val (remote, backend) = fixture(); assertNull(remote.readCrypto()); assertEquals(listOf("get:users/uid-A/v2_meta/crypto"), backend.calls)
        assertEquals(params(), remote.createCrypto(params())); assertEquals(params(), remote.createCrypto(params("different")))
        assertEquals("salt", remote.readCrypto()!!.salt); assertEquals(1, backend.documents.size)
    }
    @Test fun createCryptoRejectsResetMarkerWithoutAnyCryptoWrite() = runTest {
        val (remote, backend) = fixture(); backend.put("users/uid-A/v2_meta/reset", mapOf("deviceId" to DEVICE, "startedAt" to RemoteTime(1, 0u)))
        try { remote.createCrypto(params()); fail("Expected CryptoChanged") } catch (_: PlatformException.CryptoChanged) { }
        assertFalse(backend.documents.containsKey("users/uid-A/v2_meta/crypto"))
    }
    @Test fun writeReadsOnlyCryptoAndHeadAndWritesWholeBatchWithOneServerTimestamp() = runTest {
        val (remote, backend) = fixture(); remote.createCrypto(params()); backend.reads.clear()
        remote.write(listOf(record('b'), record('a')), "salt", 2u, null, DEVICE)
        assertEquals(listOf("users/uid-A/v2_meta/crypto", "users/uid-A/v2_meta/head"), backend.reads)
        val docs = backend.documents.filterKeys { "/v2_records/" in it }.values
        assertEquals(2, docs.size); assertEquals(1, (docs.map { it.fields["updatedAt"] } + backend.documents.getValue("users/uid-A/v2_meta/head").fields["updatedAt"]).distinct().size)
        assertEquals("transactions_" + "b".repeat(32), backend.documents.getValue("users/uid-A/v2_meta/head").fields["lastDocId"])
        assertEquals(setOf("table", "id", "deleted", "deviceId", "v", "updatedAt", "payload"), docs.first().fields.keys)
    }
    @Test fun staleHeadRejectsEntireBatchRegardlessOwnDevice() = runTest {
        val (remote, backend) = fixture(); remote.createCrypto(params()); remote.write(listOf(record('a')), "salt", 2u, null, DEVICE)
        val before = backend.documents.toMap()
        try { remote.write(listOf(record('b')), "salt", 2u, null, DEVICE); fail("Expected Stale") } catch (_: PlatformException.Stale) { }
        assertEquals(before, backend.documents); assertFalse(backend.documents.containsKey("users/uid-A/v2_records/transactions_" + "b".repeat(32)))
    }
    @Test fun cursorComparisonIncludesNanosecondsAndDocIdAtSameTimestamp() = runTest {
        val (remote, backend) = fixture(); remote.createCrypto(params()); backend.put("users/uid-A/v2_meta/head", head(10, 50u, "transactions_" + "b".repeat(32)))
        try { remote.write(listOf(record('c')), "salt", 2u, RemoteCursor(RemoteTime(10, 49u), "z"), DEVICE); fail("Expected nanos stale") } catch (_: PlatformException.Stale) { }
        try { remote.write(listOf(record('c')), "salt", 2u, RemoteCursor(RemoteTime(10, 50u), "transactions_" + "a".repeat(32)), DEVICE); fail("Expected id stale") } catch (_: PlatformException.Stale) { }
        remote.write(listOf(record('c')), "salt", 2u, RemoteCursor(RemoteTime(10, 50u), "transactions_" + "b".repeat(32)), DEVICE)
        assertTrue(backend.documents.containsKey("users/uid-A/v2_records/transactions_" + "c".repeat(32)))
    }
    @Test fun cryptoMissingSaltChangedAndFutureProtocolNeverWriteAnyRecord() = runTest {
        for (mode in 0..2) {
            val (remote, backend) = fixture()
            if (mode != 0) remote.createCrypto(if (mode == 1) params("new-salt") else params(version = 3u))
            val before = backend.documents.toMap()
            try { remote.write(listOf(record('a')), "salt", 2u, null, DEVICE); fail("Expected CryptoChanged") } catch (_: PlatformException.CryptoChanged) { }
            assertEquals(before, backend.documents)
        }
    }
    @Test fun optimisticSdkRetrySeesAdvancedHeadAndReturnsStaleNotOverwrite() = runTest {
        val (remote, backend) = fixture(); remote.createCrypto(params())
        backend.beforeCommit = { backend.beforeCommit = {}; backend.put("users/uid-A/v2_meta/head", head(100, 0u, "transactions_" + "f".repeat(32))) }
        try { remote.write(listOf(record('a')), "salt", 2u, null, DEVICE); fail("Expected Stale after optimistic retry") } catch (_: PlatformException.Stale) { }
        assertTrue(backend.documents.keys.none { "/v2_records/" in it })
    }
    @Test fun tombstoneIsWholeDocumentReplacementWithoutOldPayload() = runTest {
        val (remote, backend) = fixture(); remote.createCrypto(params()); remote.write(listOf(record('a')), "salt", 2u, null, DEVICE)
        val cursor = CoreV2Wire.head(backend.documents.getValue("users/uid-A/v2_meta/head"))
        remote.write(listOf(record('a').copy(deleted = true, payload = null)), "salt", 2u, cursor, DEVICE)
        val doc = backend.documents.getValue("users/uid-A/v2_records/transactions_" + "a".repeat(32)); assertFalse(doc.fields.containsKey("payload"))
        assertTrue(remote.fetchAfter(null, 300u).single().deleted)
    }
    @Test fun fetchUsesServerTimestampThenDocumentIdCursorNotMillisOrTimestampOnly() = runTest {
        val (remote, backend) = fixture()
        for (letter in listOf('b', 'a', 'c')) backend.put("users/uid-A/v2_records/transactions_" + "$letter".repeat(32), CoreV2Wire.recordFields(record(letter)).mapValues { if (it.value === CoreServerTimestamp) RemoteTime(5, 9u) else it.value })
        val page = remote.fetchAfter(RemoteCursor(RemoteTime(5, 9u), "transactions_" + "a".repeat(32)), 1u)
        assertEquals("b".repeat(32), page.single().id); assertEquals(true, backend.queries.last().timestamp); assertEquals(1L, backend.queries.last().limit)
    }
    @Test fun malformedRecordsAndMissingTimeFailRatherThanAdvancingCursorWithDefaults() = runTest {
        for (fields in listOf(CoreV2Wire.recordFields(record('a')).minus("updatedAt"), CoreV2Wire.recordFields(record('a')).plus("v" to 2.0), CoreV2Wire.recordFields(record('a')).plus("updatedAt" to RemoteTime(1, 1_000_000_000u)))) {
            val (remote, backend) = fixture(); backend.put("users/uid-A/v2_records/transactions_" + "a".repeat(32), fields)
            try { remote.fetchAfter(null, 10u); fail("Expected wire rejection") } catch (_: PlatformException.Other) { }
        }
    }
    @Test fun identityMismatchAndTombstonePayloadAreRejectedWithoutDecrypting() = runTest {
        val (remote, backend) = fixture(); backend.put("users/uid-A/v2_records/transactions_" + "b".repeat(32), wireRecord('a'))
        try { remote.fetchAfter(null, 10u); fail("Expected document identity rejection") } catch (_: PlatformException.Other) { }
        backend.documents.clear(); backend.put("users/uid-A/v2_records/transactions_" + "a".repeat(32), wireRecord('a').plus("deleted" to true))
        try { remote.fetchAfter(null, 10u); fail("Expected tombstone payload rejection") } catch (_: PlatformException.Other) { }
    }
    @Test fun allBatchBoundsAndDuplicateDocumentIdsFailBeforeTransaction() = runTest {
        val (remote, backend) = fixture(); remote.createCrypto(params()); val before = backend.documents.toMap()
        for (records in listOf(emptyList(), List(401) { record('a') }, listOf(record('a'), record('a')))) {
            try { remote.write(records, "salt", 2u, null, DEVICE); fail("Expected invalid batch") } catch (_: PlatformException.Other) { }
        }; assertEquals(before, backend.documents)
    }
    @Test fun headListenerIgnoresCachePendingOwnWritesAndDuplicateServerSnapshots() = runTest {
        val backend = MemoryBackend(); val gate = CoreSyncGate(); gate.publish(SyncAccount("uid-A", null)); gate.open(); var changed = 0
        val remote = CoreV2RemoteStore(backend, gate) { changed++ }; remote.startListening(DEVICE)
        assertEquals(listOf("users/uid-A/v2_meta/head"), backend.listenPaths)
        backend.emit(CoreV2Snapshot(CoreV2Document("head", head(1, 0u, "a", OTHER_DEVICE)), true, false)); assertEquals(0, changed)
        backend.emit(CoreV2Snapshot(CoreV2Document("head", head(1, 0u, "a", OTHER_DEVICE)), false, true)); assertEquals(0, changed)
        backend.emit(CoreV2Snapshot(CoreV2Document("head", head(1, 0u, "a", DEVICE)), false, false)); assertEquals(0, changed)
        val other = CoreV2Snapshot(CoreV2Document("head", head(2, 0u, "b", OTHER_DEVICE)), false, false)
        backend.emit(other); backend.emit(other); assertEquals(1, changed)
        backend.emit(CoreV2Snapshot(null, false, false)); backend.emit(CoreV2Snapshot(null, false, false)); assertEquals(2, changed)
    }
    @Test fun firstServerForeignHeadNotifiesButInitialMissingHeadDoesNot() = runTest {
        val backend = MemoryBackend(); val gate = CoreSyncGate(); gate.publish(SyncAccount("uid-A", null)); gate.open(); var changed = 0
        val remote = CoreV2RemoteStore(backend, gate) { changed++ }; remote.startListening(DEVICE)
        backend.emit(CoreV2Snapshot(null, false, false)); assertEquals(0, changed)
        backend.emit(CoreV2Snapshot(CoreV2Document("head", head(1, 0u, "a", OTHER_DEVICE)), false, false)); assertEquals(1, changed)
    }
    @Test fun listenerStopIsNoThrowAndLateCallbackOrChangedUidCannotNotify() = runTest {
        val backend = MemoryBackend(); val gate = CoreSyncGate(); gate.publish(SyncAccount("uid-A", null)); gate.open(); var changed = 0
        val remote = CoreV2RemoteStore(backend, gate) { changed++ }; remote.startListening(DEVICE); val old = backend.listener!!
        backend.throwRemove = true; remote.stopListening(); remote.stopListening(); old(CoreV2Snapshot(CoreV2Document("head", head(1, 0u, "a")), false, false), null)
        assertEquals(0, changed); remote.startListening(DEVICE); gate.close(); gate.publish(SyncAccount("uid-B", null)); gate.open()
        backend.emit(CoreV2Snapshot(CoreV2Document("head", head(2, 0u, "b")), false, false)); assertEquals(0, changed)
    }
    @Test fun fixedUidSurvivesAuthChangeDuringInFlightServerRead() = runTest {
        val (remote, backend, gate) = fixture(); remote.createCrypto(params()); backend.put("users/uid-B/v2_meta/crypto", crypto(params("B")))
        val release = CompletableDeferred<Unit>(); backend.beforeGet = { release.await() }
        val reading = async { remote.readCrypto() }; runCurrent(); gate.close(); gate.publish(SyncAccount("uid-B", null)); gate.open(); release.complete(Unit)
        assertEquals("salt", reading.await()!!.salt); assertEquals("B", remote.readCrypto()!!.salt)
        assertEquals(listOf("get:users/uid-A/v2_meta/crypto", "get:users/uid-B/v2_meta/crypto"), backend.calls.takeLast(2))
    }
    @Test fun cancellationPropagatesWithoutFakeSuccessAndAmbiguousCommitStaysOnOldUid() = runTest {
        val (remote, backend, gate) = fixture(); remote.createCrypto(params())
        backend.afterCommit = { throw CancellationException("ack lost") }
        try { remote.write(listOf(record('a')), "salt", 2u, null, DEVICE); fail("Expected cancellation") } catch (_: CancellationException) { }
        gate.close(); gate.publish(SyncAccount("uid-B", null)); gate.open()
        assertTrue(backend.documents.keys.any { it.startsWith("users/uid-A/v2_records/") }); assertTrue(backend.documents.keys.none { it.startsWith("users/uid-B/v2_records/") })
    }
    @Test fun resetDeletesIn400DocumentTransactionsAndCreatesCryptoOnlyAtEnd() = runTest {
        val (remote, backend) = fixture(); remote.createCrypto(params())
        repeat(805) { index -> backend.put("users/uid-A/v2_records/record-$index", mapOf("payload" to "opaque")) }
        remote.resetV2(params("new-salt"), DEVICE)
        assertEquals(listOf(400, 400, 5), backend.committedDeletes.filter { it.any { path -> "/v2_records/" in path } }.map { it.size })
        assertEquals("new-salt", remote.readCrypto()!!.salt); assertFalse(backend.documents.containsKey("users/uid-A/v2_meta/reset")); assertTrue(backend.documents.keys.none { "/v2_records/" in it })
    }
    @Test fun interruptedResetResumesSameMarkerAndParameterWithoutRepeatingStepOne() = runTest {
        val (remote, backend) = fixture(); remote.createCrypto(params()); backend.put("users/uid-A/v2_records/a", emptyMap())
        var fail = true; backend.beforeQuery = { if (fail) { fail = false; throw PlatformException.Network("offline") } }
        try { remote.resetV2(params("new-salt"), DEVICE); fail("Expected interruption") } catch (_: PlatformException.Network) { }
        val markerTime = backend.documents.getValue("users/uid-A/v2_meta/reset").fields["startedAt"]
        assertNull(remote.readCrypto()); backend.markerTimes.clear(); remote.resetV2(params("new-salt"), DEVICE)
        assertTrue(backend.markerTimes.isEmpty()); assertNotNull(markerTime); assertEquals("new-salt", remote.readCrypto()!!.salt)
    }
    @Test fun resetOwnershipLossStopsBatchAndFutureCryptoPreventsAllDeletion() = runTest {
        val (remote, backend) = fixture(); remote.createCrypto(params()); backend.put("users/uid-A/v2_records/a", emptyMap())
        backend.beforeQuery = { backend.put("users/uid-A/v2_meta/reset", mapOf("deviceId" to OTHER_DEVICE, "startedAt" to RemoteTime(2, 0u))) }
        try { remote.resetV2(params("new"), DEVICE); fail("Expected ownership loss") } catch (_: PlatformException.CryptoChanged) { }
        assertTrue(backend.documents.containsKey("users/uid-A/v2_records/a"))
        val (future, db) = fixture(); future.createCrypto(params(version = 3u)); db.put("users/uid-A/v2_records/a", emptyMap()); val before = db.documents.toMap()
        try { future.resetV2(params(), DEVICE); fail("Expected protocol guard") } catch (_: PlatformException.CryptoChanged) { }; assertEquals(before, db.documents)
    }
    @Test fun legacyDetectionAndDeletionUseOnlyV1PathsServerPagesAndAreRepeatable() = runTest {
        val (remote, backend) = fixture(); remote.createCrypto(params()); assertFalse(remote.hasV1Data())
        repeat(401) { backend.put("users/uid-A/records/$it", emptyMap()) }; backend.put("users/uid-A/meta/crypto", emptyMap())
        assertTrue(remote.hasV1Data()); remote.deleteV1Data(); remote.deleteV1Data(); assertFalse(remote.hasV1Data())
        assertNotNull(remote.readCrypto()); assertEquals(listOf(400, 1, 1), backend.deleteBatches.map { it.size })
    }
    @Test fun unknownExceptionBodyNeverLeaksAndTypedCasErrorsAreNotTranslated() = runTest {
        val (remote, backend) = fixture(); backend.beforeGet = { throw IllegalStateException("secret remote payload") }
        try { remote.readCrypto(); fail("Expected sanitized error") } catch (failure: PlatformException.Network) { assertFalse(failure.detail.contains("secret")) }
        backend.beforeGet = { throw RuntimeException(PlatformException.Stale()) }
        try { remote.readCrypto(); fail("Expected original Stale") } catch (_: PlatformException.Stale) { }
    }
    internal class MemoryBackend : CoreV2Backend {
        val documents = linkedMapOf<String, CoreV2Document>(); val calls = mutableListOf<String>(); val reads = mutableListOf<String>()
        data class QueryCall(val path: String, val cursor: RemoteCursor?, val limit: Long, val timestamp: Boolean)
        val queries = mutableListOf<QueryCall>(); val deleteBatches = mutableListOf<List<String>>(); val committedDeletes = mutableListOf<List<String>>(); val markerTimes = mutableListOf<Any?>(); val listenPaths = mutableListOf<String>()
        var revision = 0; var clock = 0L; var beforeGet: suspend () -> Unit = {}; var beforeQuery: suspend () -> Unit = {}
        var beforeCommit: suspend () -> Unit = {}; var afterCommit: suspend () -> Unit = {}; var throwRemove = false
        var listener: ((CoreV2Snapshot?, Throwable?) -> Unit)? = null
        fun put(path: String, fields: Map<String, Any?>) { documents[path] = CoreV2Document(path.substringAfterLast('/'), fields); revision++ }
        override suspend fun getServer(documentPath: String): CoreV2Document? { calls += "get:$documentPath"; beforeGet(); return documents[documentPath] }
        override suspend fun queryServer(collectionPath: String, cursor: RemoteCursor?, limit: Long, orderByTimestamp: Boolean): List<CoreV2Document> {
            beforeQuery(); queries += QueryCall(collectionPath, cursor, limit, orderByTimestamp)
            val docs = documents.filterKeys { it.substringBeforeLast('/') == collectionPath }.values
            val ordered = if (orderByTimestamp) docs.sortedWith { a, b -> CoreV2Wire.compare(RemoteCursor(a.fields["updatedAt"] as? RemoteTime ?: RemoteTime(0, 0u), a.id), RemoteCursor(b.fields["updatedAt"] as? RemoteTime ?: RemoteTime(0, 0u), b.id)) } else docs.sortedBy { it.id }
            return ordered.filter { !orderByTimestamp || cursor == null || CoreV2Wire.compare(RemoteCursor(it.fields["updatedAt"] as? RemoteTime ?: RemoteTime(0, 0u), it.id), cursor) > 0 }.take(limit.toInt())
        }
        override suspend fun <T> transaction(block: (CoreV2Transaction) -> T): T {
            repeat(5) {
                val seen = revision; val copy = documents.toMutableMap(); val sets = linkedMapOf<String, Map<String, Any?>>(); val deletes = mutableListOf<String>(); var written = false
                val result = block(object : CoreV2Transaction {
                    override fun get(documentPath: String): CoreV2Document? { check(!written) { "Firestore reads after write" }; reads += documentPath; return copy[documentPath] }
                    override fun set(documentPath: String, fields: Map<String, Any?>) { written = true; sets[documentPath] = fields }
                    override fun delete(documentPath: String) { written = true; deletes += documentPath }
                })
                beforeCommit(); if (revision != seen) return@repeat
                val timestamp = RemoteTime(++clock, 123u)
                deletes.forEach { documents.remove(it) }; sets.forEach { (path, fields) -> val data = fields.mapValues { if (it.value === CoreServerTimestamp) timestamp else it.value }
                    put(path, data); if (path.endsWith("/reset")) markerTimes += data["startedAt"] }
                if (deletes.isNotEmpty()) { revision++; committedDeletes += deletes.toList() }
                afterCommit(); return result
            }; error("Optimistic retries exhausted")
        }
        override suspend fun deleteBatch(documentPaths: List<String>) { deleteBatches += documentPaths; documentPaths.forEach { documents.remove(it) }; revision++ }
        override fun listenHead(documentPath: String, listener: (CoreV2Snapshot?, Throwable?) -> Unit): CoreV2Registration { listenPaths += documentPath; this.listener = listener
            return CoreV2Registration { if (throwRemove) error("listener removal") } }
        fun emit(snapshot: CoreV2Snapshot) { listener?.invoke(snapshot, null) }
    }
    companion object {
        val DEVICE = "d".repeat(32)
        val OTHER_DEVICE = "e".repeat(32)
        fun params(salt: String = "salt", version: UInt = 2u) = CryptoParams("PBKDF2-HMAC-SHA256", salt, 600000u, "AES-256-GCM", "key-check-opaque", version)
        fun record(letter: Char) = RemoteRecord("transactions", "$letter".repeat(32), false, "opaque-base64", DEVICE, 2u, null)
        fun wireRecord(letter: Char) = CoreV2Wire.recordFields(record(letter)).mapValues { if (it.value === CoreServerTimestamp) RemoteTime(1, 0u) else it.value }
        fun crypto(params: CryptoParams) = CoreV2Wire.cryptoFields(params).mapValues { if (it.value === CoreServerTimestamp) RemoteTime(1, 0u) else it.value }
        fun head(seconds: Long, nanos: UInt, id: String, device: String = OTHER_DEVICE) = mapOf("updatedAt" to RemoteTime(seconds, nanos), "lastDocId" to if (id.length < 32) "transactions_" + id.take(1).repeat(32) else id, "deviceId" to device)
    }
}
