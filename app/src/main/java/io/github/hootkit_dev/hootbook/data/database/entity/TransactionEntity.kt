package io.github.hootkit_dev.hootbook.data.database.entity

import androidx.annotation.StringRes
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.hootkit_dev.hootbook.R
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity(
        tableName = "transactions",
        indices = [
                Index(value = ["sync_id"]),
                Index(value = ["transaction_hash"], unique = true),
                // Every list, Home widget, Analytics and budget query filters on these two.
                Index(value = ["is_deleted", "date_time"])
        ]
)
data class TransactionEntity(
        @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
        @ColumnInfo(name = "amount") val amount: BigDecimal,
        @ColumnInfo(name = "merchant_name") val merchantName: String,
        @ColumnInfo(name = "category") val category: String,
        @ColumnInfo(name = "subcategory") val subcategory: String? = null,
        @ColumnInfo(name = "transaction_type") val transactionType: TransactionType,
        @ColumnInfo(name = "date_time") val dateTime: LocalDateTime,
        @ColumnInfo(name = "description") val description: String? = null,
        @ColumnInfo(name = "bank_name") val bankName: String? = null,
        @ColumnInfo(name = "account_number") val accountNumber: String? = null,
        @ColumnInfo(name = "balance_after") val balanceAfter: BigDecimal? = null,
        @ColumnInfo(name = "transaction_hash", defaultValue = "") val transactionHash: String,
        @ColumnInfo(name = "is_recurring") val isRecurring: Boolean = false,
        @ColumnInfo(name = "is_deleted", defaultValue = "0") val isDeleted: Boolean = false,
        @ColumnInfo(name = "created_at") val createdAt: LocalDateTime = LocalDateTime.now(),
        @ColumnInfo(name = "updated_at") val updatedAt: LocalDateTime = LocalDateTime.now(),
        @ColumnInfo(name = "currency", defaultValue = "INR") val currency: String = "CNY",
        @ColumnInfo(name = "from_account") val fromAccount: String? = null,
        @ColumnInfo(name = "to_account") val toAccount: String? = null,
        // Transfers between accounts in different currencies: what reached the target account,
        // in its currency. Null when both sides moved the same amount.
        @ColumnInfo(name = "to_amount") val toAmount: BigDecimal? = null,
        // The accounts (accounts.id) a transaction moves money out of and, for a transfer, into.
        // A transfer's target side is in [toCurrency] (null: the transaction's own currency).
        @ColumnInfo(name = "account_id") val accountId: Long? = null,
        @ColumnInfo(name = "to_account_id") val toAccountId: Long? = null,
        @ColumnInfo(name = "to_currency") val toCurrency: String? = null,
        @ColumnInfo(name = "reference") val reference: String? = null,
        @ColumnInfo(name = "billing_cycle") val billingCycle: String? = null,
        @ColumnInfo(name = "attachments", defaultValue = "") val attachments: String = "",
        @ColumnInfo(name = "is_sample", defaultValue = "0") val isSample: Boolean = false,
        // Sync identity and last local change (epoch millis), kept by SyncTriggers; see docs/sync.md
        @ColumnInfo(name = "sync_id", defaultValue = "''") val syncId: String = "",
        @ColumnInfo(name = "sync_updated_at", defaultValue = "0") val syncUpdatedAt: Long = 0
)

enum class TransactionType(@StringRes val labelRes: Int) {
    INCOME(R.string.type_income),
    EXPENSE(R.string.type_expense),
    CREDIT(R.string.type_credit),
    TRANSFER(R.string.type_transfer),
    INVESTMENT(R.string.type_investment),
    BALANCE_UPDATE(R.string.type_balance_update),
    LENT(R.string.type_lent),
    BORROWED(R.string.type_borrowed)
}
