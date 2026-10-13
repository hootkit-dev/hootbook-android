package io.github.hootkit_dev.hootbook.core.sync

import dev.hootkit.core.*

/** SDK-neutral document shapes; timestamps remain exact seconds+nanos, never epoch-millis. */
data class CoreV2Document(val id: String, val fields: Map<String, Any?>)
data class CoreV2Snapshot(val document: CoreV2Document?, val fromCache: Boolean, val pendingWrites: Boolean)
object CoreServerTimestamp
fun interface CoreV2Registration { fun remove() }
interface CoreV2Transaction {
    fun get(documentPath: String): CoreV2Document?
    fun set(documentPath: String, fields: Map<String, Any?>)
    fun delete(documentPath: String)
}
interface CoreV2Backend {
    suspend fun getServer(documentPath: String): CoreV2Document?
    suspend fun queryServer(collectionPath: String, cursor: RemoteCursor?, limit: Long, orderByTimestamp: Boolean): List<CoreV2Document>
    suspend fun <T> transaction(block: (CoreV2Transaction) -> T): T
    suspend fun deleteBatch(documentPaths: List<String>)
    fun listenHead(documentPath: String, listener: (CoreV2Snapshot?, Throwable?) -> Unit): CoreV2Registration
}
internal object CoreV2Wire {
    private val idPattern = Regex("[0-9a-f]{32}")
    private val tablePattern = Regex("[a-z][a-z0-9_]*")
    private fun invalid(): Nothing = throw PlatformException.Other("Malformed v2 document")
    fun string(fields: Map<String, Any?>, key: String): String = (fields[key] as? String)?.takeIf { it.isNotEmpty() } ?: invalid()
    private fun integer(fields: Map<String, Any?>, key: String): UInt {
        val value = fields[key] as? Long ?: invalid()
        if (value < 0 || value > UInt.MAX_VALUE.toLong()) invalid()
        return value.toUInt()
    }
    fun timestamp(fields: Map<String, Any?>, key: String): RemoteTime = (fields[key] as? RemoteTime)?.also {
        if (it.nanos >= 1_000_000_000u) invalid()
    } ?: invalid()
    fun crypto(document: CoreV2Document): CryptoParams {
        val f = document.fields; timestamp(f, "createdAt")
        return CryptoParams(string(f, "kdf"), string(f, "salt"), integer(f, "iterations"), string(f, "cipher"), string(f, "keyCheck"), integer(f, "v"))
    }
    fun cryptoFields(params: CryptoParams): Map<String, Any?> = mapOf("kdf" to params.kdf, "salt" to params.salt,
        "iterations" to params.iterations.toLong(), "cipher" to params.cipher, "keyCheck" to params.keyCheck, "v" to params.version.toLong(), "createdAt" to CoreServerTimestamp)
    fun head(document: CoreV2Document): RemoteCursor {
        val id = string(document.fields, "lastDocId")
        if (!Regex("[a-z][a-z0-9_]*_[0-9a-f]{32}").matches(id) || !idPattern.matches(string(document.fields, "deviceId"))) invalid()
        return RemoteCursor(timestamp(document.fields, "updatedAt"), id)
    }
    fun compare(a: RemoteCursor, b: RemoteCursor): Int = compareValues(a.time.seconds, b.time.seconds).takeIf { it != 0 }
        ?: compareValues(a.time.nanos, b.time.nanos).takeIf { it != 0 } ?: a.docId.compareTo(b.docId)
    fun docId(record: RemoteRecord): String {
        if (!idPattern.matches(record.id) || !tablePattern.matches(record.table) || !idPattern.matches(record.deviceId)) invalid()
        return "${record.table}_${record.id}"
    }
    fun record(document: CoreV2Document): RemoteRecord {
        val f = document.fields; val table = string(f, "table"); val id = string(f, "id")
        val deleted = f["deleted"] as? Boolean ?: invalid()
        val payload = if (deleted) null else string(f, "payload")
        val record = RemoteRecord(table, id, deleted, payload, string(f, "deviceId"), integer(f, "v"), timestamp(f, "updatedAt"))
        if (document.id != docId(record)) invalid()
        if (deleted && f.containsKey("payload")) invalid()
        return record
    }
    fun recordFields(record: RemoteRecord): Map<String, Any?> {
        docId(record)
        if ((!record.deleted && record.payload.isNullOrEmpty()) || (record.deleted && record.payload != null)) invalid()
        return buildMap {
            put("table", record.table); put("id", record.id); put("deleted", record.deleted); put("deviceId", record.deviceId)
            put("v", record.version.toLong()); put("updatedAt", CoreServerTimestamp)
            if (!record.deleted) put("payload", record.payload)
        }
    }
}
