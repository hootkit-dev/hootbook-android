package io.github.hootkit_dev.hootbook.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A synced record that changed here and has not been sent yet: one row per record, the latest
 * change wins. Written only by the triggers of SyncTriggers; see docs/sync.md.
 */
@Entity(tableName = "sync_outbox", primaryKeys = ["table_name", "sync_id"])
data class SyncOutboxEntity(
    @ColumnInfo(name = "table_name") val tableName: String,
    @ColumnInfo(name = "sync_id") val syncId: String,
    /** SyncTriggers.OP_UPSERT or SyncTriggers.OP_DELETE */
    @ColumnInfo(name = "op") val op: String,
    /** Epoch millis */
    @ColumnInfo(name = "queued_at") val queuedAt: Long,
    /**
     * For a DELETE that resolved a duplicate (two records with one natural key, see docs/sync.md):
     * the sync id of the record that replaces this one. Null otherwise; the triggers never set it.
     */
    @ColumnInfo(name = "replaced_by") val replacedBy: String? = null
)

/**
 * The single row (id 1) that switches change capture. While [applyingRemote] is set, the sync
 * triggers neither stamp rows nor queue them: it is set, inside one transaction, while changes
 * from another device are written. [capturing] is the triggers' own guard against capturing the
 * row update they make.
 */
@Entity(tableName = "sync_control")
data class SyncControlEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: Int = 1,
    @ColumnInfo(name = "applying_remote", defaultValue = "0") val applyingRemote: Boolean = false,
    @ColumnInfo(name = "capturing", defaultValue = "0") val capturing: Boolean = false
)

/**
 * A remote record that could not be applied yet, kept until it can: a child whose parent has not
 * arrived, or a record written by a newer protocol version. Fields are the document's as received
 * (docs/sync.md, "Wire protocol"); the payload stays encrypted.
 */
@Entity(tableName = "sync_inbox")
data class SyncInboxEntity(
    /** `{table}_{syncId}` */
    @PrimaryKey @ColumnInfo(name = "doc_id") val docId: String,
    @ColumnInfo(name = "table_name") val tableName: String,
    @ColumnInfo(name = "sync_id") val syncId: String,
    @ColumnInfo(name = "deleted") val deleted: Boolean,
    @ColumnInfo(name = "payload") val payload: String?,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "version") val version: Int,
    @ColumnInfo(name = "replaced_by") val replacedBy: String?,
    @ColumnInfo(name = "updated_seconds") val updatedSeconds: Long,
    @ColumnInfo(name = "updated_nanos") val updatedNanos: Int,
    /** Why it waits: the doc id of a missing parent, or "version" */
    @ColumnInfo(name = "waiting_for") val waitingFor: String
)
