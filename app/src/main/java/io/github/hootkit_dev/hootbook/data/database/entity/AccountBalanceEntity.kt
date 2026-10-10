package io.github.hootkit_dev.hootbook.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * One balance of one currency of an account, at a moment. Rows are written alongside the
 * transactions that move them (balanceMoves). Sync must copy them verbatim, with
 * sync_control.applying_remote set, and never re-run a transaction's balance effect: see
 * docs/sync.md, "Balances caveat".
 */
@Entity(
    tableName = "account_balances",
    indices = [
        Index(value = ["sync_id"]),
        // Each currency of an account keeps its own history, so two can share a moment
        Index(value = ["bank_name", "account_last4", "currency", "timestamp"], unique = true),
        Index(value = ["bank_name", "account_last4"]),
        Index(value = ["account_id", "currency", "timestamp"]),
        Index(value = ["timestamp"]
        )
    ]
)
data class AccountBalanceEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "icon_res_id", defaultValue = "0") val iconResId: Int = 0,
    @ColumnInfo(name = "icon_name", defaultValue = "") val iconName: String = "",
    @ColumnInfo(name = "bank_name") val bankName: String,
    @ColumnInfo(name = "account_last4") val accountLast4: String,
    @ColumnInfo(name = "balance") val balance: BigDecimal,
    @ColumnInfo(name = "timestamp") val timestamp: LocalDateTime,
    @ColumnInfo(name = "transaction_id") val transactionId: Long? = null,
    @ColumnInfo(name = "credit_limit") val creditLimit: BigDecimal? = null,
    @ColumnInfo(name = "is_credit_card", defaultValue = "0") val isCreditCard: Boolean = false,
    @ColumnInfo(name = "source_type")
    val sourceType: String? = null, // TRANSACTION, SMS_BALANCE, MANUAL, CARD_LINK
    @ColumnInfo(name = "created_at") val createdAt: LocalDateTime = LocalDateTime.now(),
    @ColumnInfo(name = "currency", defaultValue = "INR") val currency: String = "CNY",
    @ColumnInfo(name = "is_wallet", defaultValue = "0") val isWallet: Boolean = false,
    @ColumnInfo(name = "color", defaultValue = "#33B5E5") val color: String = "#33B5E5",
    @ColumnInfo(name = "is_sample", defaultValue = "0") val isSample: Boolean = false,
    // The account this row belongs to (accounts.id); its currency is the row's currency
    @ColumnInfo(name = "account_id") val accountId: Long? = null,
    // Sync identity and last local change (epoch millis), kept by SyncTriggers; see docs/sync.md
    @ColumnInfo(name = "sync_id", defaultValue = "''") val syncId: String = "",
    @ColumnInfo(name = "sync_updated_at", defaultValue = "0") val syncUpdatedAt: Long = 0
)
