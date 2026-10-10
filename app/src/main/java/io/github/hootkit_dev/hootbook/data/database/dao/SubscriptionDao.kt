package io.github.hootkit_dev.hootbook.data.database.dao

import androidx.room.*
import io.github.hootkit_dev.hootbook.data.database.entity.SubscriptionEntity
import io.github.hootkit_dev.hootbook.data.database.entity.SubscriptionState
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate

@Dao
interface SubscriptionDao {
    
    @Query("SELECT * FROM subscriptions ORDER BY next_payment_date ASC")
    fun getAllSubscriptions(): Flow<List<SubscriptionEntity>>
    
    @Query("SELECT * FROM subscriptions WHERE state = :state ORDER BY next_payment_date ASC")
    fun getSubscriptionsByState(state: SubscriptionState): Flow<List<SubscriptionEntity>>
    
    @Query("SELECT * FROM subscriptions WHERE state = 'ACTIVE' ORDER BY next_payment_date ASC")
    fun getActiveSubscriptions(): Flow<List<SubscriptionEntity>>
    
    @Query("SELECT * FROM subscriptions WHERE next_payment_date <= :date AND state = 'ACTIVE' ORDER BY next_payment_date ASC")
    fun getUpcomingSubscriptions(date: LocalDate): Flow<List<SubscriptionEntity>>
    
    @Query("SELECT * FROM subscriptions WHERE merchant_name = :merchantName AND state = 'ACTIVE' LIMIT 1")
    suspend fun getActiveSubscriptionByMerchant(merchantName: String): SubscriptionEntity?
    
    @Query("SELECT * FROM subscriptions WHERE merchant_name = :merchantName AND state = 'HIDDEN' LIMIT 1")
    suspend fun getHiddenSubscriptionByMerchant(merchantName: String): SubscriptionEntity?
    
    
    @Query("SELECT * FROM subscriptions WHERE merchant_name = :merchantName AND amount = :amount AND next_payment_date = :paymentDate LIMIT 1")
    suspend fun getSubscriptionByMerchantAmountAndDate(
        merchantName: String,
        amount: BigDecimal,
        paymentDate: LocalDate
    ): SubscriptionEntity?
    
    @Query("SELECT * FROM subscriptions WHERE merchant_name = :merchantName AND amount = :amount LIMIT 1")
    suspend fun getSubscriptionByMerchantAndAmount(
        merchantName: String,
        amount: BigDecimal
    ): SubscriptionEntity?
    
    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun getSubscriptionById(id: Long): SubscriptionEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(subscription: SubscriptionEntity): Long
    
    @Update
    suspend fun updateSubscription(subscription: SubscriptionEntity)
    
    @Query("UPDATE subscriptions SET state = :state, updated_at = datetime('now') WHERE id = :id")
    suspend fun updateSubscriptionState(id: Long, state: SubscriptionState)
    
    @Query("UPDATE subscriptions SET next_payment_date = :nextPaymentDate, updated_at = datetime('now') WHERE id = :id")
    suspend fun updateNextPaymentDate(id: Long, nextPaymentDate: LocalDate)
    
    @Query("UPDATE subscriptions SET next_payment_date = :nextPaymentDate, last_paid_date = :lastPaidDate, updated_at = datetime('now') WHERE id = :id")
    suspend fun updatePaymentStatus(id: Long, nextPaymentDate: LocalDate, lastPaidDate: LocalDate?)
    
    @Delete
    suspend fun deleteSubscription(subscription: SubscriptionEntity)
    
    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteSubscriptionById(id: Long)
    
    @Query("SELECT * FROM subscriptions WHERE state = :state ORDER BY next_payment_date ASC")
    suspend fun getSubscriptionsByStateList(state: SubscriptionState): List<SubscriptionEntity>
    

    @Query("DELETE FROM subscriptions")
    suspend fun deleteAllSubscriptions()

    // Subscriptions keep only the institution name, not the account
    @Query("UPDATE subscriptions SET bank_name = :newBankName WHERE bank_name = :oldBankName")
    suspend fun renameBank(oldBankName: String, newBankName: String): Int
}