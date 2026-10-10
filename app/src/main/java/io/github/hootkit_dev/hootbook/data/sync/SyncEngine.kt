package io.github.hootkit_dev.hootbook.data.sync

import android.util.Log
import io.github.hootkit_dev.hootbook.data.database.HootbookDatabase
import io.github.hootkit_dev.hootbook.data.database.SyncTriggers
import io.github.hootkit_dev.hootbook.data.database.entity.SyncInboxEntity
import io.github.hootkit_dev.hootbook.data.database.entity.SyncOutboxEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Where the last pull stopped; kept by the app in preferences, by tests in memory. */
interface SyncCursorStore {
    var cursor: RemoteCursor?
}

/** What a pull did. */
data class PullResult(
    val applied: Int,
    /** Records still waiting (for a parent, or for a newer app) */
    val held: Int,
    /** Of those, the ones written by a newer protocol version */
    val newerVersion: Int,
    /** Records that could not be decrypted or read */
    val unreadable: Int,
    /** Document ids of the live records seen */
    val seen: Set<String>,
)

/** The state of a first sync, which decides what it does (docs/sync.md, "First sync"). */
enum class FirstSyncCase {
    /** Nothing in the cloud: upload everything here */
    CLOUD_EMPTY,
    /** The cloud has data and this device only the built-in categories: take the cloud's */
    LOCAL_PRISTINE,
    /** Both have data: the user chooses between replacing this device's data and merging */
    BOTH_HAVE_DATA,
}

/**
 * Moves changes between this device's database and the remote store (docs/sync.md): [push] sends
 * the outbox, [pull] applies what other devices wrote. Remote changes are written verbatim with
 * capture paused, never through the use cases, so balances are copied rather than recomputed.
 * Not thread-safe: the caller runs one operation at a time.
 */
class SyncEngine(
    private val database: HootbookDatabase,
    private val remote: RemoteStore,
    private val key: ByteArray,
    private val deviceId: String,
    private val cursorStore: SyncCursorStore,
    private val pageSize: Int = 300,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val sql get() = database.openHelper.writableDatabase
    private val store get() = SyncLocalStore(sql)

    // ---- push ----------------------------------------------------------------------------

    /** Sends every queued change; returns how many records were written. */
    suspend fun push(): Int = withContext(Dispatchers.IO) {
        var written = 0
        repeat(MAX_ROUNDS) {
            val entries = database.syncDao().pending(PUSH_BATCH)
            if (entries.isEmpty()) return@withContext written
            val records = entries.mapNotNull(::record).sortedBy(::order)
            records.chunked(WRITE_BATCH).forEach { chunk -> remote.write(chunk) }
            // Entries whose row is gone (or whose table is not synced) are dropped with the rest
            entries.forEach { database.syncDao().markSent(it) }
            written += records.size
        }
        written
    }

    /** Upserts parents first, then deletes children first. */
    private fun order(record: RemoteRecord): Int {
        val level = SyncSchema.table(record.table)?.level ?: 0
        return if (record.deleted) 100 - level else level
    }

    private fun record(entry: SyncOutboxEntity): RemoteRecord? {
        val table = SyncSchema.table(entry.tableName) ?: return null
        val docId = SyncSchema.docId(table.name, entry.syncId)
        if (entry.op == SyncTriggers.OP_DELETE) {
            return RemoteRecord(table.name, entry.syncId, deleted = true, payload = null, deviceId = deviceId, replacedBy = entry.replacedBy)
        }
        val payload = store.encode(table, entry.syncId) ?: return null
        val sealed = SyncCrypto.seal(key, payload.toString().toByteArray(Charsets.UTF_8), docId)
        return RemoteRecord(table.name, entry.syncId, deleted = false, payload = sealed, deviceId = deviceId)
    }

    // ---- pull ----------------------------------------------------------------------------

    /** Applies every record written since the last pull, page by page, then retries held ones. */
    suspend fun pull(): PullResult = withContext(Dispatchers.IO) {
        var applied = 0
        var unreadable = 0
        val seen = mutableSetOf<String>()
        touched.clear()
        while (true) {
            val page = remote.fetchAfter(cursorStore.cursor, pageSize)
            if (page.isEmpty()) break
            page.filter { !it.deleted }.forEach { seen += it.docId }
            val outcome = applyPage(page.map(::Incoming))
            applied += outcome.applied
            unreadable += outcome.unreadable
            val next = page.mapNotNull { it.cursor }.maxOrNull()
            if (next == null || next == cursorStore.cursor) break
            cursorStore.cursor = next
            if (page.size < pageSize) break
        }
        val retried = retryHeld()
        replayTouchedBalances()
        val held = database.syncDao().inbox()
        PullResult(applied + retried, held.size, held.count { it.waitingFor == WAITING_VERSION }, unreadable, seen)
    }

    /** A record to apply, from the server or from the inbox. */
    private class Incoming(
        val table: String,
        val syncId: String,
        val deleted: Boolean,
        val payload: String?,
        val deviceId: String,
        val version: Int,
        val replacedBy: String?,
        val updatedAt: RemoteTime,
    ) {
        constructor(r: RemoteRecord) : this(
            r.table, r.syncId, r.deleted, r.payload, r.deviceId, r.version, r.replacedBy, r.updatedAt ?: RemoteTime(0, 0)
        )
        constructor(e: SyncInboxEntity) : this(
            e.tableName, e.syncId, e.deleted, e.payload, e.deviceId, e.version, e.replacedBy, RemoteTime(e.updatedSeconds, e.updatedNanos)
        )
        val docId get() = SyncSchema.docId(table, syncId)
        fun held(waitingFor: String) = SyncInboxEntity(
            docId, table, syncId, deleted, payload, deviceId, version, replacedBy, updatedAt.seconds, updatedAt.nanos, waitingFor
        )
    }

    private class PageOutcome(val applied: Int, val unreadable: Int, val waiting: Map<String, String>)

    private sealed class Applied {
        data object Done : Applied()
        data object Unreadable : Applied()
        data class Waiting(val reason: String) : Applied()
    }

    /** Remote references this run learned about: replaced records and deleted ones. */
    private val aliases = mutableMapOf<String, String>()
    private val gone = mutableSetOf<String>()
    /** Local records the server confirmed alive (or never had): a clash with one is a duplicate. */
    private val confirmed = mutableSetOf<String>()

    private val fallback = SyncLocalStore.Fallback { table, syncId ->
        val docId = SyncSchema.docId(table, syncId)
        aliases[docId]?.let { SyncLocalStore.Resolution.Alias(it) }
            ?: if (docId in gone) SyncLocalStore.Resolution.Gone else null
    }

    /**
     * Applies [records] in one transaction with capture paused: deletes first (children first),
     * then upserts (parents first), so a record deleted and re-made under the same natural key in
     * one change does not clash with itself. A record that cannot be applied yet goes to the
     * inbox; one applied leaves it.
     */
    private fun applyPage(records: List<Incoming>): PageOutcome {
        var applied = 0
        var unreadable = 0
        val waiting = mutableMapOf<String, String>()
        val ordered = records.sortedBy { r ->
            val level = SyncSchema.table(r.table)?.level ?: 0
            if (r.deleted) -level else 100 + level
        }
        database.runInTransaction {
            val local = store
            local.setApplyingRemote(true)
            try {
                for (record in ordered) {
                    when (val result = apply(local, record)) {
                        Applied.Done -> { applied++; release(record.docId) }
                        Applied.Unreadable -> { unreadable++; release(record.docId) }
                        is Applied.Waiting -> {
                            waiting[record.docId] = result.reason
                            sql.execSQL(
                                "INSERT OR REPLACE INTO sync_inbox (doc_id, table_name, sync_id, deleted, payload, device_id, " +
                                    "version, replaced_by, updated_seconds, updated_nanos, waiting_for) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                                record.held(result.reason).let {
                                    arrayOf<Any?>(it.docId, it.tableName, it.syncId, if (it.deleted) 1 else 0, it.payload, it.deviceId,
                                        it.version, it.replacedBy, it.updatedSeconds, it.updatedNanos, it.waitingFor)
                                }
                            )
                        }
                    }
                }
            } finally {
                local.setApplyingRemote(false)
            }
        }
        return PageOutcome(applied, unreadable, waiting)
    }

    /** Per pocket, the earliest stored time a remote balance change touched in this pull. */
    private val touched = mutableMapOf<SyncLocalStore.Pocket, String>()

    private fun touch(place: Pair<SyncLocalStore.Pocket, String>) {
        val (pocket, time) = place
        touched[pocket] = touched[pocket]?.let { minOf(it, time) } ?: time
    }

    /**
     * Each balance row holds the balance after its change, worked out on the device that wrote it.
     * Two devices adding to one account while apart each work from the balance they knew, so the
     * rows they copy to each other disagree. After a pull, every pocket a remote balance row
     * touched is worked out again from the row before the earliest touched time, the way a
     * back-dated entry is (stopping at a balance calibration). It runs with capture on, so a row
     * that changes is uploaded and every device ends on the same numbers.
     */
    private suspend fun replayTouchedBalances() {
        if (touched.isEmpty()) return
        val dao = database.accountBalanceDao()
        val local = store
        for ((pocket, time) in touched.toList()) {
            val anchor = local.balanceBefore(pocket, time) ?: local.balanceFrom(pocket, time) ?: continue
            val anchorTime = converters.toLocalDateTime(anchor.first) ?: continue
            val anchorBalance = anchor.second.toBigDecimalOrNull() ?: continue
            dao.recalculateBalancesAfter(pocket.bankName, pocket.accountLast4, anchorTime, anchorBalance, pocket.currency)
        }
        touched.clear()
    }

    private val converters = io.github.hootkit_dev.hootbook.data.database.converter.Converters()

    private fun release(docId: String) = sql.execSQL("DELETE FROM sync_inbox WHERE doc_id = ?", arrayOf<Any>(docId))

    private fun apply(local: SyncLocalStore, record: Incoming): Applied {
        val table = SyncSchema.table(record.table) ?: return Applied.Unreadable
        if (record.version > SyncSchema.VERSION) return Applied.Waiting(WAITING_VERSION)
        // A change made here and not sent yet wins: it reaches the server later, so it is the last write
        if (local.pendingOp(table.name, record.syncId) != null) return Applied.Done
        if (table.name == BALANCES) local.rowIdOf(table.name, record.syncId)?.let { row -> local.balancePlace(row)?.let(::touch) }
        val result = applyRecord(local, table, record)
        if (table.name == BALANCES && result == Applied.Done) {
            local.rowIdOf(table.name, record.syncId)?.let { row -> local.balancePlace(row)?.let(::touch) }
        }
        return result
    }

    private fun applyRecord(local: SyncLocalStore, table: SyncSchema.Table, record: Incoming): Applied {
        if (record.deleted) {
            applyDelete(local, table, record.syncId, record.replacedBy)
            return Applied.Done
        }
        val payload: JsonObject = try {
            val plain = SyncCrypto.open(key, record.payload ?: return Applied.Unreadable, record.docId)
            json.parseToJsonElement(plain.toString(Charsets.UTF_8)).jsonObject
        } catch (e: Exception) {
            Log.w(TAG, "Unreadable record ${record.docId}", e)
            return Applied.Unreadable
        }
        val values = try {
            local.decode(table, payload, fallback)
        } catch (e: SyncLocalStore.Missing) {
            return Applied.Waiting(e.docId)
        } catch (e: SyncLocalStore.Gone) {
            // Its parent was deleted, so it was too (the parent's delete cascades)
            local.rowIdOf(table.name, record.syncId)?.let { local.delete(table.name, it) }
            return Applied.Done
        }
        return applyUpsert(local, table, record.syncId, values)
    }

    /**
     * Writes a record by sync id. When another row holds one of the table's natural keys, the two
     * are one record: the smaller sync id survives with its own content, and the other is deleted
     * everywhere with a tombstone naming the survivor (docs/sync.md, "Duplicates").
     */
    private fun applyUpsert(local: SyncLocalStore, table: SyncSchema.Table, syncId: String, values: android.content.ContentValues): Applied {
        var existing = local.rowIdOf(table.name, syncId)
        val conflict = local.conflicting(table, values, existing)
        if (conflict != null) {
            val (otherRow, otherId) = conflict
            // The clashing row may be one the server has already deleted (deleted and made again in
            // one change, the delete not read yet): unless it has a change of its own queued here,
            // ask the server about it before treating the two as duplicates
            val otherDoc = SyncSchema.docId(table.name, otherId)
            if (otherDoc !in confirmed && local.pendingOp(table.name, otherId) == null) {
                return Applied.Waiting(WAITING_DUPLICATE + otherDoc)
            }
            if (syncId < otherId) {
                if (existing != null) local.mergeRows(table.name, from = otherRow, into = existing)
                else { local.setSyncId(table.name, otherRow, syncId); existing = otherRow }
                local.queue(table.name, otherId, SyncTriggers.OP_DELETE, replacedBy = syncId)
            } else {
                if (existing != null) local.mergeRows(table.name, from = existing, into = otherRow)
                local.queue(table.name, syncId, SyncTriggers.OP_DELETE, replacedBy = otherId)
                local.queue(table.name, otherId, SyncTriggers.OP_UPSERT)
                return Applied.Done
            }
        }
        if (existing != null) local.update(table.name, existing, values) else local.insert(table.name, syncId, values)
        return Applied.Done
    }

    private fun applyDelete(local: SyncLocalStore, table: SyncSchema.Table, syncId: String, replacedBy: String?) {
        val row = local.rowIdOf(table.name, syncId) ?: return
        if (replacedBy == null) {
            local.delete(table.name, row)
            return
        }
        val survivor = local.rowIdOf(table.name, replacedBy)
        if (survivor != null) local.mergeRows(table.name, from = row, into = survivor)
        // The survivor's own record arrives (or arrived) with its content
        else local.setSyncId(table.name, row, replacedBy)
    }

    /**
     * Retries held records until none moves, asking the server about each missing parent once: a
     * live parent is applied at once, a replaced one is followed, a deleted one lets the child go
     * without it (or with it, when the child cannot exist alone). For a natural-key clash it asks
     * about the local record: deleted there, its tombstone is applied; otherwise the two are
     * duplicates.
     */
    private suspend fun retryHeld(): Int {
        var applied = 0
        val asked = mutableSetOf<String>()
        repeat(MAX_ROUNDS) {
            val held = database.syncDao().inbox()
            if (held.isEmpty()) return applied
            val before = held.size
            val outcome = applyPage(held.map(::Incoming))
            applied += outcome.applied
            val missing = outcome.waiting.values.filter { it != WAITING_VERSION && it !in asked }.toSet()
            if (missing.isEmpty() && database.syncDao().inbox().size >= before) return applied
            for (reason in missing) {
                asked += reason
                if (reason.startsWith(WAITING_DUPLICATE)) {
                    val docId = reason.removePrefix(WAITING_DUPLICATE)
                    val other = remote.get(docId)
                    if (other != null && other.deleted) applied += applyPage(listOf(Incoming(other))).applied
                    else confirmed += docId
                    continue
                }
                val docId = reason
                val parent = remote.get(docId)
                when {
                    parent == null -> Unit
                    !parent.deleted -> applied += applyPage(listOf(Incoming(parent))).applied
                    parent.replacedBy != null -> aliases[docId] = parent.replacedBy
                    else -> gone += docId
                }
            }
        }
        return applied
    }

    // ---- first sync ----------------------------------------------------------------------

    /** Which first sync this is (docs/sync.md, "First sync"). */
    suspend fun firstSyncCase(): FirstSyncCase = when {
        remote.isEmpty() -> FirstSyncCase.CLOUD_EMPTY
        isLocalPristine() -> FirstSyncCase.LOCAL_PRISTINE
        else -> FirstSyncCase.BOTH_HAVE_DATA
    }

    /** True when this device holds nothing but the built-in categories and subcategories. */
    suspend fun isLocalPristine(): Boolean = withContext(Dispatchers.IO) {
        val local = store
        SyncSchema.TABLES.filter { it.name != "categories" && it.name != "subcategories" }
            .all { local.count("SELECT COUNT(*) FROM `${it.name}`") == 0L } &&
            local.count("SELECT COUNT(*) FROM categories WHERE is_system = 0") == 0L &&
            local.count("SELECT COUNT(*) FROM subcategories WHERE is_system = 0") == 0L
    }

    /** Cloud empty: uploads every record here. */
    suspend fun uploadAll() {
        resetLocalSyncState()
        enqueueAll()
        push()
    }

    /** Replaces this device's data with the cloud's (the caller backs it up first when it matters). */
    suspend fun replaceLocalWithCloud(): PullResult {
        // Fails offline, before anything here is deleted
        remote.isEmpty()
        withContext(Dispatchers.IO) {
            database.runInTransaction {
                val local = store
                local.setApplyingRemote(true)
                try {
                    SyncSchema.TABLES.sortedByDescending { it.level }.forEach { sql.execSQL("DELETE FROM `${it.name}`") }
                } finally {
                    local.setApplyingRemote(false)
                }
            }
        }
        resetLocalSyncState()
        return pull()
    }

    /**
     * Keeps both: applies the cloud's records first (a record both have, by sync id or natural key,
     * becomes one), then uploads what only this device has.
     */
    suspend fun merge(): PullResult {
        resetLocalSyncState()
        val result = pull()
        enqueueAll(except = result.seen)
        push()
        return result
    }

    /** Forgets queued changes, held records and the cursor: a first sync starts from scratch. */
    suspend fun resetLocalSyncState() = withContext(Dispatchers.IO) {
        database.syncDao().clearOutbox()
        database.syncDao().clearInbox()
        cursorStore.cursor = null
        aliases.clear()
        gone.clear()
        confirmed.clear()
    }

    /** Queues an upload of every record here (parents first), except the documents in [except]. */
    suspend fun enqueueAll(except: Set<String> = emptySet()) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        database.runInTransaction {
            val local = store
            SyncSchema.TABLES.forEach { table ->
                local.syncIdsOf(table.name)
                    .filter { it.isNotEmpty() && SyncSchema.docId(table.name, it) !in except }
                    .forEach { local.queue(table.name, it, SyncTriggers.OP_UPSERT, at = now + table.level) }
            }
        }
    }

    companion object {
        private const val TAG = "SyncEngine"
        private const val BALANCES = "account_balances"
        private const val PUSH_BATCH = 400
        // Firestore commits at most 500 writes at once
        private const val WRITE_BATCH = 400
        private const val MAX_ROUNDS = 200
        const val WAITING_VERSION = "version"
        /** Prefix of `waiting_for` for a record that clashes with a local one: then that one's document id */
        const val WAITING_DUPLICATE = "duplicate:"
    }
}
