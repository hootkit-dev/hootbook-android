package io.github.hootkit_dev.hootbook.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Entity representing an individual entry (Lending, Borrowing, or Settlement) for a person.
 */
@Entity(
    tableName = "lend_borrow_transactions",
    foreignKeys = [
        ForeignKey(
            entity = LendBorrowPersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sync_id"]),
        Index(value = ["person_id"]),
        Index(value = ["type"]),
        Index(value = ["date"])
    ]
)
data class LendBorrowTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "person_id")
    val personId: Long,

    @ColumnInfo(name = "transaction_id")
    val transactionId: Long? = null,

    @ColumnInfo(name = "type")
    val type: LendBorrowType,

    @ColumnInfo(name = "amount")
    val amount: BigDecimal,

    @ColumnInfo(name = "currency", defaultValue = "'INR'")
    val currency: String = "CNY",

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "due_date")
    val dueDate: LocalDateTime? = null,

    @ColumnInfo(name = "is_settled", defaultValue = "0")
    val isSettled: Boolean = false,

    @ColumnInfo(name = "date")
    val date: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "is_sample", defaultValue = "0")
    val isSample: Boolean = false,

    @ColumnInfo(name = "account_id")
    val accountId: Long? = null,

    @ColumnInfo(name = "category_name")
    val category: String? = null,

    @ColumnInfo(name = "merchant_name")
    val merchant: String? = null,

    @ColumnInfo(name = "attachments", defaultValue = "'[]'")
    val attachments: List<String> = emptyList(),
    // Sync identity and last local change (epoch millis), kept by SyncTriggers; see docs/sync.md
    @ColumnInfo(name = "sync_id", defaultValue = "''") val syncId: String = "",
    @ColumnInfo(name = "sync_updated_at", defaultValue = "0") val syncUpdatedAt: Long = 0
)

enum class LendBorrowType {
    LENT,                // You gave money to the person (They owe you)
    BORROWED,            // You took money from the person (You owe them)
    SETTLEMENT_LENT,     // They returned money for a previous lending
    SETTLEMENT_BORROWED  // You paid money back for a previous debt
}
