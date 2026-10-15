package io.github.hootkit_dev.hootbook.presentation.ui.features.investments

import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.presentation.format.coreDateText
import java.math.BigDecimal

/** Selection identity follows Core's per-connection / per-account / per-model uniqueness. */
data class BrokerAccountRef(val connectionId: String, val brokerAccountId: String)

data class CoreHoldingKey(val instrumentId: String, val currency: String, val model: String) {
    companion object {
        fun of(holding: Holding) =
            CoreHoldingKey(holding.instrumentId, holding.currency, holding.model)
    }
}

enum class InvestmentFailureCode {
    BROKERAGE,
    INVALID,
    CONFLICT,
    NOT_FOUND,
    NETWORK,
    STORAGE,
    PLATFORM,
    CANCELLED,
    UNKNOWN,
}

data class CoreInvestmentFailure(
    val code: InvestmentFailureCode,
    val kind: BrokerageErrorKind? = null,
)

/** Never surface message/detail, a URL, server response, credential or Throwable in UI state. */
internal fun investmentFailure(failure: Throwable): CoreInvestmentFailure =
    when (failure) {
        is CoreException.Brokerage ->
            CoreInvestmentFailure(InvestmentFailureCode.BROKERAGE, failure.kind)
        is CoreException.Invalid -> CoreInvestmentFailure(InvestmentFailureCode.INVALID)
        is CoreException.Conflict -> CoreInvestmentFailure(InvestmentFailureCode.CONFLICT)
        is CoreException.NotFound -> CoreInvestmentFailure(InvestmentFailureCode.NOT_FOUND)
        is CoreException.Network -> CoreInvestmentFailure(InvestmentFailureCode.NETWORK)
        is CoreException.Storage -> CoreInvestmentFailure(InvestmentFailureCode.STORAGE)
        is CoreException.Platform -> CoreInvestmentFailure(InvestmentFailureCode.PLATFORM)
        is CoreException.Cancelled -> CoreInvestmentFailure(InvestmentFailureCode.CANCELLED)
        else -> CoreInvestmentFailure(InvestmentFailureCode.UNKNOWN)
    }

/** Exact display only: no price-times-quantity, currency conversion, cost or P&L arithmetic. */
internal fun investmentDecimal(amount: BigDecimal?): String = amount?.toPlainString() ?: "—"

internal fun investmentDate(date: LocalDate): String = date.coreDateText()

internal fun investmentTime(time: LocalDateTime?): String =
    time?.coreDateText(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) ?: "—"

internal fun linkedAccount(
    ref: BrokerAccountRef,
    connections: List<BrokerageConnection>,
): BrokerageAccount? =
    connections
        .find { it.id == ref.connectionId }
        ?.accounts
        ?.find { it.accountId == ref.brokerAccountId }

internal fun availableLinkAccounts(ref: BrokerAccountRef, accounts: List<Account>): List<Account> =
    accounts
        .filter { account ->
            !account.archived &&
                account.kind != AccountKind.CREDIT_CARD &&
                account.kind != AccountKind.WALLET &&
                (account.broker == null ||
                    account.broker?.let {
                        it.connectionId == ref.connectionId &&
                            it.brokerAccountId == ref.brokerAccountId
                    } == true)
        }
        .sortedBy { if (it.kind == AccountKind.INVESTMENT) 0 else 1 }

internal fun buildBrokerLedgerAccount(
    connection: BrokerageConnection,
    brokerAccount: BrokerageAccount,
    providers: List<BrokerageProviderInfo>,
    institutions: List<Institution>,
    existingAccounts: List<Account>,
    defaultCurrency: String,
): NewAccount {
    val provider = providers.find { it.id == connection.providerId }
    val institution = institutions.find { it.key == provider?.institutionKey }
    val mainCurrency =
        brokerAccount.cash.firstOrNull()?.currency
            ?: brokerAccount.valueByCurrency.firstOrNull()?.currency
            ?: brokerAccount.holdings.firstOrNull()?.currency
            ?: institution?.currency?.takeIf { it.isNotBlank() }
            ?: defaultCurrency.ifBlank { "CNY" }
    val last4 = brokerAccount.accountId.takeLast(4).takeIf { it.matches(Regex("[0-9]{4}")) }
    val baseName =
        connection.label.trim().ifEmpty {
            institution?.chineseName?.takeIf { it.isNotBlank() }
                ?: institution?.englishName?.takeIf { it.isNotBlank() }
                ?: connection.providerId.ifBlank { "Investment" }
        }
    val preferredName =
        if (connection.accounts.size > 1) "$baseName (${brokerAccount.accountId})" else baseName
    fun collides(candidate: String): Boolean =
        existingAccounts.any { it.name.equals(candidate, ignoreCase = true) && it.last4 == last4 }
    var uniqueName = preferredName
    if (collides(uniqueName)) {
        val withAccount = "$baseName (${brokerAccount.accountId})"
        if (!collides(withAccount)) {
            uniqueName = withAccount
        } else {
            var suffix = 2
            while (collides("$withAccount #$suffix")) {
                suffix++
            }
            uniqueName = "$withAccount #$suffix"
        }
    }
    return NewAccount(
        name = uniqueName,
        last4 = last4,
        kind = AccountKind.INVESTMENT,
        mainCurrency = mainCurrency,
        openingBalance = null,
        openingAt = null,
        creditLimit = null,
        statementDay = null,
        dueDay = null,
        institutionKey = institution?.key,
        icon = institution?.iconName?.takeIf { it.isNotBlank() } ?: "type_finance_chart_increasing",
        color = institution?.color?.takeIf { it.isNotBlank() } ?: "#33B5E5",
        extraCurrencies = emptyList(),
    )
}

/** Operation-private references only, never a state/saved-state/route/preferences object. */
internal class InvestmentCredentialSubmission(fields: Map<String, String>) : AutoCloseable {
    private val values = LinkedHashMap(fields)

    fun fields(): Map<String, String> = values

    override fun close() {
        values.clear()
    }

    override fun toString() = "InvestmentCredentialSubmission([redacted])"
}
