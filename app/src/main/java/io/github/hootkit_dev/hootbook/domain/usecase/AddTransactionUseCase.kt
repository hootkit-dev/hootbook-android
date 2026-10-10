package io.github.hootkit_dev.hootbook.domain.usecase

import io.github.hootkit_dev.hootbook.data.repository.applyBalanceMoves
import io.github.hootkit_dev.hootbook.data.repository.balanceMoves
import io.github.hootkit_dev.hootbook.utils.SubscriptionUtils

import io.github.hootkit_dev.hootbook.data.database.entity.SubscriptionEntity
import io.github.hootkit_dev.hootbook.data.database.entity.SubscriptionState
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionType
import io.github.hootkit_dev.hootbook.data.repository.AccountBalanceRepository
import io.github.hootkit_dev.hootbook.data.repository.SubscriptionRepository
import io.github.hootkit_dev.hootbook.data.repository.TransactionRepository
import androidx.room.withTransaction
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

class AddTransactionUseCase
@Inject
constructor(
        private val transactionRepository: TransactionRepository,
        private val subscriptionRepository: SubscriptionRepository,
        private val accountBalanceRepository: AccountBalanceRepository,
        // The row, its balance legs and its subscription are written together (null in unit tests)
        private val database: io.github.hootkit_dev.hootbook.data.database.HootbookDatabase? = null
) {
    suspend fun execute(
            amount: BigDecimal,
            merchant: String,
            category: String,
            type: TransactionType,
            date: LocalDateTime,
            notes: String? = null,
            subcategory: String? = null,
            isRecurring: Boolean = false,
            bankName: String? = null,
            accountLast4: String? = null,
            currency: String,
            sourceAccountId: Long? = null,
            targetAccountBankName: String? = null,
            targetAccountLast4: String? = null,
            // What reached the target account of a transfer in its own currency, if that differs
            targetAmount: BigDecimal? = null,
            // The target account's currency the transfer lands in; null: the one matching, else its main one
            targetCurrency: String? = null,
            billingCycle: String? = null,
            createSubscription: Boolean = true,
            attachments: String = ""
    ): Long {
        val write: suspend () -> Long = write@{
        // Generate a unique hash for manual transactions
        val transactionHash =
                generateManualTransactionHash(amount = amount, merchant = merchant, date = date)

        val account = if (bankName != null && accountLast4 != null) accountBalanceRepository.account(bankName, accountLast4) else null
        val target = if (type == TransactionType.TRANSFER && targetAccountBankName != null && targetAccountLast4 != null) {
            accountBalanceRepository.account(targetAccountBankName, targetAccountLast4)
        } else null
        val toCurrency = target?.let {
            accountBalanceRepository.pocketCurrency(it.name, it.last4, targetCurrency ?: currency)
        }

        // Create the transaction entity
        val transaction =
                TransactionEntity(
                        amount = amount,
                        merchantName = merchant,
                        category = category,
                        subcategory = subcategory,
                        transactionType = type,
                        dateTime = date,
                        description = notes,
                        bankName = bankName ?: "Manual Entry",
                        accountNumber = accountLast4,
                        fromAccount = accountLast4,
                        toAccount = targetAccountLast4,
                        toAmount = targetAmount?.takeIf { type == TransactionType.TRANSFER && it.compareTo(amount) != 0 },
                        accountId = account?.id,
                        toAccountId = target?.id,
                        toCurrency = toCurrency,
                        balanceAfter = null,
                        transactionHash = transactionHash,
                        isRecurring = isRecurring,
                        createdAt = LocalDateTime.now(),
                        updatedAt = LocalDateTime.now(),
                        currency = currency,
                        billingCycle = billingCycle,
                        attachments = attachments
                )

        // Insert the transaction
        val transactionId = transactionRepository.insertTransaction(transaction)
        // A duplicate hash is ignored (-1): nothing was added, so no balance moves
        if (transactionId <= 0L) return@write transactionId

        // Move the balances the way an edit would (see balanceMoves)
        if (bankName != null && accountLast4 != null) {
            val target = if (targetAccountBankName != null && targetAccountLast4 != null) targetAccountBankName to targetAccountLast4 else null
            val moves = balanceMoves(transaction.copy(id = transactionId), bankName to accountLast4, target, toCurrency)
            applyBalanceMoves(moves, transactionId, date) { move, id, at ->
                accountBalanceRepository.insertTransactionBalance(
                    bankName = move.bankName,
                    accountLast4 = move.accountLast4,
                    amount = move.amount,
                    transactionType = move.type,
                    explicitBalance = move.reportedBalance,
                    timestamp = at,
                    transactionId = id,
                    creditLimit = null,
                    isCreditCard = false,
                    
                    currency = move.currency
                )
            }
        }

        // If marked as recurring, create a subscription
        if (createSubscription && isRecurring && transactionId != -1L) {
            val nextPaymentDate = SubscriptionUtils.calculateNextPaymentDate(date.toLocalDate(), billingCycle)

            val subscription =
                    SubscriptionEntity(
                            merchantName = merchant,
                            amount = amount,
                            nextPaymentDate = nextPaymentDate,
                            state = SubscriptionState.ACTIVE,
                            bankName = bankName ?: "Manual Entry",
                            category = category,
                            subcategory = subcategory,
                            createdAt = LocalDateTime.now(),
                            updatedAt = LocalDateTime.now(),
                            currency = currency,
                            billingCycle = billingCycle,
                            lastPaidDate = date.toLocalDate()
                    )

            subscriptionRepository.insertSubscription(subscription)
        }
        transactionId
        }
        return database?.withTransaction { write() } ?: write()
    }


    private fun generateManualTransactionHash(
            amount: BigDecimal,
            merchant: String,
            date: LocalDateTime
    ): String {
        // Create a unique hash for manual transactions. The insert ignores a duplicate hash, so
        // two entries that look alike (same amount, a blank merchant or a template, a time
        // picked to the minute) must still differ: the entry time makes each one unique.
        val data = "MANUAL_${amount}_${merchant}_${date}_${System.nanoTime()}"

        return MessageDigest.getInstance("MD5").digest(data.toByteArray()).joinToString("") {
            "%02x".format(it)
        }
    }
}
