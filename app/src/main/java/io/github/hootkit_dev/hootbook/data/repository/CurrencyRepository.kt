package io.github.hootkit_dev.hootbook.data.repository

import android.content.Context
import android.content.SharedPreferences
import io.github.hootkit_dev.hootbook.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurrencyRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private val sharedPrefs = context.getSharedPreferences("account_prefs", Context.MODE_PRIVATE)

    /**
     * Reactive Flow of the Main Account key.
     */
    private val mainAccountKeyFlow: Flow<String?> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "main_account") {
                trySend(prefs.getString(key, null))
            }
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(sharedPrefs.getString("main_account", null))
        awaitClose { sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /**
     * Returns the currency of the "Main Account" if set, otherwise falls back to UserPreferences.
     * This flow is reactive to both preference changes (selecting a new main account)
     * and database changes (updating the currency of the currently selected main account).
     */
    val baseCurrencyCode: Flow<String> = mainAccountKeyFlow.flatMapLatest { mainAccountKey ->
        if (mainAccountKey == null) {
            userPreferencesRepository.baseCurrency
        } else {
            val parts = mainAccountKey.split("_")
            if (parts.size >= 2) {
                val bankName = parts.subList(0, parts.size - 1).joinToString("_")
                val last4 = parts.last()
                
                // Observe the specific account in the database
                accountBalanceRepository.getLatestBalanceFlow(bankName, last4).flatMapLatest { account ->
                    if (account != null) {
                        kotlinx.coroutines.flow.flowOf(account.currency)
                    } else {
                        userPreferencesRepository.baseCurrency
                    }
                }
            } else {
                userPreferencesRepository.baseCurrency
            }
        }
    }

    /**
     * Returns the effective base currency, overriding with:
     * 1. Unified currency when enabled (highest priority — overrides all other currency settings for display)
     * 2. Default currency when enabled (used as the relational base for exchange rates)
     * 3. Main account's currency as fallback
     */
    val effectiveBaseCurrencyCode: Flow<String> = combine(
        baseCurrencyCode,
        userPreferencesRepository.unifiedCurrencyEnabled,
        userPreferencesRepository.unifiedCurrencyCode,
        userPreferencesRepository.defaultCurrencyEnabled,
        userPreferencesRepository.defaultCurrencyCode
    ) { baseCode, unifiedEnabled, unifiedCode, defaultEnabled, defaultCode ->
        when {
            unifiedEnabled && !unifiedCode.isNullOrBlank() -> unifiedCode
            defaultEnabled && !defaultCode.isNullOrBlank() -> defaultCode
            else -> baseCode
        }
    }
}
