package io.github.hootkit_dev.hootbook.presentation.ui.features.home

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Home-only formatting: one currency label, exact Core precision, no conversion or rounding. */
internal object HomeMoneyPresentation {
    fun format(
        amount: BigDecimal?,
        currency: String,
        symbols: Map<String, String> = emptyMap(),
        locale: Locale = Locale.getDefault(),
        useCode: Boolean = false,
    ): String {
        if (amount == null) return "— $currency"
        val label = if (useCode) currency else symbols[currency]?.ifBlank { currency }
            ?: runCatching { Currency.getInstance(currency).getSymbol(locale) }.getOrDefault(currency)
        val number = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = amount.scale().coerceAtLeast(0)
            maximumFractionDigits = minimumFractionDigits
            maximumIntegerDigits = 309
        }.format(amount)
        return "$label $number"
    }
}
