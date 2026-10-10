package io.github.hootkit_dev.hootbook.data.sync

/**
 * An in-memory account behaving like the Firestore one: a write batch gets one server time,
 * later than every earlier one, and documents are replaced whole.
 */
class FakeRemoteStore : RemoteStore {
    val docs = linkedMapOf<String, RemoteRecord>()
    var crypto: CryptoParams? = null
    private var clock = 1_000L
    var writes = 0
        private set
    /** Set to make every call fail as if offline. */
    var offline = false

    private fun check() { if (offline) throw SyncRemoteException(SyncProblem.NETWORK) }

    override suspend fun readCrypto(): CryptoParams? { check(); return crypto }

    override suspend fun createCrypto(params: CryptoParams): CryptoParams {
        check()
        return crypto ?: params.also { crypto = it }
    }

    override suspend fun write(records: List<RemoteRecord>) {
        check()
        val time = RemoteTime(++clock, 0)
        records.forEach { docs[it.docId] = it.copy(updatedAt = time, payload = if (it.deleted) null else it.payload) }
        writes += records.size
    }

    /** Writes [records] as another device would, one batch. */
    fun put(vararg records: RemoteRecord) {
        val time = RemoteTime(++clock, 0)
        records.forEach { docs[it.docId] = it.copy(updatedAt = time) }
    }

    override suspend fun fetchAfter(cursor: RemoteCursor?, limit: Int): List<RemoteRecord> {
        check()
        return docs.values.sortedBy { it.cursor!! }.filter { cursor == null || it.cursor!! > cursor }.take(limit)
    }

    override suspend fun get(docId: String): RemoteRecord? { check(); return docs[docId] }

    override suspend fun isEmpty(): Boolean { check(); return docs.isEmpty() }

    override fun listen(cursor: RemoteCursor?, ownDevice: String, onChange: () -> Unit) = AutoCloseable { }
}

/** A cursor kept in memory. */
class MemoryCursorStore : SyncCursorStore {
    override var cursor: RemoteCursor? = null
}
