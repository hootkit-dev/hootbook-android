package io.github.hootkit_dev.hootbook.data.sync

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.hootkit_dev.hootbook.data.database.SyncTriggers
import io.github.hootkit_dev.hootbook.data.sync.SyncSchema.Kind
import java.math.BigDecimal
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Rows of the synced tables as payloads and back, on the raw database: what the wire protocol
 * carries is the stored value of each column (docs/sync.md), so nothing goes through entities or
 * type converters. Writes here run inside the engine's transaction with capture paused.
 */
internal class SyncLocalStore(private val db: SupportSQLiteDatabase) {

    /** A reference that cannot be resolved yet: the record waits for [docId]. */
    class Missing(val docId: String) : Exception(docId)

    /** Resolves a reference to a sync id this device does not hold: an alias, gone, or unknown (null). */
    fun interface Fallback {
        fun resolve(table: String, syncId: String): Resolution?
    }

    sealed class Resolution {
        /** The record is now [syncId] (it was a duplicate that another record replaced) */
        data class Alias(val syncId: String) : Resolution()
        /** The record was deleted */
        data object Gone : Resolution()
    }

    // ---- reading -------------------------------------------------------------------------

    /** The payload of record [syncId] of [table], or null when no row holds that id. */
    fun encode(table: SyncSchema.Table, syncId: String): JsonObject? =
        db.query("SELECT * FROM `${table.name}` WHERE sync_id = ? LIMIT 1", arrayOf<Any>(syncId)).use { c ->
            if (!c.moveToFirst()) return null
            JsonObject(table.synced.associate { column -> column.name to read(c, column) })
        }

    private fun read(c: Cursor, column: SyncSchema.Column): JsonElement {
        val i = c.getColumnIndexOrThrow(column.name)
        if (c.isNull(i)) return JsonNull
        return when (column.kind) {
            Kind.INT -> JsonPrimitive(c.getLong(i))
            Kind.BOOL -> JsonPrimitive(c.getLong(i) != 0L)
            Kind.DECIMAL -> JsonPrimitive(plainDecimal(c.getString(i)))
            Kind.REF -> syncIdOf(column.ref!!, c.getLong(i))?.let(::JsonPrimitive) ?: JsonNull
            Kind.TEXT, Kind.DATETIME, Kind.DATE -> JsonPrimitive(c.getString(i))
            Kind.LOCAL -> JsonNull
        }
    }

    private fun plainDecimal(value: String): String =
        runCatching { BigDecimal(value.trim()).toPlainString() }.getOrDefault(value)

    fun syncIdOf(table: String, id: Long): String? =
        db.query("SELECT sync_id FROM `$table` WHERE id = ?", arrayOf<Any>(id)).use { if (it.moveToFirst()) it.getString(0) else null }
            ?.takeIf { it.isNotEmpty() }

    fun rowIdOf(table: String, syncId: String): Long? =
        db.query("SELECT rowid FROM `$table` WHERE sync_id = ? LIMIT 1", arrayOf<Any>(syncId))
            .use { if (it.moveToFirst()) it.getLong(0) else null }

    fun syncIdsOf(table: String): List<String> =
        db.query("SELECT sync_id FROM `$table`").use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    fun count(sql: String): Long = db.query(sql).use { if (it.moveToFirst()) it.getLong(0) else 0 }

    /** One currency of one account, as balance rows name it. */
    data class Pocket(val bankName: String, val accountLast4: String, val currency: String)

    /** The pocket and stored time of balance row [rowId]. */
    fun balancePlace(rowId: Long): Pair<Pocket, String>? =
        db.query("SELECT bank_name, account_last4, currency, timestamp FROM account_balances WHERE rowid = ?", arrayOf<Any>(rowId))
            .use { if (it.moveToFirst()) Pocket(it.getString(0), it.getString(1), it.getString(2)) to it.getString(3) else null }

    /** The latest balance row of [pocket] strictly before [time] (stored form), as (time, balance). */
    fun balanceBefore(pocket: Pocket, time: String): Pair<String, String>? =
        db.query(
            "SELECT timestamp, balance FROM account_balances WHERE bank_name = ? AND account_last4 = ? AND currency = ? " +
                "AND timestamp < ? ORDER BY timestamp DESC, id DESC LIMIT 1",
            arrayOf<Any>(pocket.bankName, pocket.accountLast4, pocket.currency, time)
        ).use { if (it.moveToFirst()) it.getString(0) to it.getString(1) else null }

    /** The earliest balance row of [pocket] at or after [time], as (time, balance). */
    fun balanceFrom(pocket: Pocket, time: String): Pair<String, String>? =
        db.query(
            "SELECT timestamp, balance FROM account_balances WHERE bank_name = ? AND account_last4 = ? AND currency = ? " +
                "AND timestamp >= ? ORDER BY timestamp ASC, id ASC LIMIT 1",
            arrayOf<Any>(pocket.bankName, pocket.accountLast4, pocket.currency, time)
        ).use { if (it.moveToFirst()) it.getString(0) to it.getString(1) else null }

    // ---- writing -------------------------------------------------------------------------

    /**
     * The column values of a payload, with references mapped to local ids. Columns the payload
     * lacks, and LOCAL ones, are left out (an update keeps them, an insert takes the default).
     * Throws [Missing] when a referenced record is not here and [fallback] does not know it.
     */
    fun decode(table: SyncSchema.Table, payload: JsonObject, fallback: Fallback): ContentValues {
        val values = ContentValues()
        for (column in table.synced) {
            if (!payload.containsKey(column.name)) continue
            val element = payload[column.name]!!
            if (element is JsonNull) {
                if (column.nullable) values.putNull(column.name)
                continue
            }
            val primitive = element as? JsonPrimitive ?: continue
            when (column.kind) {
                Kind.INT -> (primitive.longOrNull ?: primitive.content.toLongOrNull())?.let { values.put(column.name, it) }
                Kind.BOOL -> {
                    val flag = primitive.booleanOrNull ?: primitive.longOrNull?.let { it != 0L } ?: continue
                    values.put(column.name, if (flag) 1L else 0L)
                }
                Kind.REF -> {
                    val local = resolve(column.ref!!, primitive.content, fallback)
                    if (local == null) {
                        if (!column.nullable) throw Gone()
                        values.putNull(column.name)
                    } else values.put(column.name, local)
                }
                Kind.TEXT, Kind.DATETIME, Kind.DATE, Kind.DECIMAL -> values.put(column.name, primitive.content)
                Kind.LOCAL -> Unit
            }
        }
        return values
    }

    /** Thrown by [decode] when a required parent was deleted: the record cannot exist. */
    class Gone : Exception()

    /** The local id of [syncId] in [table], following aliases; null when gone; throws [Missing] when unknown. */
    private fun resolve(table: String, syncId: String, fallback: Fallback): Long? {
        var id = syncId
        repeat(8) {
            rowIdOf(table, id)?.let { return it }
            // A duplicate this device resolved itself but has not sent yet
            val local = db.query(
                "SELECT replaced_by FROM sync_outbox WHERE table_name = ? AND sync_id = ? AND op = ?",
                arrayOf<Any>(table, id, SyncTriggers.OP_DELETE)
            ).use { if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null }
            if (local != null) { id = local; return@repeat }
            when (val r = fallback.resolve(table, id)) {
                is Resolution.Alias -> id = r.syncId
                Resolution.Gone -> return null
                null -> throw Missing(SyncSchema.docId(table, id))
            }
        }
        throw Missing(SyncSchema.docId(table, id))
    }

    /** The row (rowid, sync id) other than [except] that holds a natural key of [table] with [values]. */
    fun conflicting(table: SyncSchema.Table, values: ContentValues, existing: Long?): Pair<Long, String>? {
        val current = existing?.let { row(table.name, it) }
        for (key in table.uniqueKeys) {
            val keyValues = key.map { name -> if (values.containsKey(name)) values.get(name) else current?.get(name) }
            if (keyValues.any { it == null }) continue
            val where = key.joinToString(" AND ") { "`$it` = ?" }
            val args = keyValues.map { it!! }.toMutableList<Any>()
            val sql = "SELECT rowid, sync_id FROM `${table.name}` WHERE $where" + if (existing != null) " AND rowid <> ?" else ""
            if (existing != null) args += existing
            db.query("$sql LIMIT 1", args.toTypedArray()).use { if (it.moveToFirst()) return it.getLong(0) to it.getString(1) }
        }
        return null
    }

    private fun row(table: String, rowId: Long): Map<String, Any?>? =
        db.query("SELECT * FROM `$table` WHERE rowid = ?", arrayOf<Any>(rowId)).use { c ->
            if (!c.moveToFirst()) return null
            (0 until c.columnCount).associate { i ->
                c.getColumnName(i) to when (c.getType(i)) {
                    Cursor.FIELD_TYPE_NULL -> null
                    Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                    Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                    else -> c.getString(i)
                }
            }
        }

    fun insert(table: String, syncId: String, values: ContentValues) {
        values.put("sync_id", syncId)
        db.insert(table, SQLiteDatabase.CONFLICT_ABORT, values)
    }

    fun update(table: String, rowId: Long, values: ContentValues) {
        if (values.size() == 0) return
        db.update(table, SQLiteDatabase.CONFLICT_ABORT, values, "rowid = ?", arrayOf<Any>(rowId))
    }

    fun setSyncId(table: String, rowId: Long, syncId: String) {
        db.execSQL("UPDATE `$table` SET sync_id = ? WHERE rowid = ?", arrayOf<Any>(syncId, rowId))
    }

    fun delete(table: String, rowId: Long) {
        db.execSQL("DELETE FROM `$table` WHERE rowid = ?", arrayOf<Any>(rowId))
    }

    /**
     * Makes row [from] of [table] one with row [into]: what referred to [from] refers to [into]
     * (a reference that would duplicate a natural key is left and goes with [from]), then [from]
     * is deleted.
     */
    fun mergeRows(table: String, from: Long, into: Long) {
        if (from == into) return
        for (child in SyncSchema.TABLES) for (column in child.refs.filter { it.ref == table }) {
            db.execSQL("UPDATE OR IGNORE `${child.name}` SET `${column.name}` = ? WHERE `${column.name}` = ?", arrayOf<Any>(into, from))
        }
        delete(table, from)
    }

    /** Queues a change by hand (capture is paused while remote changes are applied). */
    fun queue(table: String, syncId: String, op: String, replacedBy: String? = null, at: Long = System.currentTimeMillis()) {
        db.execSQL("DELETE FROM sync_outbox WHERE table_name = ? AND sync_id = ?", arrayOf<Any>(table, syncId))
        db.execSQL(
            "INSERT INTO sync_outbox (table_name, sync_id, op, queued_at, replaced_by) VALUES (?, ?, ?, ?, ?)",
            arrayOf<Any?>(table, syncId, op, at, replacedBy)
        )
    }

    fun pendingOp(table: String, syncId: String): String? =
        db.query("SELECT op FROM sync_outbox WHERE table_name = ? AND sync_id = ?", arrayOf<Any>(table, syncId))
            .use { if (it.moveToFirst()) it.getString(0) else null }

    fun setApplyingRemote(applying: Boolean) {
        db.execSQL("UPDATE sync_control SET applying_remote = ? WHERE id = 1", arrayOf<Any>(if (applying) 1 else 0))
    }
}
