package io.github.hootkit_dev.hootbook.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Entity representing a monthly budget.
 * Users can create multiple budgets, each tied to a specific month and year.
 */
@Entity(tableName = "budgets", indices = [Index(value = ["sync_id"])])
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "amount")
    val amount: BigDecimal,

    @ColumnInfo(name = "year")
    val year: Int,

    @ColumnInfo(name = "month")
    val month: Int,

    @ColumnInfo(name = "currency", defaultValue = "INR")
    val currency: String = "CNY",

    @ColumnInfo(name = "is_active", defaultValue = "1")
    val isActive: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    // --- New Fields ---
    @ColumnInfo(name = "start_date", defaultValue = "")
    val startDate: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "end_date", defaultValue = "")
    val endDate: LocalDateTime = LocalDateTime.now().plusMonths(1),

    @ColumnInfo(name = "period_type", defaultValue = "MONTHLY")
    val periodType: BudgetPeriod = BudgetPeriod.MONTHLY,

    @ColumnInfo(name = "track_type", defaultValue = "ALL_TRANSACTIONS")
    val trackType: BudgetTrackType = BudgetTrackType.ALL_TRANSACTIONS,

    @ColumnInfo(name = "budget_type", defaultValue = "EXPENSE")
    val budgetType: BudgetType = BudgetType.EXPENSE,

    @ColumnInfo(name = "account_ids", defaultValue = "")
    val accountIds: List<String> = emptyList(), // List of "BankName:Last4"

    @ColumnInfo(name = "color", defaultValue = "#4CAF50") // Default Green
    val color: String = "#4CAF50",

    @ColumnInfo(name = "is_sample", defaultValue = "0")
    val isSample: Boolean = false,
    // Sync identity and last local change (epoch millis), kept by SyncTriggers; see docs/sync.md
    @ColumnInfo(name = "sync_id", defaultValue = "''") val syncId: String = "",
    @ColumnInfo(name = "sync_updated_at", defaultValue = "0") val syncUpdatedAt: Long = 0
)

enum class BudgetPeriod {
    CUSTOM,
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY
}

/** Budgets count every transaction; the "manually added only" mode was removed (rows read as this). */
enum class BudgetTrackType {
    ALL_TRANSACTIONS
}

enum class BudgetType {
    EXPENSE,
    SAVINGS
}
