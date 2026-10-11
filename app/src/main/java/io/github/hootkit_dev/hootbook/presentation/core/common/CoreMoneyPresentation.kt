package io.github.hootkit_dev.hootbook.presentation.core.common

import dev.hootkit.core.CurrencyInterface
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.CancellationException

data class CoreDisplayedMoney(
    val originalAmount: BigDecimal,
    val originalCurrency: String,
    val amount: BigDecimal?,
    val currency: String,
    val complete: Boolean,
    val failed: Boolean = false,
)

/** Presentation-only formatting. Conversion always calls Core; no rates or arithmetic live here. */
object CoreMoneyPresentation {
    fun format(amount: BigDecimal?, currency: String, customSymbolMap: Map<String, String> = emptyMap(),
        locale: Locale = Locale.getDefault()): String {
        if (amount == null) return "— $currency"
        val symbol = if (currency in customSymbolMap) customSymbolMap.getValue(currency).ifBlank { currency }
            else runCatching { java.util.Currency.getInstance(currency).getSymbol(locale) }.getOrDefault(currency)
        val formatter = NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = amount.scale().coerceAtLeast(0)
            minimumFractionDigits = amount.scale().coerceAtLeast(0)
            maximumIntegerDigits = 309
        }
        return "$symbol ${formatter.format(amount)} · $currency"
    }

    suspend fun convert(coreCurrency: CurrencyInterface, amount: BigDecimal, from: String, to: String): CoreDisplayedMoney {
        return try {
            val converted = coreCurrency.convert(amount, from, to)
            CoreDisplayedMoney(amount, from, converted, to, complete = converted != null)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { CoreDisplayedMoney(amount, from, null, to, complete = false, failed = true) }
    }
}
