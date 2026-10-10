package io.github.hootkit_dev.hootbook.data.repository

import androidx.room.withTransaction
import io.github.hootkit_dev.hootbook.data.database.HootbookDatabase
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saves an edited transaction and moves its effect on account balances with it.
 *
 * The old effect is taken back the way deleting the transaction does, and the new one applied
 * the way adding it does, so a changed amount, type, date, account or transfer target all land
 * right, credit cards included (their balance is what is owed). Editing used to adjust balances
 * by a delta that assumed an ordinary account, which ran the wrong way on cards.
 */
@Singleton
class TransactionEditor @Inject constructor(private val database: HootbookDatabase) {

    /**
     * Replaces [original] with [updated]. [newTarget] is the account a transfer now goes to, when
     * the user picked one; otherwise a transfer keeps its current target.
     */
    suspend fun update(original: TransactionEntity, updated: TransactionEntity, newTarget: AccountBalanceEntity? = null) {
        database.withTransaction {
            val balances = database.accountBalanceDao()
            val linked = balances.getBalancesForTransaction(original.id)
            // The receiving row of a transfer: its target account and currency (a transfer between
            // two currencies of one account has both rows on the same account)
            val oldTarget = if (original.transactionType != TransactionType.TRANSFER) null else
                linked.firstOrNull {
                    original.toAccountId != null && it.accountId == original.toAccountId &&
                        (original.toAccountId != original.accountId || it.currency == original.toCurrency)
                } ?: linked.firstOrNull { !(it.bankName == original.bankName && it.accountLast4 == original.accountNumber) }
            val oldSource = linked.firstOrNull { it !== oldTarget }
            val accountChanged = original.bankName != updated.bankName || original.accountNumber != updated.accountNumber

            // Where the edited transaction moves money now
            val account = if (updated.bankName != null && updated.accountNumber != null) {
                balances.accountFor(updated.bankName, updated.accountNumber)
            } else null
            val target = when {
                updated.transactionType != TransactionType.TRANSFER -> null
                newTarget != null -> balances.accountFor(newTarget.bankName, newTarget.accountLast4)
                oldTarget != null && oldTarget.accountLast4 == updated.toAccount -> balances.accountFor(oldTarget.bankName, oldTarget.accountLast4)
                else -> null
            }
            val toCurrency = target?.let {
                val wanted = updated.toCurrency ?: newTarget?.currency ?: oldTarget?.currency
                balances.pocketCurrency(it.name, it.last4, wanted ?: it.mainCurrency)
            }
            val saved = updated.copy(accountId = account?.id, toAccountId = target?.id, toCurrency = toCurrency)

            if (linked.isNotEmpty() && !original.isDeleted) {
                // Recalculates every affected account as if the transaction were gone; its rows
                // then pass the running balance through and can be removed without a trace
                balances.changeTransactionDeletion(listOf(original.id), deleted = true)
            }
            linked.forEach { balances.deleteBalanceById(it.id) }
            database.transactionDao().updateTransaction(saved)

            // With an account, the edited transaction always moves its balance, even one that had
            // no rows before (a copy, or a transfer saved without a target)
            if (saved.isDeleted || account == null) return@withTransaction

            // A balance the bank reported with the transaction still holds if only details changed
            val reported = oldSource?.takeIf {
                it.sourceType == SOURCE_TRANSACTION_SMS_BALANCE && !accountChanged &&
                    original.amount.compareTo(saved.amount) == 0 &&
                    original.transactionType == saved.transactionType && original.dateTime == saved.dateTime
            }?.balance
            val moves = balanceMoves(saved, account.name to account.last4, target?.let { it.name to it.last4 }, toCurrency, reported)
            applyBalanceMoves(moves, saved.id, saved.dateTime) { move, id, at ->
                balances.insertTransactionBalance(
                    bankName = move.bankName, accountLast4 = move.accountLast4, amount = move.amount,
                    transactionType = move.type, explicitBalance = move.reportedBalance,
                    timestamp = at, transactionId = id, creditLimit = null,
                    isCreditCard = false, currency = move.currency
                )
            }
        }
    }

    private companion object {
        const val SOURCE_TRANSACTION_SMS_BALANCE = "TRANSACTION_SMS_BALANCE"
    }
}
