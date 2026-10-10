package io.github.hootkit_dev.hootbook.data.backup

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

/** Backups made before accounts had rows of their own still restore into accounts and currencies. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupAccountsTest {
    private lateinit var db: HootbookDatabase
    private val start = LocalDateTime.of(2026, 9, 1, 9, 0)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HootbookDatabase::class.java)
            .addCallback(SyncTriggers.Callback).allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    private fun row(id: Long, bank: String, currency: String, day: Long, source: String?, txId: Long? = null) =
        AccountBalanceEntity(
            id = id, bankName = bank, accountLast4 = "1234", balance = BigDecimal.TEN, timestamp = start.plusDays(day),
            sourceType = source, currency = currency, transactionId = txId
        )

    @Test fun strayCurrenciesJoinTheAccountsOwn() {
        val rows = inAccountCurrencies(
            listOf(
                row(1, "Broker", "USD", 0, "MANUAL"),
                // A transfer in HKD used to stamp its currency on the receiving account
                row(2, "Broker", "HKD", 1, "TRANSACTION"),
                row(3, "Bank", "HKD", 0, "TRANSACTION"),
                row(4, "Bank", "CNY", 1, "TRANSACTION")
            )
        )
        assertEquals(listOf("USD", "USD", "CNY", "CNY"), rows.map { it.currency })
    }

    @Test fun transactionsFromAnOldBackupFindTheirAccounts() = runTest {
        val tx = TransactionEntity(
            id = 7, amount = BigDecimal("100"), merchantName = "m", category = "c",
            transactionType = TransactionType.TRANSFER, dateTime = start.plusDays(1), bankName = "Bank",
            accountNumber = "1234", toAccount = "1234", transactionHash = "h", currency = "HKD"
        )
        db.transactionDao().insertTransaction(tx)
        inAccountCurrencies(
            listOf(
                row(1, "Bank", "HKD", 0, "MANUAL"),
                row(2, "Broker", "USD", 0, "MANUAL"),
                row(3, "Bank", "HKD", 1, "TRANSACTION", txId = 7),
                row(4, "Broker", "HKD", 1, "TRANSACTION", txId = 7)
            )
        ).forEach { db.accountBalanceDao().insertBalance(it) }

        db.accountBalanceDao().linkTransactionsToAccounts()

        val linked = db.transactionDao().getTransactionById(7)!!
        val accounts = db.accountDao().getAccounts().associateBy { it.name }
        assertEquals(accounts.getValue("Bank").id, linked.accountId)
        // Both accounts end in 1234: the target comes from the transfer's own balance rows
        assertEquals(accounts.getValue("Broker").id, linked.toAccountId)
        assertEquals("USD", linked.toCurrency)
        assertEquals(listOf("USD"), db.accountDao().getCurrencies(accounts.getValue("Broker").id).map { it.currency })
    }
}
