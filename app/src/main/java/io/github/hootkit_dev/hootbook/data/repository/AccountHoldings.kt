package io.github.hootkit_dev.hootbook.data.repository

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.hootkit_dev.hootbook.data.currency.CurrencyConversionService
import io.github.hootkit_dev.hootbook.data.database.dao.AccountBalanceDao
import io.github.hootkit_dev.hootbook.data.database.dao.PocketBalance
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.di.ApplicationScope
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * What one account holds: the balance of each of its currencies and, when there is more than
 * one, their sum in the app's main currency (owed amounts for a card).
 */
data class AccountHoldings(
    val pockets: List<PocketBalance>,
    val total: BigDecimal,
    val totalCurrency: String
) {
    val isMultiCurrency: Boolean get() = pockets.size > 1
}

/** Every account's holdings by account id, kept live for all screens. */
@Singleton
class AccountHoldingsSource @Inject constructor(
    accountBalanceDao: AccountBalanceDao,
    currencyRepository: CurrencyRepository,
    private val conversion: CurrencyConversionService,
    @ApplicationScope scope: CoroutineScope
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val holdings: StateFlow<Map<Long, AccountHoldings>> =
        combine(accountBalanceDao.observePocketBalances(), currencyRepository.effectiveBaseCurrencyCode) { pockets, main ->
            pockets to main
        }
            .mapLatest { (pockets, main) ->
                pockets.groupBy { it.accountId }.mapValues { (_, own) -> AccountHoldings(own, total(own, main), main) }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private suspend fun total(pockets: List<PocketBalance>, currency: String): BigDecimal {
        var sum = BigDecimal.ZERO
        for (pocket in pockets) sum += conversion.convertAmount(pocket.balance, pocket.currency, currency)
        return sum
    }
}

/**
 * Each account once per currency it holds, with that currency's balance: what totals add up.
 * Accounts without holdings (none loaded yet) stay as they are.
 */
fun List<AccountBalanceEntity>.byCurrency(holdings: Map<Long, AccountHoldings>): List<AccountBalanceEntity> =
    flatMap { account ->
        val pockets = account.accountId?.let { holdings[it]?.pockets }
        if (pockets.isNullOrEmpty()) listOf(account)
        else pockets.map { account.copy(balance = it.balance, currency = it.currency) }
    }

/** The live holdings for composables that show accounts; empty where no source is provided (previews). */
val LocalAccountHoldings = staticCompositionLocalOf<Map<Long, AccountHoldings>> { emptyMap() }
