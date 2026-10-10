package io.github.hootkit_dev.hootbook.domain.usecase

import io.github.hootkit_dev.hootbook.data.currency.CurrencyConversionService
import io.github.hootkit_dev.hootbook.data.database.dao.PocketBalance
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.utils.sumOfBigDecimal
import java.math.BigDecimal

/** Bank name + last 4: how preferences (the default account) name an account. */
fun AccountBalanceEntity.accountKey(): String = "${bankName}_$accountLast4"

/**
 * Converts every balance into [targetCurrency] and returns assets minus credit-card debt.
 *
 * Home and Profile have to show the same number, so both go through here instead of
 * summing balances themselves.
 */
suspend fun List<AccountBalanceEntity>.netWorthIn(
    targetCurrency: String,
    conversionService: CurrencyConversionService,
    investmentSnapshots: Map<String, BigDecimal> = emptyMap(),
    // Every currency of every account; an account found here counts each of its currencies
    pockets: List<PocketBalance> = emptyList()
): BigDecimal {
    val (creditCards, assets) = withPockets(pockets).partition { it.isCreditCard }
    return assets.convertedTotal(targetCurrency, conversionService) -
        creditCards.convertedTotal(targetCurrency, conversionService) +
        investmentSnapshots.convertedTotal(targetCurrency, conversionService)
}

/** Each account once per currency it holds (see [PocketBalance]); accounts not in [pockets] as they are. */
fun List<AccountBalanceEntity>.withPockets(pockets: List<PocketBalance>): List<AccountBalanceEntity> {
    if (pockets.isEmpty()) return this
    val byAccount = pockets.groupBy { it.accountId }
    return flatMap { account ->
        account.accountId?.let { byAccount[it] }?.map { account.copy(balance = it.balance, currency = it.currency) }
            ?: listOf(account)
    }
}

/** What a card owes across all its currencies, in its main currency. */
suspend fun AccountBalanceEntity.owedAcrossCurrencies(
    pockets: List<PocketBalance>,
    conversionService: CurrencyConversionService
): BigDecimal {
    val own = pockets.filter { it.accountId == accountId }.takeIf { it.isNotEmpty() } ?: return balance
    var total = BigDecimal.ZERO
    for (pocket in own) total += conversionService.convertAmount(pocket.balance, pocket.currency, currency)
    return total
}

/** Total of [AccountBalanceEntity.balance] expressed in [targetCurrency]. */
suspend fun List<AccountBalanceEntity>.convertedTotal(
    targetCurrency: String,
    conversionService: CurrencyConversionService
): BigDecimal {
    if (none { it.currency != targetCurrency }) {
        return sumOfBigDecimal { it.balance }
    }
    var total = BigDecimal.ZERO
    for (account in this) {
        total += if (account.currency == targetCurrency) {
            account.balance
        } else {
            conversionService.convertAmount(
                amount = account.balance,
                fromCurrency = account.currency,
                toCurrency = targetCurrency
            )
        }
    }
    return total
}

private suspend fun Map<String, BigDecimal>.convertedTotal(
    targetCurrency: String,
    conversionService: CurrencyConversionService
): BigDecimal {
    if (isEmpty()) return BigDecimal.ZERO
    var total = BigDecimal.ZERO
    for ((currency, amount) in this) {
        total += if (currency == targetCurrency) {
            amount
        } else {
            conversionService.convertAmount(
                amount = amount,
                fromCurrency = currency,
                toCurrency = targetCurrency
            )
        }
    }
    return total
}

/**
 * Net worth at the end of each day from [from] to [to]: every currency of every account at its
 * latest balance by that day (carried forward on days without a row; what a card owes is
 * subtracted), each converted at [rates] (currency → rate into the target, today's). Days before
 * the first balance are left out.
 */
fun netWorthByDay(
    rows: List<AccountBalanceEntity>,
    from: java.time.LocalDate,
    to: java.time.LocalDate,
    rates: Map<String, BigDecimal>,
    isCard: (AccountBalanceEntity) -> Boolean = { it.isCreditCard }
): List<Pair<java.time.LocalDate, BigDecimal>> {
    val sorted = rows.sortedWith(compareBy({ it.timestamp }, { it.id }))
    val latest = HashMap<String, AccountBalanceEntity>()
    var next = 0
    val days = mutableListOf<Pair<java.time.LocalDate, BigDecimal>>()
    var day = from
    while (!day.isAfter(to)) {
        while (next < sorted.size && !sorted[next].timestamp.toLocalDate().isAfter(day)) {
            val row = sorted[next++]
            latest["${row.accountId ?: "${row.bankName}_${row.accountLast4}"}|${row.currency}"] = row
        }
        if (latest.isNotEmpty()) {
            val total = latest.values.fold(BigDecimal.ZERO) { sum, row ->
                val value = row.balance.multiply(rates[row.currency] ?: BigDecimal.ONE)
                if (isCard(row)) sum - value else sum + value
            }
            days += day to total
        }
        day = day.plusDays(1)
    }
    return days
}
