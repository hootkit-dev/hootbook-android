package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.currency

data class CurrencySettingsUiState(
    val unifiedCurrencyEnabled: Boolean = false,
    val unifiedCurrencyCode: String? = null,
    val defaultCurrencyEnabled: Boolean = false,
    val defaultCurrencyCode: String? = null
)
