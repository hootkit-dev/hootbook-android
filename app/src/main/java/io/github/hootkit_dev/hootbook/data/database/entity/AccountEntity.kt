package io.github.hootkit_dev.hootbook.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * An account: a bank account, card, wallet or brokerage. It holds money in one or more
 * currencies ([AccountCurrencyEntity]); each currency keeps its own balance history in
 * `account_balances`. Name and last 4 are what the user sees, the id is what everything refers to.
 */
@Entity(
    tableName = "accounts",
    indices = [Index(value = ["sync_id"]), Index(value = ["name", "last4"], unique = true)]
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "last4") val last4: String,
    @ColumnInfo(name = "main_currency") val mainCurrency: String,
    @ColumnInfo(name = "is_credit_card", defaultValue = "0") val isCreditCard: Boolean = false,
    @ColumnInfo(name = "is_wallet", defaultValue = "0") val isWallet: Boolean = false,
    // A card's limit in its main currency, shared by all its currencies unless one sets its own
    @ColumnInfo(name = "credit_limit") val creditLimit: BigDecimal? = null,
    @ColumnInfo(name = "icon_res_id", defaultValue = "0") val iconResId: Int = 0,
    @ColumnInfo(name = "icon_name", defaultValue = "") val iconName: String = "",
    @ColumnInfo(name = "color", defaultValue = "#33B5E5") val color: String = "#33B5E5",
    @ColumnInfo(name = "is_sample", defaultValue = "0") val isSample: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: LocalDateTime = LocalDateTime.now(),
    // A credit card's statement closing day and payment due day, as days of the month (1–31;
    // a short month uses its last day)
    @ColumnInfo(name = "statement_day") val statementDay: Int? = null,
    @ColumnInfo(name = "due_day") val dueDay: Int? = null,
    // Sync identity and last local change (epoch millis), kept by SyncTriggers; see docs/sync.md
    @ColumnInfo(name = "sync_id", defaultValue = "''") val syncId: String = "",
    @ColumnInfo(name = "sync_updated_at", defaultValue = "0") val syncUpdatedAt: Long = 0
)

/** A currency an account holds. Added, never removed. */
@Entity(
    tableName = "account_currencies",
    indices = [Index(value = ["sync_id"])],
    primaryKeys = ["account_id", "currency"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class AccountCurrencyEntity(
    @ColumnInfo(name = "account_id") val accountId: Long,
    @ColumnInfo(name = "currency") val currency: String,
    // A card limit of this currency alone; null shares the account's limit
    @ColumnInfo(name = "credit_limit") val creditLimit: BigDecimal? = null,
    @ColumnInfo(name = "created_at") val createdAt: LocalDateTime = LocalDateTime.now(),
    // Sync identity and last local change (epoch millis), kept by SyncTriggers; see docs/sync.md
    @ColumnInfo(name = "sync_id", defaultValue = "''") val syncId: String = "",
    @ColumnInfo(name = "sync_updated_at", defaultValue = "0") val syncUpdatedAt: Long = 0
)
