package io.github.hootkit_dev.hootbook.utils

import io.github.hootkit_dev.hootbook.presentation.core.common.CoreMoneyPresentation
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/** Pure display compatibility for retained visual components; no currencies/rates/account-name rules. */
object CurrencyFormatter {
    fun formatCurrency(amount: BigDecimal, currencyCode: String = "CNY"): String = CoreMoneyPresentation.format(amount, currencyCode)
    fun formatCurrency(amount: Double, currencyCode: String = "CNY"): String = formatCurrency(BigDecimal.valueOf(amount), currencyCode)
    fun formatAmount(amount: BigDecimal, currencyCode: String = "CNY"): String = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        maximumFractionDigits = amount.scale().coerceAtLeast(0); minimumFractionDigits = amount.scale().coerceAtLeast(0)
    }.format(amount)
    fun formatAmount(amount: Double, currencyCode: String = "CNY"): String = formatAmount(BigDecimal.valueOf(amount), currencyCode)
    fun getCurrencySymbol(currencyCode: String): String = runCatching { java.util.Currency.getInstance(currencyCode).getSymbol(Locale.getDefault()) }.getOrDefault(currencyCode)
}
