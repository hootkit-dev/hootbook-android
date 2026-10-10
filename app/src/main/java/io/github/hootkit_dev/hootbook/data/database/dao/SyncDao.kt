package io.github.hootkit_dev.hootbook.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.hootkit_dev.hootbook.data.database.entity.SyncInboxEntity
import io.github.hootkit_dev.hootbook.data.database.entity.SyncOutboxEntity
import kotlinx.coroutines.flow.Flow

/** The sync outbox and switch; see docs/sync.md. */
@Dao
interface SyncDao {
    /** Changes waiting to be sent, oldest first. */
    @Query("SELECT * FROM sync_outbox ORDER BY queued_at, table_name, sync_id LIMIT :limit")
    suspend fun pending(limit: Int = 500): List<SyncOutboxEntity>

    @Query("SELECT COUNT(*) FROM sync_outbox")
    suspend fun pendingCount(): Int

    @Query("SELECT COUNT(*) FROM sync_outbox")
    fun pendingCountFlow(): Flow<Int>

    @Query("SELECT * FROM sync_outbox WHERE table_name = :tableName AND sync_id = :syncId")
    suspend fun pendingEntry(tableName: String, syncId: String): SyncOutboxEntity?

    /** Queues a change by hand (the triggers queue every ordinary one). Replaces the record's entry. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun queue(entry: SyncOutboxEntity)

    @Query("DELETE FROM sync_outbox")
    suspend fun clearOutbox()

    /** Remote records waiting to be applied, oldest first. */
    @Query("SELECT * FROM sync_inbox ORDER BY updated_seconds, updated_nanos, doc_id")
    suspend fun inbox(): List<SyncInboxEntity>

    @Query("SELECT COUNT(*) FROM sync_inbox")
    fun inboxCountFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hold(entry: SyncInboxEntity)

    @Query("DELETE FROM sync_inbox WHERE doc_id = :docId")
    suspend fun release(docId: String)

    @Query("DELETE FROM sync_inbox")
    suspend fun clearInbox()

    /**
     * Removes an entry once it has been sent, unless the record changed again since it was read
     * (its entry then has a newer op or time and stays queued).
     */
    @Query(
        "DELETE FROM sync_outbox WHERE table_name = :tableName AND sync_id = :syncId " +
            "AND op = :op AND queued_at = :queuedAt"
    )
    suspend fun markSent(tableName: String, syncId: String, op: String, queuedAt: Long): Int

    suspend fun markSent(entry: SyncOutboxEntity): Int =
        markSent(entry.tableName, entry.syncId, entry.op, entry.queuedAt)

    @Query("SELECT applying_remote FROM sync_control WHERE id = 1")
    suspend fun isApplyingRemote(): Boolean?

    /**
     * Pauses (true) or resumes change capture. Set it only inside the transaction that writes
     * remote changes: every other write in between would go uncaptured too.
     */
    @Query("INSERT OR REPLACE INTO sync_control (id, applying_remote, capturing) VALUES (1, :applying, 0)")
    suspend fun setApplyingRemote(applying: Boolean)
}
