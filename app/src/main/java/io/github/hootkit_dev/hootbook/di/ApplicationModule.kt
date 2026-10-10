package io.github.hootkit_dev.hootbook.di

import io.github.hootkit_dev.hootbook.data.currency.CurrencyConversionService
import io.github.hootkit_dev.hootbook.data.currency.ExchangeRateProvider
import io.github.hootkit_dev.hootbook.data.currency.ExchangeRateProviderFactory
import io.github.hootkit_dev.hootbook.data.currency.RateSyncState
import io.github.hootkit_dev.hootbook.data.database.dao.ExchangeRateDao
import io.github.hootkit_dev.hootbook.data.preferences.UserPreferencesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class ApplicationScope

/**
 * Hilt module that provides application-level dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object ApplicationModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Provides the ExchangeRateProvider implementation.
     *
     * @return ExchangeRateProvider for fetching exchange rates
     */
    @Provides
    @Singleton
    fun provideExchangeRateProvider(syncState: RateSyncState): ExchangeRateProvider {
        return ExchangeRateProviderFactory.createProvider(syncState)
    }

    /**
     * Provides the CurrencyConversionService.
     *
     * @param exchangeRateDao Database access for exchange rates
     * @param exchangeRateProvider Provider for fetching rates from API
     * @param userPreferencesRepository User preferences for base currency
     * @return CurrencyConversionService for currency conversion operations
     */
    @Provides
    @Singleton
    fun provideCurrencyConversionService(
        exchangeRateDao: ExchangeRateDao,
        exchangeRateProvider: ExchangeRateProvider,
        userPreferencesRepository: UserPreferencesRepository
    ): CurrencyConversionService {
        return CurrencyConversionService(
            exchangeRateDao = exchangeRateDao,
            exchangeRateProvider = exchangeRateProvider,
            userPreferencesRepository = userPreferencesRepository
        )
    }
}