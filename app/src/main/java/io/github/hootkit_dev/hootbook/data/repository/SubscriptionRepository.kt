package io.github.hootkit_dev.hootbook.data.repository

import io.github.hootkit_dev.hootbook.data.database.dao.SubscriptionDao
import io.github.hootkit_dev.hootbook.data.database.entity.SubscriptionEntity
import io.github.hootkit_dev.hootbook.data.database.entity.SubscriptionState
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log

@Singleton
class SubscriptionRepository @Inject constructor(
    private val subscriptionDao: SubscriptionDao
) {
    
    companion object {
        private const val TAG = "SubscriptionRepository"
    }
    
    fun getAllSubscriptions(): Flow<List<SubscriptionEntity>> = 
        subscriptionDao.getAllSubscriptions()
    
    fun getActiveSubscriptions(): Flow<List<SubscriptionEntity>> = 
        subscriptionDao.getActiveSubscriptions()
    
    fun getUpcomingSubscriptions(daysAhead: Int = 7): Flow<List<SubscriptionEntity>> {
        val futureDate = LocalDate.now().plusDays(daysAhead.toLong())
        return subscriptionDao.getUpcomingSubscriptions(futureDate)
    }
    
    suspend fun getSubscriptionById(id: Long): SubscriptionEntity? = 
        subscriptionDao.getSubscriptionById(id)
    
    suspend fun insertSubscription(subscription: SubscriptionEntity): Long = 
        subscriptionDao.insertSubscription(subscription)
    
    suspend fun updateSubscription(subscription: SubscriptionEntity) = 
        subscriptionDao.updateSubscription(subscription)
    
    suspend fun updateSubscriptionState(id: Long, state: SubscriptionState) = 
        subscriptionDao.updateSubscriptionState(id, state)
    
    suspend fun hideSubscription(id: Long) {
        Log.d(TAG, "Hiding subscription with ID: $id")
        updateSubscriptionState(id, SubscriptionState.HIDDEN)
    }
    
    suspend fun unhideSubscription(id: Long) = 
        updateSubscriptionState(id, SubscriptionState.ACTIVE)
    
    suspend fun deleteSubscription(id: Long) = 
        subscriptionDao.deleteSubscriptionById(id)

    suspend fun deleteAllSubscriptions() =
        subscriptionDao.deleteAllSubscriptions()
    
    /**
     * Checks if a transaction matches any active subscription
     */
    suspend fun matchTransactionToSubscription(
        merchantName: String,
        amount: BigDecimal
    ): SubscriptionEntity? {
        val activeSubscription = subscriptionDao.getActiveSubscriptionByMerchant(merchantName)
        
        // Check if amounts match (with some tolerance for small variations)
        return if (activeSubscription != null && 
                   areAmountsEqual(activeSubscription.amount, amount)) {
            activeSubscription
        } else {
            null
        }
    }

    private fun areAmountsEqual(amount1: BigDecimal, amount2: BigDecimal): Boolean {
        // Allow for small variations (up to 5%)
        val tolerance = amount1.multiply(BigDecimal("0.05"))
        val diff = amount1.subtract(amount2).abs()
        return diff <= tolerance
    }
    
    suspend fun updatePaymentStatus(id: Long, nextPaymentDate: LocalDate, lastPaidDate: LocalDate?) =
        subscriptionDao.updatePaymentStatus(id, nextPaymentDate, lastPaidDate)
}
