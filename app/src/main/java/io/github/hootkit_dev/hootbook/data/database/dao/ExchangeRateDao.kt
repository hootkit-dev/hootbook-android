package io.github.hootkit_dev.hootbook.data.database.dao

import androidx.room.*
import io.github.hootkit_dev.hootbook.data.database.entity.ExchangeRateEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface ExchangeRateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExchangeRate(exchangeRate: ExchangeRateEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExchangeRates(exchangeRates: List<ExchangeRateEntity>)

    @Query("SELECT * FROM exchange_rates WHERE from_currency = :fromCurrency AND to_currency = :toCurrency AND expires_at > :currentTime")
    suspend fun getExchangeRate(fromCurrency: String, toCurrency: String, currentTime: LocalDateTime = LocalDateTime.now()): ExchangeRateEntity?

    @Query("SELECT * FROM exchange_rates WHERE from_currency = :fromCurrency AND expires_at > :currentTime")
    suspend fun getExchangeRatesForCurrency(fromCurrency: String, currentTime: LocalDateTime = LocalDateTime.now()): List<ExchangeRateEntity>

    @Query("SELECT * FROM exchange_rates WHERE from_currency = :fromCurrency ORDER BY updated_at DESC")
    suspend fun getAllRatesForCurrency(fromCurrency: String): List<ExchangeRateEntity>

    @Query("DELETE FROM exchange_rates WHERE updated_at < :expiryTime")
    suspend fun deleteExpiredRates(expiryTime: LocalDateTime): Int

    @Query("SELECT COUNT(*) FROM exchange_rates WHERE from_currency = :fromCurrency AND to_currency = :toCurrency AND expires_at > :currentTime")
    suspend fun hasValidRate(fromCurrency: String, toCurrency: String, currentTime: LocalDateTime = LocalDateTime.now()): Int

    @Query("SELECT * FROM exchange_rates ORDER BY updated_at DESC LIMIT 1")
    suspend fun getLatestRate(): ExchangeRateEntity?

    /** The newest stored rate for the pair, expired or not: better than none while offline. */
    @Query("SELECT * FROM exchange_rates WHERE from_currency = :fromCurrency AND to_currency = :toCurrency ORDER BY updated_at DESC LIMIT 1")
    suspend fun getNewestRate(fromCurrency: String, toCurrency: String): ExchangeRateEntity?

    // Get all unique currencies that have exchange rates
    @Query("SELECT DISTINCT from_currency FROM exchange_rates WHERE expires_at > :currentTime")
    suspend fun getAvailableCurrencies(currentTime: LocalDateTime = LocalDateTime.now()): List<String>

    @Query("SELECT MAX(expires_at_unix) FROM exchange_rates WHERE from_currency = :fromCurrency")
    suspend fun getMaxExpiryTimeUnix(fromCurrency: String): Long?

    @Query("SELECT * FROM exchange_rates WHERE from_currency = :fromCurrency AND to_currency = :toCurrency AND is_custom = 1")
    suspend fun getCustomRate(fromCurrency: String, toCurrency: String): ExchangeRateEntity?

    @Query("SELECT * FROM exchange_rates WHERE from_currency = :fromCurrency AND is_custom = 1")
    suspend fun getCustomRatesForCurrency(fromCurrency: String): List<ExchangeRateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomRate(entity: ExchangeRateEntity)

    @Query("DELETE FROM exchange_rates WHERE from_currency = :fromCurrency AND to_currency = :toCurrency AND is_custom = 1")
    suspend fun resetCustomRate(fromCurrency: String, toCurrency: String)

    @Query("SELECT * FROM exchange_rates")
    fun getAllRates(): Flow<List<ExchangeRateEntity>>

    @Query("DELETE FROM exchange_rates")
    suspend fun deleteAllRates()
}