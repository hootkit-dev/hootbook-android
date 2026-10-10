package io.github.hootkit_dev.hootbook.data.currency

import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import java.math.BigDecimal

/**
 * A list's transactions in the main currency, from [CurrencyConversionService.convert]:
 * converted [amounts] by transaction id, and the [pendingCurrencies] whose rate is still being
 * fetched. A foreign transaction in neither has no rate available.
 */
data class Conversions(
    val amounts: Map<Long, BigDecimal> = emptyMap(),
    val pendingCurrencies: Set<String> = emptySet()
) {
    fun amountOf(transaction: TransactionEntity): BigDecimal? = amounts[transaction.id]

    fun isLoading(transaction: TransactionEntity): Boolean =
        transaction.currency.uppercase() in pendingCurrencies
}
