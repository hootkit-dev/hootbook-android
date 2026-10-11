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
    /**
     * The amount with its currency's symbol (¥ 10,554.00), at Core's exact precision. [useCode]
     * writes the ISO code instead, for lists that mix currencies sharing a symbol. A custom
     * symbol set in Currencies wins; a blank one falls back to the code.
     */
    fun format(amount: BigDecimal?, currency: String, customSymbolMap: Map<String, String> = emptyMap(),
        locale: Locale = Locale.getDefault(), useCode: Boolean = false): String {
        if (amount == null) return "— $currency"
        val label = if (useCode) currency else customSymbolMap[currency]?.ifBlank { currency }
            ?: runCatching { java.util.Currency.getInstance(currency).getSymbol(locale) }.getOrDefault(currency)
        val formatter = NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = amount.scale().coerceAtLeast(0)
            minimumFractionDigits = amount.scale().coerceAtLeast(0)
            maximumIntegerDigits = 309
        }
        return "$label ${formatter.format(amount)}"
    }

    suspend fun convert(coreCurrency: CurrencyInterface, amount: BigDecimal, from: String, to: String): CoreDisplayedMoney {
        return try {
            val converted = coreCurrency.convert(amount, from, to)
            CoreDisplayedMoney(amount, from, converted, to, complete = converted != null)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { CoreDisplayedMoney(amount, from, null, to, complete = false, failed = true) }
    }
}
