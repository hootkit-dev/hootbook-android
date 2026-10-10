package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import io.github.hootkit_dev.hootbook.data.repository.AccountHoldings
import io.github.hootkit_dev.hootbook.data.repository.byCurrency
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.presentation.ui.components.sortedForDisplay
import io.github.hootkit_dev.hootbook.data.brokerage.BrokerConnection
import java.math.BigDecimal

internal enum class AccountSectionKind { WALLETS, BANKS, CREDIT_CARDS, INVESTMENTS }

internal data class AccountSection(
    val kind: AccountSectionKind,
    val accounts: List<AccountBalanceEntity>,
    // Never add balances in different currencies or mix assets with card debt.
    val totals: Map<String, BigDecimal>,
    // Connected brokerage accounts, counted in the investments summary but listed as connections
    val linkedCount: Int = 0
) {
    val collapsedPreviewCount: Int
        get() = 0

    fun visibleAccounts(expanded: Boolean): List<AccountBalanceEntity> =
        if (expanded) accounts else accounts.take(collapsedPreviewCount)

    fun showFooterToggle(expanded: Boolean): Boolean =
        expanded && accounts.isNotEmpty()
}

internal data class AccountSections(
    val visible: List<AccountSection>
)

internal fun AccountBalanceEntity.listKey(): String =
    "account:${bankName.length}:$bankName:$accountLast4"

internal fun buildAccountSections(
    accounts: List<AccountBalanceEntity>,
    mainKey: String? = null,
    holdings: Map<Long, AccountHoldings> = emptyMap()
): AccountSections {
    // Same order as the account picker: one bank's accounts together, the main account first.
    val visible = accounts.sortedForDisplay(mainKey)
    return AccountSections(
        visible = AccountSectionKind.entries.map { kind ->
            val members = visible.filter {
                when (kind) {
                    AccountSectionKind.WALLETS -> it.isWallet && !it.isCreditCard
                    AccountSectionKind.BANKS -> !it.isWallet && !it.isCreditCard && it.category() != AccountCategory.INVESTMENTS
                    AccountSectionKind.CREDIT_CARDS -> it.isCreditCard
                    AccountSectionKind.INVESTMENTS -> it.category() == AccountCategory.INVESTMENTS
                }
            }
            // Totals per currency count every currency an account holds
            AccountSection(kind, members, members.byCurrency(holdings).groupBy { it.currency }.toSortedMap().mapValues { (_, group) ->
                group.fold(BigDecimal.ZERO) { total, account -> total + account.balance }
            })
        }
    )
}

/** Investments with the connected brokers' latest snapshots added, as Home's overview counts them. */
internal fun AccountSection.withBrokerSnapshots(connections: List<BrokerConnection>): AccountSection {
    if (connections.isEmpty()) return this
    val merged = totals.toMutableMap()
    connections.investmentSnapshotsOrEmpty().forEach { (currency, amount) ->
        merged[currency] = (merged[currency] ?: BigDecimal.ZERO) + amount
    }
    return copy(totals = merged.toSortedMap(), linkedCount = connections.sumOf { it.accounts.size })
}
