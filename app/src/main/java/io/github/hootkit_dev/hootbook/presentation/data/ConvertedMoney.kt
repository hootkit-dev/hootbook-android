package io.github.hootkit_dev.hootbook.presentation.data

import dev.hootkit.core.CurrencyInterface
import java.math.BigDecimal
import kotlinx.coroutines.CancellationException

/** Original value and Core's nullable conversion result, with query failure state. */
data class ConvertedMoney(
    val originalAmount: BigDecimal,
    val originalCurrency: String,
    val amount: BigDecimal?,
    val currency: String,
    val complete: Boolean,
    val failed: Boolean = false,
)

internal suspend fun queryConvertedMoney(
    coreCurrency: CurrencyInterface,
    amount: BigDecimal,
    from: String,
    to: String,
): ConvertedMoney = try {
    val converted = coreCurrency.convert(amount, from, to)
    ConvertedMoney(amount, from, converted, to, complete = converted != null)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    ConvertedMoney(amount, from, null, to, complete = false, failed = true)
}
