package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.currency

data class CurrencySettingsUiState(
    val mainCurrency: String = "",
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val unifiedCurrencyEnabled: Boolean = false,
    val unifiedCurrencyCode: String? = null,
    val defaultCurrencyEnabled: Boolean = false,
    val defaultCurrencyCode: String? = null
)
