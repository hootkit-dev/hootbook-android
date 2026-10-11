package io.github.hootkit_dev.hootbook.core.sync

import dev.hootkit.core.*
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException

/** Firestore v2 protocol adapter only; no ledger merge, crypto, outbox or balance rules. */
class CoreV2RemoteStore(private val backend: CoreV2Backend, private val gate: CoreSyncGate,
    private val remoteChanged: () -> Unit) : RemoteStore {
    private val listenerLock = Any()
    private var registration: CoreV2Registration? = null
    private val listenGeneration = AtomicLong()
    private fun root(lease: CoreRemoteLease) = "users/${lease.uid}"
    private suspend fun <T> call(block: suspend (CoreRemoteLease) -> T): T {
        val lease = gate.lease()
        try { return block(lease) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Throwable) {
            // Transaction SDK wrappers may wrap a deliberate CAS PlatformException; preserve it.
            var cause: Throwable? = failure; val seen = mutableSetOf<Throwable>()
            while (cause != null && seen.add(cause)) {
                if (cause is PlatformException) throw cause
                cause = cause.cause
            }
            throw PlatformException.Network("Remote operation failed")
        }
    }
    override suspend fun readCrypto(): CryptoParams? = call { lease ->
        backend.getServer("${root(lease)}/v2_meta/crypto")?.let(CoreV2Wire::crypto)
    }
    override suspend fun createCrypto(params: CryptoParams): CryptoParams = call { lease ->
        val meta = "${root(lease)}/v2_meta"
        backend.transaction { tx ->
            val crypto = tx.get("$meta/crypto"); val reset = tx.get("$meta/reset")
            if (reset != null) throw PlatformException.CryptoChanged()
            if (crypto != null) CoreV2Wire.crypto(crypto)
            else { tx.set("$meta/crypto", CoreV2Wire.cryptoFields(params)); params }
        }
    }
    override suspend fun write(records: List<RemoteRecord>, keySalt: String, protocolVersion: UInt,
        cursor: RemoteCursor?, deviceId: String) = call { lease ->
        if (records.size !in 1..400) throw PlatformException.Other("Invalid write batch size")
        val writes = records.map { CoreV2Wire.docId(it) to CoreV2Wire.recordFields(it) }
        if (writes.map { it.first }.distinct().size != writes.size) throw PlatformException.Other("Duplicate batch document")
        val path = root(lease); val meta = "$path/v2_meta"
        backend.transaction { tx ->
            val crypto = tx.get("$meta/crypto")?.let(CoreV2Wire::crypto)
            if (crypto == null || crypto.salt != keySalt || crypto.version > protocolVersion) throw PlatformException.CryptoChanged()
            val head = tx.get("$meta/head")?.let(CoreV2Wire::head)
            if (head != null && (cursor == null || CoreV2Wire.compare(head, cursor) > 0)) throw PlatformException.Stale()
            writes.forEach { (id, fields) -> tx.set("$path/v2_records/$id", fields) }
            tx.set("$meta/head", mapOf("updatedAt" to CoreServerTimestamp, "deviceId" to deviceId, "lastDocId" to writes.maxOf { it.first }))
        }
    }
    override suspend fun fetchAfter(cursor: RemoteCursor?, limit: UInt): List<RemoteRecord> = call { lease ->
        if (limit == 0u) throw PlatformException.Other("Invalid fetch limit")
        backend.queryServer("${root(lease)}/v2_records", cursor, limit.toLong(), true).map(CoreV2Wire::record)
    }
    override fun startListening(ownDeviceId: String) {
        val lease = gate.lease()
        synchronized(listenerLock) {
            stopListening()
            val generation = listenGeneration.incrementAndGet()
            var existed = false
            var last: RemoteCursor? = null
            registration = backend.listenHead("${root(lease)}/v2_meta/head") { snapshot, failure ->
                try {
                    synchronized(listenerLock) {
                        if (generation != listenGeneration.get() || !gate.accepts(lease) || failure != null || snapshot == null || snapshot.fromCache || snapshot.pendingWrites) return@listenHead
                        val doc = snapshot.document
                        if (doc == null) {
                            val notify = existed; existed = false; last = null
                            if (notify) remoteChanged()
                        } else {
                            val head = CoreV2Wire.head(doc); val changed = !existed || last != head
                            existed = true; last = head
                            if (changed && CoreV2Wire.string(doc.fields, "deviceId") != ownDeviceId) remoteChanged()
                        }
                    }
                } catch (_: Throwable) { /* No-throw SDK callback; never log private document/error bodies. */ }
            }
        }
    }
    override fun stopListening() {
        synchronized(listenerLock) {
            listenGeneration.incrementAndGet()
            try { registration?.remove() } catch (_: Throwable) { }
            registration = null
        }
    }
    override suspend fun hasV1Data(): Boolean = call { lease ->
        val path = root(lease)
        backend.queryServer("$path/records", null, 1, false).isNotEmpty() || backend.queryServer("$path/meta", null, 1, false).isNotEmpty()
    }
    override suspend fun deleteV1Data() = call { lease ->
        val path = root(lease)
        for (collection in listOf("records", "meta")) {
            while (true) {
                val docs = backend.queryServer("$path/$collection", null, 400, false)
                if (docs.isEmpty()) break
                backend.deleteBatch(docs.map { "$path/$collection/${it.id}" })
            }
        }
    }
    override suspend fun resetV2(params: CryptoParams, deviceId: String) = call { lease ->
        val path = root(lease); val meta = "$path/v2_meta"
        backend.transaction { tx ->
            val crypto = tx.get("$meta/crypto"); val reset = tx.get("$meta/reset")
            if (crypto != null && CoreV2Wire.crypto(crypto).version > params.version) throw PlatformException.CryptoChanged()
            if (reset?.fields?.get("deviceId") != deviceId) {
                tx.delete("$meta/crypto"); tx.delete("$meta/head")
                tx.set("$meta/reset", mapOf("deviceId" to deviceId, "startedAt" to CoreServerTimestamp))
            }
        }
        while (true) {
            val docs = backend.queryServer("$path/v2_records", null, 400, false)
            if (docs.isEmpty()) break
            backend.transaction { tx ->
                if (tx.get("$meta/reset")?.fields?.get("deviceId") != deviceId) throw PlatformException.CryptoChanged()
                docs.forEach { tx.delete("$path/v2_records/${it.id}") }
            }
        }
        backend.transaction { tx ->
            if (tx.get("$meta/reset")?.fields?.get("deviceId") != deviceId) throw PlatformException.CryptoChanged()
            tx.set("$meta/crypto", CoreV2Wire.cryptoFields(params)); tx.delete("$meta/reset")
        }
    }
}
