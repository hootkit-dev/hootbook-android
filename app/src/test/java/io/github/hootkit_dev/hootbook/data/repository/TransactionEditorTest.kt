package io.github.hootkit_dev.hootbook.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.hootkit_dev.hootbook.data.database.HootbookDatabase
import io.github.hootkit_dev.hootbook.data.database.SyncTriggers
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionType
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Credit card balances (amount owed) through adding, paying, editing and deleting, on a real database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransactionEditorTest {
    private lateinit var db: HootbookDatabase
    private lateinit var editor: TransactionEditor
    private val start = LocalDateTime.of(2026, 9, 1, 9, 0)
    private var hash = 0

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HootbookDatabase::class.java)
            .addCallback(SyncTriggers.Callback).allowMainThreadQueries().build()
        editor = TransactionEditor(db)
    }

    @After fun tearDown() = db.close()

    private suspend fun account(bank: String, last4: String, balance: String, card: Boolean, currency: String = "CNY") {
        db.accountBalanceDao().insertBalance(
            AccountBalanceEntity(
                bankName = bank, accountLast4 = last4, balance = BigDecimal(balance), timestamp = start,
                sourceType = "MANUAL", isCreditCard = card, creditLimit = if (card) BigDecimal("10000") else null,
                currency = currency
            )
        )
    }

    /** What AddTransactionUseCase does: the row, then its balance entries. */
    private suspend fun add(
        type: TransactionType, amount: String, day: Int, bank: String, last4: String,
        toBank: String? = null, toLast4: String? = null, currency: String = "CNY", toAmount: String? = null
    ): TransactionEntity {
        val tx = TransactionEntity(
            amount = BigDecimal(amount), merchantName = "m", category = "c", transactionType = type,
            dateTime = start.plusDays(day.toLong()), bankName = bank, accountNumber = last4, toAccount = toLast4,
            toAmount = toAmount?.let(::BigDecimal), transactionHash = "h${hash++}", currency = currency
        )
        val id = db.transactionDao().insertTransaction(tx)
        val balances = db.accountBalanceDao()
        if (type == TransactionType.TRANSFER) {
            balances.insertTransactionBalance(bank, last4, tx.amount, TransactionType.EXPENSE, null, tx.dateTime, id, null, false, currency)
            balances.insertTransactionBalance(toBank!!, toLast4!!, tx.toAmount ?: tx.amount, TransactionType.INCOME, null, tx.dateTime, id, null, false, currency)
        } else {
            balances.insertTransactionBalance(bank, last4, tx.amount, type, null, tx.dateTime, id, null, false, currency)
        }
        return tx.copy(id = id)
    }

    private suspend fun owed(bank: String = CARD, last4: String = CARD_LAST4) =
        db.accountBalanceDao().getLatestBalance(bank, last4)!!.balance.stripTrailingZeros().toPlainString()

    @Test fun purchasesRaiseAndPaymentsLowerWhatIsOwed() = runTest {
        account(CARD, CARD_LAST4, "0", card = true)
        account(BANK, BANK_LAST4, "5000", card = false)
        add(TransactionType.EXPENSE, "1200", 1, CARD, CARD_LAST4)
        add(TransactionType.TRANSFER, "1000", 2, BANK, BANK_LAST4, CARD, CARD_LAST4)
        assertEquals("200", owed())
        assertEquals("4000", owed(BANK, BANK_LAST4))
    }

    @Test fun payingMoreThanIsOwedLeavesACredit() = runTest {
        account(CARD, CARD_LAST4, "0", card = true)
        add(TransactionType.EXPENSE, "100", 1, CARD, CARD_LAST4)
        add(TransactionType.INCOME, "150", 2, CARD, CARD_LAST4)
        assertEquals("-50", owed())
        add(TransactionType.EXPENSE, "30", 3, CARD, CARD_LAST4)
        assertEquals("-20", owed())
    }

    @Test fun editingACardPurchaseMovesWhatIsOwedTheSameWay() = runTest {
        account(CARD, CARD_LAST4, "0", card = true)
        val purchase = add(TransactionType.EXPENSE, "100", 1, CARD, CARD_LAST4)
        add(TransactionType.EXPENSE, "40", 2, CARD, CARD_LAST4)
        editor.update(purchase, purchase.copy(amount = BigDecimal("150")))
        assertEquals("190", owed())
    }

    @Test fun editingARepaymentMovesBothAccounts() = runTest {
        account(CARD, CARD_LAST4, "0", card = true)
        account(BANK, BANK_LAST4, "5000", card = false)
        add(TransactionType.EXPENSE, "1200", 1, CARD, CARD_LAST4)
        val payment = add(TransactionType.TRANSFER, "1000", 2, BANK, BANK_LAST4, CARD, CARD_LAST4)
        editor.update(payment, payment.copy(amount = BigDecimal("1500")))
        assertEquals("-300", owed())
        assertEquals("3500", owed(BANK, BANK_LAST4))
        // The account keeps its own look: no snapshot row without icon or colour
        assertEquals(true, db.accountBalanceDao().getLatestBalance(CARD, CARD_LAST4)!!.isCreditCard)
    }

    @Test fun movingAPurchaseToAnotherDayOrAccount() = runTest {
        account(CARD, CARD_LAST4, "0", card = true)
        account(BANK, BANK_LAST4, "5000", card = false)
        val purchase = add(TransactionType.EXPENSE, "100", 5, CARD, CARD_LAST4)
        add(TransactionType.INCOME, "60", 3, CARD, CARD_LAST4)
        // Before the refund now: owed 100, then the refund brings it to 40
        val earlier = purchase.copy(dateTime = start.plusDays(2))
        editor.update(purchase, earlier)
        assertEquals("40", owed())
        // Paid from the bank instead: the card only has the refund, a credit of 60
        editor.update(earlier, earlier.copy(bankName = BANK, accountNumber = BANK_LAST4))
        assertEquals("-60", owed())
        assertEquals("4900", owed(BANK, BANK_LAST4))
    }

    @Test fun repaymentToAnotherCard() = runTest {
        account(CARD, CARD_LAST4, "500", card = true)
        account(OTHER_CARD, CARD_LAST4, "800", card = true)
        account(BANK, BANK_LAST4, "5000", card = false)
        val payment = add(TransactionType.TRANSFER, "300", 1, BANK, BANK_LAST4, CARD, CARD_LAST4)
        val other = db.accountBalanceDao().getLatestBalance(OTHER_CARD, CARD_LAST4)!!
        // Both cards end in the same digits: the picked account decides, not the last 4
        editor.update(payment, payment, newTarget = other)
        assertEquals("500", owed())
        assertEquals("500", owed(OTHER_CARD, CARD_LAST4))
        assertEquals("4700", owed(BANK, BANK_LAST4))
    }

    @Test fun deletingAndRestoringAPaymentRoundTrips() = runTest {
        account(CARD, CARD_LAST4, "0", card = true)
        add(TransactionType.EXPENSE, "100", 1, CARD, CARD_LAST4)
        val refund = add(TransactionType.INCOME, "150", 2, CARD, CARD_LAST4)
        db.accountBalanceDao().changeTransactionDeletion(listOf(refund.id), deleted = true)
        assertEquals("100", owed())
        db.accountBalanceDao().changeTransactionDeletion(listOf(refund.id), deleted = false)
        assertEquals("-50", owed())
    }

    @Test fun transferIntoAnAccountInAnotherCurrency() = runTest {
        account(HK_BANK, BANK_LAST4, "20000", card = false, currency = "HKD")
        account(BROKER, BROKER_LAST4, "250", card = false, currency = "USD")
        val transfer = add(
            TransactionType.TRANSFER, "5000", 1, HK_BANK, BANK_LAST4, BROKER, BROKER_LAST4,
            currency = "HKD", toAmount = "640"
        )
        val broker = db.accountBalanceDao().getLatestBalance(BROKER, BROKER_LAST4)!!
        assertEquals("890", broker.balance.stripTrailingZeros().toPlainString())
        assertEquals("USD", broker.currency)
        assertEquals("15000", owed(HK_BANK, BANK_LAST4))

        // A later back-dated entry recalculates the broker side with what arrived, not the HKD amount
        add(TransactionType.INCOME, "10", 0, BROKER, BROKER_LAST4, currency = "USD")
        assertEquals("900", owed(BROKER, BROKER_LAST4))

        // Editing the amount keeps the received side as given
        editor.update(transfer, transfer.copy(amount = BigDecimal("6000"), toAmount = BigDecimal("768")))
        assertEquals("1028", owed(BROKER, BROKER_LAST4))
        assertEquals("14000", owed(HK_BANK, BANK_LAST4))
        assertEquals("USD", db.accountBalanceDao().getLatestBalance(BROKER, BROKER_LAST4)!!.currency)
    }

    @Test fun oneAccountHoldsSeveralCurrencies() = runTest {
        account(BROKER, BROKER_LAST4, "100", card = false, currency = "USD")
        // A second currency: its first row adds it to the account
        account(BROKER, BROKER_LAST4, "10000", card = false, currency = "HKD")
        val broker = db.accountDao().findAccount(BROKER, BROKER_LAST4)!!
        assertEquals("USD", broker.mainCurrency)
        assertEquals(setOf("USD", "HKD"), db.accountDao().getCurrencies(broker.id).map { it.currency }.toSet())

        // Exchange inside the account: HKD out, USD in
        val fx = TransactionEntity(
            amount = BigDecimal("5000"), merchantName = "FX", category = "c", transactionType = TransactionType.TRANSFER,
            dateTime = start.plusDays(2), bankName = BROKER, accountNumber = BROKER_LAST4, toAccount = BROKER_LAST4,
            toAmount = BigDecimal("640"), accountId = broker.id, toAccountId = broker.id, toCurrency = "USD",
            transactionHash = "fx", currency = "HKD"
        )
        val fxId = db.transactionDao().insertTransaction(fx)
        val balances = db.accountBalanceDao()
        balances.insertTransactionBalance(BROKER, BROKER_LAST4, fx.amount, TransactionType.EXPENSE, null, fx.dateTime, fxId, null, false, "HKD")
        balances.insertTransactionBalance(BROKER, BROKER_LAST4, fx.toAmount!!, TransactionType.INCOME, null, fx.dateTime, fxId, null, false, "USD")
        assertEquals("5000", pocket("HKD"))
        assertEquals("740", pocket("USD"))
        // The account as the screens see it: its main currency
        assertEquals("USD", balances.getLatestBalance(BROKER, BROKER_LAST4)!!.currency)

        // An earlier USD dividend recalculates only the USD side, the exchange still counted as income there
        add(TransactionType.INCOME, "10", 1, BROKER, BROKER_LAST4, currency = "USD")
        assertEquals("750", pocket("USD"))
        assertEquals("5000", pocket("HKD"))

        // Editing the exchange moves both sides of the one account
        val saved = db.transactionDao().getTransactionById(fxId)!!
        editor.update(saved, saved.copy(amount = BigDecimal("6000"), toAmount = BigDecimal("768")))
        assertEquals("4000", pocket("HKD"))
        assertEquals("878", pocket("USD"))
    }

    private suspend fun pocket(currency: String) = db.accountBalanceDao()
        .getLatestBalanceOnOrBefore(BROKER, BROKER_LAST4, currency, start.plusYears(1))!!
        .balance.stripTrailingZeros().toPlainString()

    private companion object {
        const val HK_BANK = "汇丰香港"
        const val BROKER = "盈透证券"
        const val BROKER_LAST4 = "0001"
        const val CARD = "招商银行"
        const val OTHER_CARD = "交通银行"
        const val CARD_LAST4 = "1234"
        const val BANK = "中国银行"
        const val BANK_LAST4 = "9270"
    }
}
