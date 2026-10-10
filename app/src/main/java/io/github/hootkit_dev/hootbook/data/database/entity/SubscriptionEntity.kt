package io.github.hootkit_dev.hootbook.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(tableName = "subscriptions", indices = [Index(value = ["sync_id"])])
data class SubscriptionEntity(
        @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
        @ColumnInfo(name = "merchant_name") val merchantName: String,
        @ColumnInfo(name = "amount") val amount: BigDecimal,
        @ColumnInfo(name = "next_payment_date") val nextPaymentDate: LocalDate?,
        @ColumnInfo(name = "state") val state: SubscriptionState = SubscriptionState.ACTIVE,
        @ColumnInfo(name = "bank_name") val bankName: String? = null,
        @ColumnInfo(name = "category") val category: String? = null,
        @ColumnInfo(name = "subcategory") val subcategory: String? = null,
        // The user's notes. Stored as sms_body before version 70; older backups still call it smsBody
        @SerializedName(value = "notes", alternate = ["smsBody"])
        @ColumnInfo(name = "notes") val notes: String? = null,
        @ColumnInfo(name = "created_at") val createdAt: LocalDateTime = LocalDateTime.now(),
        @ColumnInfo(name = "updated_at") val updatedAt: LocalDateTime = LocalDateTime.now(),
        @ColumnInfo(name = "currency", defaultValue = "INR") val currency: String = "CNY",
        @ColumnInfo(name = "billing_cycle") val billingCycle: String? = null,
        @ColumnInfo(name = "last_paid_date") val lastPaidDate: LocalDate? = null,
        @ColumnInfo(name = "is_sample", defaultValue = "0") val isSample: Boolean = false,
        // Sync identity and last local change (epoch millis), kept by SyncTriggers; see docs/sync.md
        @ColumnInfo(name = "sync_id", defaultValue = "''") val syncId: String = "",
        @ColumnInfo(name = "sync_updated_at", defaultValue = "0") val syncUpdatedAt: Long = 0
)

enum class SubscriptionState {
    ACTIVE,
    HIDDEN // Soft delete - hidden from view but kept for reactivation detection
}
