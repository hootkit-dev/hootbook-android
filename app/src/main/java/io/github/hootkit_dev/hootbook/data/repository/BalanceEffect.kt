package io.github.hootkit_dev.hootbook.data.repository

import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionType
import java.math.BigDecimal
import java.time.LocalDateTime

/** One balance row a transaction writes: [amount] moves the account the way [type] does. */
internal data class BalanceMove(
    val bankName: String,
    val accountLast4: String,
    val amount: BigDecimal,
    val type: TransactionType,
    val currency: String,
    // A balance the bank reported with the transaction, kept instead of a computed one
    val reportedBalance: BigDecimal? = null
)

/**
 * The balance rows saved transaction [tx] writes, on [source] (bank name, last 4) and, for a
 * transfer, [target] in [targetCurrency]. Adding and editing a transaction both go through
 * here, so they cannot disagree: a transfer leaves its source as spending and arrives at its
 * target as income (in what arrived there, [TransactionEntity.toAmount], when the currencies
 * differ); a transfer without a target and a balance update move nothing.
 */
internal fun balanceMoves(
    tx: TransactionEntity,
    source: Pair<String, String>,
    target: Pair<String, String>?,
    targetCurrency: String?,
    reportedBalance: BigDecimal? = null
): List<BalanceMove> = when (tx.transactionType) {
    TransactionType.BALANCE_UPDATE -> emptyList()
    TransactionType.TRANSFER -> if (target == null) emptyList() else listOf(
        BalanceMove(source.first, source.second, tx.amount, TransactionType.EXPENSE, tx.currency),
        BalanceMove(target.first, target.second, tx.toAmount ?: tx.amount, TransactionType.INCOME, targetCurrency ?: tx.currency)
    )
    else -> listOf(BalanceMove(source.first, source.second, tx.amount, tx.transactionType, tx.currency, reportedBalance))
}

/** Writes [moves] for the transaction [transactionId] at [timestamp] through [insert]. */
internal suspend fun applyBalanceMoves(
    moves: List<BalanceMove>,
    transactionId: Long,
    timestamp: LocalDateTime,
    insert: suspend (BalanceMove, Long, LocalDateTime) -> Unit
) = moves.forEach { insert(it, transactionId, timestamp) }
