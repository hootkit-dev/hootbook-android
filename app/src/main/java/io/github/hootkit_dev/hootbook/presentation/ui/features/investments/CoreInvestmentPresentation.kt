package io.github.hootkit_dev.hootbook.presentation.ui.features.investments

import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.presentation.format.coreDateText
import java.math.BigDecimal

/** Selection identity follows Core's per-connection / per-account / per-model uniqueness. */
data class BrokerAccountRef(val connectionId: Long, val brokerAccountId: String)

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
    accounts.filter { account ->
        !account.archived &&
            account.kind != AccountKind.CREDIT_CARD &&
            account.kind != AccountKind.WALLET &&
            (account.broker == null ||
                account.broker?.let {
                    it.connectionId == ref.connectionId && it.brokerAccountId == ref.brokerAccountId
                } == true)
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
