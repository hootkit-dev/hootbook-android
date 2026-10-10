package io.github.hootkit_dev.hootbook.data.currency.model

data class CurrencyConversion(
    val currencyCode: String,
    val rate: Double,
    val lastUpdated: Long = 0L,
    val isCustom: Boolean = false,
    val customSymbol: String? = null
) {
    val symbol: String get() = customSymbol ?: CurrencySymbols.getSymbol(currencyCode)
    val displayRate: String get() = String.format("%.6f", rate)
}
