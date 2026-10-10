package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.data.brokerage.BrokerConnection
import io.github.hootkit_dev.hootbook.domain.brokerage.BrokerageAccount
import io.github.hootkit_dev.hootbook.presentation.common.icons.InstitutionCatalog
import java.math.BigDecimal
import java.math.RoundingMode

enum class AccountCategory(val titleRes: Int) {
    WALLETS(R.string.section_wallets), BANKS(R.string.section_bank_accounts),
    CREDIT_CARDS(R.string.section_credit_cards), INVESTMENTS(R.string.overview_investments)
}

fun AccountBalanceEntity.category(): AccountCategory = when {
    isCreditCard -> AccountCategory.CREDIT_CARDS
    isWallet -> AccountCategory.WALLETS
    InstitutionCatalog.find(bankName)?.isBroker == true -> AccountCategory.INVESTMENTS
    else -> AccountCategory.BANKS
}

fun AccountBalanceEntity.isInvestmentAccount(): Boolean =
    category() == AccountCategory.INVESTMENTS

internal fun BrokerageAccount.snapshotByCurrency(): Map<String, BigDecimal> {
    val totals = mutableMapOf<String, BigDecimal>()
    holdings.groupBy { it.currency }.forEach { (currency, rows) ->
        totals[currency] = rows.fold(BigDecimal.ZERO) { n, holding ->
            n + (holding.marketValue?.toBigDecimalOrNull() ?: BigDecimal.ZERO)
        }
    }
    cashBalances.forEach { cash ->
        val amount = cash.endingCash.toBigDecimalOrNull() ?: return@forEach
        totals[cash.currency] = (totals[cash.currency] ?: BigDecimal.ZERO) + amount
    }
    return totals
}

internal fun List<BrokerConnection>.snapshotTotals(): Map<String, BigDecimal> {
    val totals = mutableMapOf<String, BigDecimal>()
    for (account in flatMap { it.accounts }) {
        for ((currency, amount) in account.snapshotByCurrency()) {
            totals[currency] = (totals[currency] ?: BigDecimal.ZERO) + amount
        }
    }
    return totals
}

internal fun List<BrokerConnection>.snapshotValuationsComplete(): Boolean =
    flatMap { it.accounts }.flatMap { it.holdings }.all { it.marketValue?.toBigDecimalOrNull() != null }

internal fun List<BrokerConnection>.investmentSnapshotsOrEmpty(): Map<String, BigDecimal> =
    if (isNotEmpty() && snapshotValuationsComplete()) snapshotTotals() else emptyMap()

internal enum class OverviewStatus { READY, LOADING, CONNECT, UNAVAILABLE, MULTIPLE_SOURCES }
internal data class AccountOverviewItem(
    val category: AccountCategory,
    val count: Int = 0,
    val amount: BigDecimal? = null,
    val currency: String = "CNY",
    val status: OverviewStatus = OverviewStatus.LOADING,
    val isSnapshot: Boolean = false,
    val hasSnapshots: Boolean = false,
    val converted: Boolean = false
)

/** A missing rate must never relabel an unconverted amount as the target currency. */
internal suspend fun overviewTotal(
    totals: Map<String, BigDecimal>, currency: String,
    rate: suspend (String, String) -> BigDecimal?
): BigDecimal? {
    var sum = BigDecimal.ZERO
    for ((source, value) in totals) {
        val exchange = if (source == currency) BigDecimal.ONE else rate(source, currency) ?: return null
        sum += value * exchange
    }
    return sum.setScale(2, RoundingMode.HALF_UP)
}

internal suspend fun buildOverview(
    accounts: List<AccountBalanceEntity>, connections: List<BrokerConnection>, currency: String,
    brokerLoaded: Boolean, brokerFailed: Boolean,
    rate: suspend (String, String) -> BigDecimal?
): List<AccountOverviewItem> = AccountCategory.entries.map { category ->
    val members = accounts.filter { it.category() == category }
    val manualTotals = members.groupBy { it.currency }.mapValues { (_, list) -> list.fold(BigDecimal.ZERO) { n, a -> n + a.balance } }
    if (category != AccountCategory.INVESTMENTS) {
        val amount = overviewTotal(manualTotals, currency, rate)
        AccountOverviewItem(category, members.size, amount, currency,
            if (amount == null) OverviewStatus.UNAVAILABLE else OverviewStatus.READY,
            converted = manualTotals.keys.any { it != currency })
    } else {
        val brokerAccounts = connections.flatMap { it.accounts }
        val holdings = brokerAccounts.flatMap { it.holdings }
        val snapshotTotals = connections.snapshotTotals()
        val status = when {
            brokerFailed -> OverviewStatus.UNAVAILABLE
            !brokerLoaded -> OverviewStatus.LOADING
            members.isEmpty() && connections.isEmpty() -> OverviewStatus.CONNECT
            holdings.any { it.marketValue?.toBigDecimalOrNull() == null } -> OverviewStatus.UNAVAILABLE
            else -> OverviewStatus.READY
        }
        val totals = snapshotTotals.toMutableMap()
        manualTotals.forEach { (source, value) ->
            totals[source] = (totals[source] ?: BigDecimal.ZERO) + value
        }
        val amount = if (status == OverviewStatus.READY) overviewTotal(totals, currency, rate) else null
        AccountOverviewItem(category, members.size + brokerAccounts.size, amount, currency,
            if (status == OverviewStatus.READY && amount == null) OverviewStatus.UNAVAILABLE else status,
            isSnapshot = connections.isNotEmpty(), hasSnapshots = connections.isNotEmpty(),
            converted = totals.keys.any { it != currency })
    }
}

internal fun AccountOverviewItem.netWorthContribution(): BigDecimal? = when {
    category == AccountCategory.CREDIT_CARDS -> amount?.negate()
    else -> amount
}
