package io.github.hootkit_dev.hootbook.data.currency

import io.github.hootkit_dev.hootbook.data.database.dao.ExchangeRateDao
import io.github.hootkit_dev.hootbook.data.database.entity.ExchangeRateEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurrencyConversionService @Inject constructor(
    private val exchangeRateDao: ExchangeRateDao,
    private val exchangeRateProvider: ExchangeRateProvider,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private val backgroundScope = CoroutineScope(Dispatchers.IO)

    // Cache rates for performance
    // Read and written from several ViewModels' pipelines on Default and Main.
    private val rateCache = java.util.concurrent.ConcurrentHashMap<String, BigDecimal>()
    private var lastCacheUpdate: LocalDateTime = LocalDateTime.MIN

    // Emits a new value whenever a custom rate is saved or reset, or a rate a list was waiting
    // for arrives from the network, so ViewModels can react
    private val _rateChangeTrigger = MutableStateFlow(0L)
    val rateChangeTrigger: StateFlow<Long> = _rateChangeTrigger.asStateFlow()

    // Pairs ("USD_CNY") being fetched for availableRates, and when a pair was last fetched:
    // it is not asked for again for RETRY_AFTER_MS, whatever the outcome, so lists do not
    // hit the network on every emission while offline, nor loop on a rate that arrives
    // already expired.
    private val fetchesInFlight = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val attemptedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** Rates for a list: what is known now, and which currencies are still being fetched. */
    data class RateLookup(val rates: Map<String, BigDecimal>, val pending: Set<String>)

    /**
     * [transactions] in [toCurrency], for a list: amounts whose rate is known now, and the
     * currencies still being fetched. Never waits on the network; when a missing rate
     * arrives, [rateChangeTrigger] fires and the caller converts again.
     */
    suspend fun convert(transactions: List<TransactionEntity>, toCurrency: String): Conversions {
        val foreign = transactions.filter { !it.currency.equals(toCurrency, ignoreCase = true) }
        if (foreign.isEmpty()) return Conversions()
        val lookup = availableRates(foreign.map { it.currency }.toSet(), toCurrency)
        return Conversions(
            amounts = foreign.mapNotNull { tx ->
                lookup.rates[tx.currency.uppercase()]?.let { tx.id to tx.amount.multiply(it).setScale(2, RoundingMode.HALF_UP) }
            }.toMap(),
            pendingCurrencies = lookup.pending
        )
    }

    /**
     * Rates from each of [currencies] to [toCurrency] that need no network (memory, custom
     * rates, stored rates). Currencies without one are fetched in the background and listed as
     * [RateLookup.pending] meanwhile; when a fetch succeeds, [rateChangeTrigger] fires so the
     * list converts again. A currency fetched recently without a rate is neither: it has none.
     */
    suspend fun availableRates(currencies: Collection<String>, toCurrency: String): RateLookup {
        val rates = mutableMapOf<String, BigDecimal>()
        val pending = mutableSetOf<String>()
        currencies.map { it.uppercase() }.distinct().forEach { from ->
            if (from.equals(toCurrency, ignoreCase = true)) {
                rates[from] = BigDecimal.ONE
                return@forEach
            }
            val local = getExchangeRate(from, toCurrency, allowNetwork = false)
            when {
                local != null -> rates[from] = local
                requestRate(from, toCurrency) -> pending += from
            }
        }
        return RateLookup(rates, pending)
    }

    /** Starts a background fetch for the pair unless one is running or failed recently. */
    private fun requestRate(fromCurrency: String, toCurrency: String): Boolean {
        val key = "${fromCurrency.uppercase()}_${toCurrency.uppercase()}"
        if (recentlyAttempted(key)) return false
        if (!fetchesInFlight.add(key)) return true
        backgroundScope.launch {
            try {
                fetchWithBackoff(fromCurrency, toCurrency)
            } finally {
                fetchesInFlight.remove(key)
                // Success or not, lists waiting on this pair convert again: with the new rate,
                // or showing that none is available instead of loading for good.
                _rateChangeTrigger.value++
            }
        }
        return true
    }

    private fun recentlyAttempted(key: String): Boolean =
        attemptedAt[key]?.let { System.currentTimeMillis() - it < RETRY_AFTER_MS } == true

    /** [fetchAndCacheRate], at most once per pair every [RETRY_AFTER_MS]. */
    private suspend fun fetchWithBackoff(fromCurrency: String, toCurrency: String): BigDecimal? {
        val key = "${fromCurrency.uppercase()}_${toCurrency.uppercase()}"
        if (recentlyAttempted(key)) return null
        attemptedAt[key] = System.currentTimeMillis()
        return fetchAndCacheRate(fromCurrency, toCurrency)
    }

    /**
     * Convert amount from one currency to another
     */
    suspend fun convertAmount(
        amount: BigDecimal,
        fromCurrency: String,
        toCurrency: String,
        forceRefresh: Boolean = false
    ): BigDecimal {
        if (fromCurrency.equals(toCurrency, ignoreCase = true)) {
            return amount
        }

        val rate = getExchangeRate(fromCurrency, toCurrency, forceRefresh)
        return if (rate != null) {
            amount.multiply(rate).setScale(2, RoundingMode.HALF_UP)
        } else {
            amount // Return original amount if conversion fails
        }
    }

    /**
     * Get exchange rate between two currencies
     */
    suspend fun getExchangeRate(
        fromCurrency: String,
        toCurrency: String,
        forceRefresh: Boolean = false,
        allowNetwork: Boolean = true
    ): BigDecimal? {
        val cacheKey = "${fromCurrency.uppercase()}_${toCurrency.uppercase()}"

        // Check cache first (unless forced refresh)
        if (!forceRefresh && isCacheValid()) {
            rateCache[cacheKey]?.let { return it }
        }

        // Check database for custom rates first (always takes priority)
        if (!forceRefresh) {
            val customRate = exchangeRateDao.getCustomRate(fromCurrency.uppercase(), toCurrency.uppercase())
            if (customRate != null) {
                updateCache(cacheKey, customRate.rate)
                return customRate.rate
            }

            val reverseCustomRate = exchangeRateDao.getCustomRate(toCurrency.uppercase(), fromCurrency.uppercase())
            if (reverseCustomRate != null) {
                try {
                    val invertedRate = BigDecimal.ONE.divide(reverseCustomRate.rate, MathContext(10))
                    updateCache(cacheKey, invertedRate)
                    return invertedRate
                } catch (_: ArithmeticException) {
                }
            }
        }

        // Check database for fresh rates
        val currentTime = LocalDateTime.now()
        val dbRate = exchangeRateDao.getExchangeRate(fromCurrency, toCurrency, currentTime)

        if (dbRate != null && !forceRefresh) {
            // Rate is still valid (expires_at > currentTime), use it
            updateCache(cacheKey, dbRate.rate)
            return dbRate.rate
        }

        // Check if we have any expired rate that we might be able to use if rates aren't stale overall
        if (!forceRefresh) {
            val expiredRate = exchangeRateDao.getExchangeRate(
                fromCurrency,
                toCurrency,
                currentTime.minusHours(24) // Look back up to 24 hours for expired rates
            )

            if (expiredRate != null && !areOverallRatesStale()) {
                // Use expired rate if overall rates aren't stale, but fetch fresh ones soon
                updateCache(cacheKey, expiredRate.rate)
                // Trigger background refresh for next time
                backgroundScope.launch {
                    refreshExchangeRates(listOf(fromCurrency, toCurrency, "USD"))
                }
                return expiredRate.rate
            }
        }

        // Check reverse pair (e.g., custom rate stored as INR→USD when looking for USD→INR)
        val reverseRate = exchangeRateDao.getExchangeRate(toCurrency, fromCurrency, currentTime)
        if (reverseRate != null && !forceRefresh) {
            try {
                val invertedRate = BigDecimal.ONE.divide(reverseRate.rate, MathContext(10))
                updateCache(cacheKey, invertedRate)
                return invertedRate
            } catch (_: ArithmeticException) {
                // Division by zero or non-terminating decimal — fall through to API
            }
        }

        // An expired rate in either direction beats none: use it now and refresh it in the
        // background (rateChangeTrigger announces the fresh one).
        if (!forceRefresh) {
            val stale = exchangeRateDao.getNewestRate(fromCurrency.uppercase(), toCurrency.uppercase())?.rate
                ?: exchangeRateDao.getNewestRate(toCurrency.uppercase(), fromCurrency.uppercase())?.rate
                    ?.takeIf { it.signum() > 0 }
                    ?.let { BigDecimal.ONE.divide(it, MathContext(10)) }
            if (stale != null) {
                requestRate(fromCurrency, toCurrency)
                return stale
            }
        }

        // Fetch from API if not found, forced refresh, or rates are stale
        if (!allowNetwork) return null
        return if (forceRefresh) fetchAndCacheRate(fromCurrency, toCurrency)
        else fetchWithBackoff(fromCurrency, toCurrency)
    }

    /**
     * Check if we have a valid rate for this currency pair
     */
    suspend fun hasValidRate(fromCurrency: String, toCurrency: String): Boolean {
        if (fromCurrency.equals(toCurrency, ignoreCase = true)) {
            return true
        }

        val cacheKey = "${fromCurrency.uppercase()}_${toCurrency.uppercase()}"
        if (isCacheValid() && rateCache.containsKey(cacheKey)) {
            return true
        }

        return exchangeRateDao.hasValidRate(fromCurrency, toCurrency) > 0
    }

    /**
     * Refresh exchange rates for an account's currencies
     */
    suspend fun refreshExchangeRatesForAccount(currencies: List<String>) {
        if (currencies.size < 2) return // No conversion needed for single currency

        // Get unique currencies and ensure USD is included for API compatibility
        val uniqueCurrencies = currencies.distinct().toMutableList()
        if (!uniqueCurrencies.contains("USD")) {
            uniqueCurrencies.add("USD")
        }

        refreshExchangeRates(uniqueCurrencies)
    }

    /**
     * Refresh exchange rates for specific currencies using USD as base
     */
    suspend fun refreshExchangeRates(currencies: List<String>) {
        // Use USD as the base currency for the API since it's most commonly supported
        val apiBaseCurrency = "USD"

        // Check if we need to refresh by looking at the newest rate in our database
        if (!shouldRefreshRates(apiBaseCurrency)) {
            println("Currency rates are fresh, skipping refresh")
            return // Rates are still fresh, no need to refresh
        }
        println("Currency rates are stale, refreshing from API")
        fetchAndSaveAllRates(apiBaseCurrency, currencies)
    }

    /**
     * Fetch all relevant rates from the API and save to the database.
     */
    /**
     * A sync the user asked for: fetches every rate for [baseCurrency] now, whatever the cached
     * state, and has lists convert again. Returns whether a server answered; the reasons are in
     * [RateSyncState.lastSync].
     */
    suspend fun syncNow(baseCurrency: String): Boolean {
        val synced = fetchAndSaveAllRates(baseCurrency)
        if (synced) {
            rateCache.clear()
            attemptedAt.clear()
        }
        _rateChangeTrigger.value++
        return synced
    }

    suspend fun fetchAndSaveAllRates(baseCurrency: String, targetCurrencies: List<String> = emptyList()): Boolean {
        val response = exchangeRateProvider.fetchAllExchangeRatesWithMetadata(baseCurrency)

        if (response != null) {
            val allRates = response.rates
            val nextUpdateTime = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(response.nextUpdateTimeUnix),
                ZoneId.systemDefault()
            )
            val lastUpdateTime = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(response.lastUpdateTimeUnix),
                ZoneId.systemDefault()
            )

            val entities = mutableListOf<ExchangeRateEntity>()

            // If targetCurrencies is empty, we'll cache all received rates (usually ~150-200)
            val sourceRates = if (targetCurrencies.isEmpty()) allRates.keys else targetCurrencies

            sourceRates.forEach { toCurrency ->
                val rate = allRates[toCurrency.uppercase()] ?: allRates[toCurrency.lowercase()]
                if (rate != null) {
                    entities.add(
                        ExchangeRateEntity(
                            fromCurrency = baseCurrency.uppercase(),
                            toCurrency = toCurrency.uppercase(),
                            rate = rate,
                            provider = response.provider,
                            updatedAt = lastUpdateTime,
                            updatedAtUnix = response.lastUpdateTimeUnix,
                            expiresAt = nextUpdateTime,
                            expiresAtUnix = response.nextUpdateTimeUnix
                        )
                    )
                }
            }

            if (entities.isNotEmpty()) {
                val customRates = exchangeRateDao.getCustomRatesForCurrency(baseCurrency.uppercase())
                val customPairs = customRates.map { it.fromCurrency.uppercase() to it.toCurrency.uppercase() }.toSet()

                val filteredEntities = entities.filterNot { entity ->
                    (entity.fromCurrency.uppercase() to entity.toCurrency.uppercase()) in customPairs
                }

                if (filteredEntities.isNotEmpty()) {
                    exchangeRateDao.insertExchangeRates(filteredEntities)
                }
            }
        }
        return response != null
    }

    suspend fun saveCustomRate(fromCurrency: String, toCurrency: String, rate: BigDecimal) {
        val now = LocalDateTime.now()
        val entity = ExchangeRateEntity(
            fromCurrency = fromCurrency.uppercase(),
            toCurrency = toCurrency.uppercase(),
            rate = rate,
            provider = "custom",
            updatedAt = now,
            updatedAtUnix = now.atZone(ZoneId.systemDefault()).toEpochSecond(),
            expiresAt = now.plusYears(100),
            expiresAtUnix = now.plusYears(100).atZone(ZoneId.systemDefault()).toEpochSecond(),
            isCustom = true
        )
        exchangeRateDao.upsertCustomRate(entity)
        rateCache.clear()
        _rateChangeTrigger.value++
    }

    suspend fun resetCustomRate(fromCurrency: String, toCurrency: String) {
        exchangeRateDao.resetCustomRate(fromCurrency.uppercase(), toCurrency.uppercase())
        rateCache.clear()
        _rateChangeTrigger.value++
    }

    /**
     * Retrieve stored conversions for a base currency from the local database.
     */
    suspend fun getStoredConversions(baseCurrency: String): Pair<List<ExchangeRateEntity>, Long> {
        val rates = exchangeRateDao.getAllRatesForCurrency(baseCurrency.uppercase())
        val lastUpdated = rates.maxByOrNull { it.updatedAtUnix }?.updatedAtUnix ?: 0L
        return Pair(rates, lastUpdated)
    }

    /**
     * Fetch exchange rate from API and cache it
     */
    private suspend fun fetchAndCacheRate(fromCurrency: String, toCurrency: String): BigDecimal? {
        try {
            // Use the metadata method to get proper expiry times even for individual rates
            // We'll use USD as base since that's what the API uses and then convert
            val baseCurrency = "USD"
            val response = exchangeRateProvider.fetchAllExchangeRatesWithMetadata(baseCurrency)

            if (response != null) {
                val allRates = response.rates
                val nextUpdateTime = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(response.nextUpdateTimeUnix),
                    ZoneId.systemDefault()
                )
                val lastUpdateTime = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(response.lastUpdateTimeUnix),
                    ZoneId.systemDefault()
                )

                // Calculate the rate we need
                val rate = if (fromCurrency == baseCurrency) {
                    allRates[toCurrency]
                } else if (toCurrency == baseCurrency) {
                    allRates[fromCurrency]?.let { fromRate ->
                        BigDecimal.ONE.divide(fromRate, MathContext(10))
                    }
                } else {
                    // Cross-currency: fromCurrency -> USD -> toCurrency
                    val fromToUsd = allRates[fromCurrency]
                    val usdToTo = allRates[toCurrency]
                    if (fromToUsd != null && usdToTo != null) {
                        usdToTo.divide(fromToUsd, MathContext(10))
                    } else {
                        null
                    }
                }

                if (rate != null) {
                    val entity = ExchangeRateEntity(
                        fromCurrency = fromCurrency,
                        toCurrency = toCurrency,
                        rate = rate,
                        provider = response.provider,
                        updatedAt = lastUpdateTime,
                        updatedAtUnix = response.lastUpdateTimeUnix,
                        expiresAt = nextUpdateTime, // Use the API's actual next update time
                        expiresAtUnix = response.nextUpdateTimeUnix
                    )

                    exchangeRateDao.insertExchangeRate(entity)
                    val cacheKey = "${fromCurrency.uppercase()}_${toCurrency.uppercase()}"
                    updateCache(cacheKey, rate)
                    return rate
                }
            }
        } catch (e: Exception) {
            // Log error but don't crash
            println("Failed to fetch exchange rate for $fromCurrency to $toCurrency: ${e.message}")
        }

        return null
    }

    /**
     * Check if we should refresh rates for the given base currency
     * Returns true if rates are stale or we don't have any rates
     */
    private suspend fun shouldRefreshRates(baseCurrency: String): Boolean {
        val currentTimeUnix = System.currentTimeMillis() / 1000

        // Use the efficient Unix timestamp query to get the latest expiry time
        val maxExpiryTimeUnix = exchangeRateDao.getMaxExpiryTimeUnix(baseCurrency)

        // If we don't have any rates, or they're from old records (timestamp 0), or they've expired, refresh
        return maxExpiryTimeUnix == null || maxExpiryTimeUnix == 0L || maxExpiryTimeUnix < currentTimeUnix
    }

    /**
     * Check if overall rates are stale across all currencies
     */
    private suspend fun areOverallRatesStale(): Boolean {
        return shouldRefreshRates("USD") // USD is our main base currency, so check its rates
    }

    /**
     * Update cache with new rate
     */
    private fun updateCache(key: String, rate: BigDecimal) {
        rateCache[key] = rate
        lastCacheUpdate = LocalDateTime.now()
    }

    /**
     * Check if cache is still valid (less than 1 hour old)
     */
    private companion object {
        const val RETRY_AFTER_MS = 5 * 60 * 1000L
    }

    private fun isCacheValid(): Boolean {
        return lastCacheUpdate.isAfter(LocalDateTime.now().minusHours(1))
    }

    /**
     * Get all available currencies with exchange rates
     */
    suspend fun getAvailableCurrencies(): List<String> {
        return exchangeRateDao.getAvailableCurrencies()
    }

    data class TransactionData(
        val id: String,
        val amount: BigDecimal,
        val currency: String
    )

    data class RateFreshnessInfo(
        val hasValidUsdRates: Boolean,
        val validUsdRatesCount: Int,
        val latestUpdateTime: LocalDateTime?,
        val latestExpiryTime: LocalDateTime?,
        val isStale: Boolean,
        val currentTime: LocalDateTime
    )
}