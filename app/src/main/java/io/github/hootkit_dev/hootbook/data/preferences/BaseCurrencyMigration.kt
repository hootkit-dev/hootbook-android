package io.github.hootkit_dev.hootbook.data.preferences

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/** Pin the initial default once, so completing onboarding cannot change currency later. */
internal class BaseCurrencyMigration : DataMigration<Preferences> {
    private val currencyKey = stringPreferencesKey("base_currency")
    private val onboardingKey = booleanPreferencesKey("has_shown_scan_tutorial")

    override suspend fun shouldMigrate(currentData: Preferences): Boolean = currentData[currencyKey] == null

    override suspend fun migrate(currentData: Preferences): Preferences = currentData.toMutablePreferences().apply {
        this[currencyKey] = resolveInitialBaseCurrency(currentData[currencyKey], currentData[onboardingKey] == true)
    }

    override suspend fun cleanUp() = Unit
}

internal fun resolveInitialBaseCurrency(storedCurrency: String?, completedOnboarding: Boolean): String =
    storedCurrency ?: if (completedOnboarding) "INR" else "CNY"
