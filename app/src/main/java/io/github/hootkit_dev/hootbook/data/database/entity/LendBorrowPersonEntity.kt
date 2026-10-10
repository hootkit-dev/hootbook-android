package io.github.hootkit_dev.hootbook.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * Entity representing a person in the Lendings & Borrowings (Khata) ledger.
 */
@Entity(tableName = "lend_borrow_persons", indices = [Index(value = ["sync_id"])])
data class LendBorrowPersonEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "phone_number")
    val phoneNumber: String? = null,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "color")
    val color: String = "#4CAF50",

    @ColumnInfo(name = "avatar")
    val avatar: String? = null,

    @ColumnInfo(name = "category")
    val category: String? = null,

    @ColumnInfo(name = "is_archived", defaultValue = "0")
    val isArchived: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    // Sync identity and last local change (epoch millis), kept by SyncTriggers; see docs/sync.md
    @ColumnInfo(name = "sync_id", defaultValue = "''") val syncId: String = "",
    @ColumnInfo(name = "sync_updated_at", defaultValue = "0") val syncUpdatedAt: Long = 0
)
