package io.github.hootkit_dev.hootbook.domain.usecase

import android.content.ContextWrapper
import io.github.hootkit_dev.hootbook.data.database.dao.AccountBalanceDao
import io.github.hootkit_dev.hootbook.data.database.dao.AccountBalanceTransactionInfo
import io.github.hootkit_dev.hootbook.data.database.dao.SubscriptionDao
import io.github.hootkit_dev.hootbook.data.database.dao.TransactionDao
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionType
import io.github.hootkit_dev.hootbook.data.repository.AccountBalanceRepository
import io.github.hootkit_dev.hootbook.data.repository.SubscriptionRepository
import io.github.hootkit_dev.hootbook.data.repository.TransactionRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddTransactionUseCaseTest {

    private val testBank = "Test Bank"
    private val testLast4 = "1234"
    private val testCurrency = "INR"
    private val baseTime = LocalDateTime.of(2025, 1, 15, 10, 30)
    private var transactionDao: FakeTransactionDao? = null

    @Test
    fun `CREDIT transaction adds to existing outstanding balance`() = runTest {
        val balanceDao = FakeAccountBalanceDao()
        val useCase = createUseCase(balanceDao)

        balanceDao.seedBalance(
            AccountBalanceEntity(
                bankName = testBank,
                accountLast4 = testLast4,
                balance = BigDecimal("2000"),
                timestamp = baseTime.minusDays(1),
                isCreditCard = true,
                creditLimit = BigDecimal("50000")
            )
        )

        useCase.execute(
            amount = BigDecimal("500"),
            merchant = "Ice Cream Shop",
            category = "Food",
            type = TransactionType.CREDIT,
            date = baseTime,
            bankName = testBank,
            accountLast4 = testLast4,
            currency = testCurrency
        )

        val latest = balanceDao.getLatestBalance(testBank, testLast4)
        assertNotNull(latest)
        assertEquals(BigDecimal("2500"), latest!!.balance)
        assertEquals(true, latest.isCreditCard)
        assertEquals(BigDecimal("50000"), latest.creditLimit)
    }

    @Test
    fun `CREDIT transaction creates first balance entry`() = runTest {
        val balanceDao = FakeAccountBalanceDao()
        val useCase = createUseCase(balanceDao)

        useCase.execute(
            amount = BigDecimal("750"),
            merchant = "Coffee Shop",
            category = "Food",
            type = TransactionType.CREDIT,
            date = baseTime,
            bankName = testBank,
            accountLast4 = testLast4,
            currency = testCurrency
        )

        val latest = balanceDao.getLatestBalance(testBank, testLast4)
        assertNotNull(latest)
        assertEquals(BigDecimal("750"), latest!!.balance)
    }

    @Test
    fun `INCOME transaction adds to balance`() = runTest {
        val balanceDao = FakeAccountBalanceDao()
        val useCase = createUseCase(balanceDao)

        balanceDao.seedBalance(
            AccountBalanceEntity(
                bankName = testBank,
                accountLast4 = testLast4,
                balance = BigDecimal("10000"),
                timestamp = baseTime.minusDays(1)
            )
        )

        useCase.execute(
            amount = BigDecimal("5000"),
            merchant = "Salary",
            category = "Income",
            type = TransactionType.INCOME,
            date = baseTime,
            bankName = testBank,
            accountLast4 = testLast4,
            currency = testCurrency
        )

        val latest = balanceDao.getLatestBalance(testBank, testLast4)
        assertNotNull(latest)
        assertEquals(BigDecimal("15000"), latest!!.balance)
    }

    @Test
    fun `EXPENSE transaction subtracts from balance`() = runTest {
        val balanceDao = FakeAccountBalanceDao()
        val useCase = createUseCase(balanceDao)

        balanceDao.seedBalance(
            AccountBalanceEntity(
                bankName = testBank,
                accountLast4 = testLast4,
                balance = BigDecimal("10000"),
                timestamp = baseTime.minusDays(1)
            )
        )

        useCase.execute(
            amount = BigDecimal("3000"),
            merchant = "Rent",
            category = "Housing",
            type = TransactionType.EXPENSE,
            date = baseTime,
            bankName = testBank,
            accountLast4 = testLast4,
            currency = testCurrency
        )

        val latest = balanceDao.getLatestBalance(testBank, testLast4)
        assertNotNull(latest)
        assertEquals(BigDecimal("7000"), latest!!.balance)
    }

    @Test
    fun `INVESTMENT transaction subtracts from balance`() = runTest {
        val balanceDao = FakeAccountBalanceDao()
        val useCase = createUseCase(balanceDao)

        balanceDao.seedBalance(
            AccountBalanceEntity(
                bankName = testBank,
                accountLast4 = testLast4,
                balance = BigDecimal("50000"),
                timestamp = baseTime.minusDays(1)
            )
        )

        useCase.execute(
            amount = BigDecimal("10000"),
            merchant = "Mutual Fund SIP",
            category = "Investment",
            type = TransactionType.INVESTMENT,
            date = baseTime,
            bankName = testBank,
            accountLast4 = testLast4,
            currency = testCurrency
        )

        val latest = balanceDao.getLatestBalance(testBank, testLast4)
        assertNotNull(latest)
        assertEquals(BigDecimal("40000"), latest!!.balance)
    }

    @Test
    fun `CREDIT transaction preserves isCreditCard and creditLimit`() = runTest {
        val balanceDao = FakeAccountBalanceDao()
        val useCase = createUseCase(balanceDao)

        balanceDao.seedBalance(
            AccountBalanceEntity(
                bankName = testBank,
                accountLast4 = testLast4,
                balance = BigDecimal("5000"),
                timestamp = baseTime.minusDays(1),
                isCreditCard = true,
                creditLimit = BigDecimal("100000")
            )
        )

        useCase.execute(
            amount = BigDecimal("1500"),
            merchant = "Electronics",
            category = "Shopping",
            type = TransactionType.CREDIT,
            date = baseTime,
            bankName = testBank,
            accountLast4 = testLast4,
            currency = testCurrency
        )

        val latest = balanceDao.getLatestBalance(testBank, testLast4)
        assertEquals(BigDecimal("6500"), latest!!.balance)
        assertEquals(true, latest.isCreditCard)
        assertEquals(BigDecimal("100000"), latest.creditLimit)
    }

    @Test
    fun `CREDIT multiple transactions accumulate correctly`() = runTest {
        val balanceDao = FakeAccountBalanceDao()
        val useCase = createUseCase(balanceDao)

        balanceDao.seedBalance(
            AccountBalanceEntity(
                bankName = testBank,
                accountLast4 = testLast4,
                balance = BigDecimal.ZERO,
                timestamp = baseTime.minusDays(2),
                isCreditCard = true,
                creditLimit = BigDecimal("50000")
            )
        )

        useCase.execute(
            amount = BigDecimal("200"),
            merchant = "Lunch",
            category = "Food",
            type = TransactionType.CREDIT,
            date = baseTime.minusDays(1),
            bankName = testBank,
            accountLast4 = testLast4,
            currency = testCurrency
        )

        useCase.execute(
            amount = BigDecimal("350"),
            merchant = "Dinner",
            category = "Food",
            type = TransactionType.CREDIT,
            date = baseTime,
            bankName = testBank,
            accountLast4 = testLast4,
            currency = testCurrency
        )

        val latest = balanceDao.getLatestBalance(testBank, testLast4)
        assertEquals(BigDecimal("550"), latest!!.balance)
    }

    @Test
    fun `deleting CREDIT transaction reverses the balance`() = runTest {
        val balanceDao = FakeAccountBalanceDao()
        val (useCase, transactionRepo) = createUseCaseWithRepo(balanceDao)

        balanceDao.seedBalance(
            AccountBalanceEntity(
                bankName = testBank,
                accountLast4 = testLast4,
                balance = BigDecimal("25000"),
                timestamp = baseTime.minusDays(1),
                isCreditCard = true,
                creditLimit = BigDecimal("50000")
            )
        )

        useCase.execute(
            amount = BigDecimal("5000"),
            merchant = "Ice Cream",
            category = "Food",
            type = TransactionType.CREDIT,
            date = baseTime,
            bankName = testBank,
            accountLast4 = testLast4,
            currency = testCurrency
        )

        var latest = balanceDao.getLatestBalance(testBank, testLast4)
        assertEquals(BigDecimal("30000"), latest!!.balance)

        val transaction = transactionDao!!.insertedTransactions.last()
        transactionRepo.deleteTransaction(transaction)

        latest = balanceDao.getLatestBalance(testBank, testLast4)
        assertEquals(BigDecimal("25000"), latest!!.balance)
    }

    @Test
    fun `deleting EXPENSE transaction reverses the balance`() = runTest {
        val balanceDao = FakeAccountBalanceDao()
        val (useCase, transactionRepo) = createUseCaseWithRepo(balanceDao)

        balanceDao.seedBalance(
            AccountBalanceEntity(
                bankName = testBank,
                accountLast4 = testLast4,
                balance = BigDecimal("25000"),
                timestamp = baseTime.minusDays(1)
            )
        )

        useCase.execute(
            amount = BigDecimal("5000"),
            merchant = "Ice Cream",
            category = "Food",
            type = TransactionType.EXPENSE,
            date = baseTime,
            bankName = testBank,
            accountLast4 = testLast4,
            currency = testCurrency
        )

        var latest = balanceDao.getLatestBalance(testBank, testLast4)
        assertEquals(BigDecimal("20000"), latest!!.balance)

        val transaction = transactionDao!!.insertedTransactions.last()
        transactionRepo.deleteTransaction(transaction)

        latest = balanceDao.getLatestBalance(testBank, testLast4)
        assertEquals(BigDecimal("25000"), latest!!.balance)
    }

    @Test
    fun `undoing CREDIT delete re-applies the balance`() = runTest {
        val balanceDao = FakeAccountBalanceDao()
        val (useCase, transactionRepo) = createUseCaseWithRepo(balanceDao)

        balanceDao.seedBalance(
            AccountBalanceEntity(
                bankName = testBank,
                accountLast4 = testLast4,
                balance = BigDecimal("25000"),
                timestamp = baseTime.minusDays(1),
                isCreditCard = true,
                creditLimit = BigDecimal("50000")
            )
        )

        useCase.execute(
            amount = BigDecimal("5000"),
            merchant = "Ice Cream",
            category = "Food",
            type = TransactionType.CREDIT,
            date = baseTime,
            bankName = testBank,
            accountLast4 = testLast4,
            currency = testCurrency
        )

        val transaction = transactionDao!!.insertedTransactions.last()
        transactionRepo.deleteTransaction(transaction)

        var latest = balanceDao.getLatestBalance(testBank, testLast4)
        assertEquals(BigDecimal("25000"), latest!!.balance)

        transactionRepo.undoDeleteTransaction(
            transaction.copy(isDeleted = true)
        )

        latest = balanceDao.getLatestBalance(testBank, testLast4)
        assertEquals(BigDecimal("30000"), latest!!.balance)
    }

    @Test
    fun `overdraft survives delete and repeated undo without creating money`() = runTest {
        val dao = FakeAccountBalanceDao()
        val (useCase, repo) = createUseCaseWithRepo(dao)
        dao.seedBalance(AccountBalanceEntity(bankName = testBank, accountLast4 = testLast4,
            balance = BigDecimal("100"), timestamp = baseTime.minusDays(1), sourceType = "MANUAL"))
        useCase.execute(BigDecimal("840"), "Subscription payment", "Other", TransactionType.EXPENSE,
            baseTime, bankName = testBank, accountLast4 = testLast4, currency = "CNY")
        val txn = transactionDao!!.insertedTransactions.last()
        assertEquals(BigDecimal("-740"), dao.getLatestBalance(testBank, testLast4)!!.balance)
        // The repository must use the persisted amount, not an obsolete UI copy.
        repo.deleteTransaction(txn.copy(amount = BigDecimal("9999")))
        repo.deleteTransaction(txn)
        assertEquals(BigDecimal("100"), dao.getLatestBalance(testBank, testLast4)!!.balance)
        repo.undoDeleteTransaction(txn)
        repo.undoDeleteTransaction(txn)
        assertEquals(BigDecimal("-740"), dao.getLatestBalance(testBank, testLast4)!!.balance)
    }

    @Test
    fun `batch deletion recomputes both sides of a calibration and undo restores them`() = runTest {
        val dao = FakeAccountBalanceDao()
        val (useCase, repo) = createUseCaseWithRepo(dao)
        dao.seedBalance(AccountBalanceEntity(bankName = testBank, accountLast4 = testLast4,
            balance = BigDecimal("100"), timestamp = baseTime.minusDays(1), sourceType = "MANUAL"))
        useCase.execute(BigDecimal("840"), "Before", "Other", TransactionType.EXPENSE,
            baseTime, bankName = testBank, accountLast4 = testLast4, currency = "CNY")
        val first = transactionDao!!.insertedTransactions.last()
        dao.seedBalance(AccountBalanceEntity(bankName = testBank, accountLast4 = testLast4,
            balance = BigDecimal("5000"), timestamp = baseTime.plusDays(1), sourceType = "BALANCE_CALIBRATION"))
        useCase.execute(BigDecimal("420"), "After", "Other", TransactionType.EXPENSE,
            baseTime.plusDays(2), bankName = testBank, accountLast4 = testLast4, currency = "CNY")
        val second = transactionDao!!.insertedTransactions.last()
        repo.deleteTransaction(first)
        assertEquals(BigDecimal("4580"), dao.getLatestBalance(testBank, testLast4)!!.balance)
        repo.undoDeleteTransaction(first)
        repo.deleteTransactions(listOf(second, first, second))
        assertEquals(BigDecimal("5000"), dao.getLatestBalance(testBank, testLast4)!!.balance)
        repo.undoDeleteTransactions(listOf(second, first))
        assertEquals(BigDecimal("4580"), dao.getLatestBalance(testBank, testLast4)!!.balance)
        assertEquals(BigDecimal("-740"), dao.getBalanceByTransactionId(first.id)!!.balance)
    }

    @Test
    fun `transfer delete and undo recalculate both accounts and later expenses`() = runTest {
        val dao = FakeAccountBalanceDao()
        val (useCase, repo) = createUseCaseWithRepo(dao)
        for ((last4, amount) in listOf(testLast4 to "100", "5678" to "50")) {
            dao.seedBalance(AccountBalanceEntity(bankName = testBank, accountLast4 = last4,
                balance = BigDecimal(amount), timestamp = baseTime.minusDays(1), sourceType = "MANUAL"))
        }
        useCase.execute(BigDecimal("200"), "Transfer", "Transfer", TransactionType.TRANSFER,
            baseTime, bankName = testBank, accountLast4 = testLast4,
            targetAccountBankName = testBank, targetAccountLast4 = "5678", currency = "CNY")
        val transfer = transactionDao!!.insertedTransactions.last()
        useCase.execute(BigDecimal("30"), "Lunch", "Food", TransactionType.EXPENSE,
            baseTime.plusDays(1), bankName = testBank, accountLast4 = "5678", currency = "CNY")
        repo.deleteTransaction(transfer)
        assertEquals(BigDecimal("100"), dao.getLatestBalance(testBank, testLast4)!!.balance)
        assertEquals(BigDecimal("20"), dao.getLatestBalance(testBank, "5678")!!.balance)
        repo.undoDeleteTransaction(transfer)
        assertEquals(BigDecimal("-100"), dao.getLatestBalance(testBank, testLast4)!!.balance)
        assertEquals(BigDecimal("220"), dao.getLatestBalance(testBank, "5678")!!.balance)
    }

    @Test
    fun `transaction without a linked balance never credits an account on deletion`() = runTest {
        val dao = FakeAccountBalanceDao()
        val (_, repo) = createUseCaseWithRepo(dao)
        dao.seedBalance(AccountBalanceEntity(bankName = testBank, accountLast4 = testLast4,
            balance = BigDecimal("100"), timestamp = baseTime.minusDays(1)))
        val txn = TransactionEntity(id = 99, amount = BigDecimal("840"), merchantName = "Imported",
            category = "Other", transactionType = TransactionType.EXPENSE, dateTime = baseTime,
            bankName = testBank, accountNumber = testLast4, transactionHash = "unlinked")
        dao.transactions.add(txn)
        repo.deleteTransaction(txn)
        repo.undoDeleteTransaction(txn)
        assertEquals(BigDecimal("100"), dao.getLatestBalance(testBank, testLast4)!!.balance)
    }

    @Test
    fun `first transaction records opening base and hard delete is idempotent`() = runTest {
        val dao = FakeAccountBalanceDao()
        val (useCase, repo) = createUseCaseWithRepo(dao)
        useCase.execute(BigDecimal("840"), "First", "Other", TransactionType.EXPENSE,
            baseTime, bankName = testBank, accountLast4 = testLast4, currency = "CNY")
        val txn = transactionDao!!.insertedTransactions.last()
        assertEquals(BigDecimal("-840"), dao.getLatestBalance(testBank, testLast4)!!.balance)
        repo.deleteTransaction(txn, hardDelete = true)
        repo.deleteTransaction(txn, hardDelete = true)
        repo.undoDeleteTransaction(txn)
        assertEquals(BigDecimal.ZERO, dao.getLatestBalance(testBank, testLast4)!!.balance)
        assertTrue(dao.transactions.isEmpty())
    }

    private fun createUseCase(balanceDao: FakeAccountBalanceDao): AddTransactionUseCase {
        return createUseCaseWithRepo(balanceDao).first
    }

    private fun createUseCaseWithRepo(
        balanceDao: FakeAccountBalanceDao
    ): Pair<AddTransactionUseCase, TransactionRepository> {
        val context = ContextWrapper(null)
        val accountBalanceRepo = AccountBalanceRepository(balanceDao, context)
        val dao = FakeTransactionDao()
        transactionDao = dao
        balanceDao.transactions = dao.insertedTransactions
        val transactionRepo = TransactionRepository(dao, accountBalanceRepo)
        val subscriptionDao = FakeSubscriptionDao()
        val subscriptionRepo = SubscriptionRepository(subscriptionDao)
        return Pair(
            AddTransactionUseCase(transactionRepo, subscriptionRepo, accountBalanceRepo),
            transactionRepo
        )
    }

    private class FakeTransactionDao : TransactionDao {
        var insertedTransactions = mutableListOf<TransactionEntity>()
        private var nextId = 1L

        override suspend fun insertTransaction(transaction: TransactionEntity): Long {
            val id = nextId++
            insertedTransactions.add(transaction.copy(id = id))
            return id
        }

        override suspend fun getTransactionByHash(transactionHash: String): TransactionEntity? = null

        override fun getAllTransactions(): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override fun getTransactionCount(): Flow<Int> = flowOf(0)
        override suspend fun countVisibleTransactions(): Int = 0
        override fun getRecentVisibleTransactions(limit: Int): Flow<List<TransactionEntity>> =
            flowOf(emptyList())
        override fun getVisibleTransactionsBetweenDates(
            startDate: LocalDateTime, endDate: LocalDateTime
        ): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override suspend fun getTransactionById(transactionId: Long): TransactionEntity? = null
        override fun getTransactionsBetweenDates(
            startDate: LocalDateTime, endDate: LocalDateTime
        ): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override fun getTransactionsFiltered(
            startDate: LocalDateTime, endDate: LocalDateTime,
            currency: String, transactionType: TransactionType?
        ): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override fun getTransactionsByType(type: TransactionType): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override fun getTransactionsByCategory(category: String): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override fun searchTransactions(searchQuery: String): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override suspend fun searchTransactionsList(searchQuery: String): List<TransactionEntity> = emptyList()
        override fun getAllCategories(): Flow<List<String>> = flowOf(emptyList())
        override suspend fun getTopCategoriesByUsage(limit: Int): List<String> = emptyList()
        override fun getAllMerchants(): Flow<List<String>> = flowOf(emptyList())
        override suspend fun getTotalAmountByTypeAndPeriod(
            type: TransactionType, startDate: LocalDateTime, endDate: LocalDateTime
        ): Double? = null
        override suspend fun insertTransactions(transactions: List<TransactionEntity>) = Unit
        override suspend fun updateTransaction(transaction: TransactionEntity) = Unit
        override suspend fun deleteTransaction(transaction: TransactionEntity) = Unit
        override suspend fun deleteTransactionById(transactionId: Long) = Unit
        override suspend fun deleteAllTransactions() = Unit
        override suspend fun updateCategoryForMerchant(merchantName: String, newCategory: String) = Unit
        override suspend fun updateCategoryAndSubcategoryForMerchantContains(
            merchantName: String, newCategory: String, newSubcategory: String?
        ) = Unit
        override suspend fun getTransactionCountForMerchant(merchantName: String, excludeId: Long): Int = 0
        override suspend fun getTransactionsByMerchantContains(
            merchantName: String, excludeId: Long
        ): List<TransactionEntity> = emptyList()
        override fun getAllCurrencies(): Flow<List<String>> = flowOf(emptyList())
        override fun getCurrenciesForPeriod(
            startDate: LocalDateTime, endDate: LocalDateTime
        ): Flow<List<String>> = flowOf(emptyList())
        override suspend fun softDeleteTransaction(transactionId: Long) = Unit
        override suspend fun softDeleteByHash(transactionHash: String) = Unit
        override suspend fun softDeleteTransactions(transactionIds: List<Long>) = Unit
        override suspend fun deleteTransactionsByIds(transactionIds: List<Long>) = Unit
        override suspend fun getTransactionsByReferenceAndAmount(
            reference: String,
            amount: java.math.BigDecimal,
            accountLast4: String?,
            startDate: LocalDateTime,
            endDate: LocalDateTime
        ): List<TransactionEntity> = emptyList()
        override suspend fun getTransactionsBetweenDatesList(
            startDate: LocalDateTime, endDate: LocalDateTime
        ): List<TransactionEntity> = emptyList()
        override fun getTransactionsByAccount(
            bankName: String, accountLast4: String
        ): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override fun getTransactionsByAccountAndDateRange(
            bankName: String, accountLast4: String,
            startDate: LocalDateTime, endDate: LocalDateTime
        ): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override suspend fun updateAccountForTransactions(
            oldBankName: String, oldAccountNumber: String,
            newBankName: String, newAccountNumber: String
        ) = Unit
        override suspend fun retargetTransfers(oldAccountId: Long, newAccountId: Long, newLast4: String) = Unit
        override suspend fun updateTransactionsCategory(
            oldCategory: String, newCategory: String, newSubcategory: String?
        ) = Unit
        override suspend fun getTransactionCountByCategory(category: String): Int = 0
        override suspend fun findPotentialDuplicates(
            startDate: LocalDateTime, endDate: LocalDateTime
        ): List<TransactionEntity> = emptyList()
        override suspend fun getTransactionsUpdatedBetween(
            updatedAfter: LocalDateTime, updatedBefore: LocalDateTime, currency: String
        ): List<TransactionEntity> = emptyList()
        override suspend fun getTransactionsBetweenDatesByCurrency(
            startDate: LocalDateTime, endDate: LocalDateTime, currency: String
        ): List<TransactionEntity> = emptyList()
    }

    private class FakeSubscriptionDao : SubscriptionDao {
        override suspend fun insertSubscription(subscription: io.github.hootkit_dev.hootbook.data.database.entity.SubscriptionEntity): Long = 1
        override fun getAllSubscriptions(): Flow<List<io.github.hootkit_dev.hootbook.data.database.entity.SubscriptionEntity>> = flowOf(emptyList())
        override fun getSubscriptionsByState(state: io.github.hootkit_dev.hootbook.data.database.entity.SubscriptionState): Flow<List<io.github.hootkit_dev.hootbook.data.database.entity.SubscriptionEntity>> = flowOf(emptyList())
        override fun getActiveSubscriptions(): Flow<List<io.github.hootkit_dev.hootbook.data.database.entity.SubscriptionEntity>> = flowOf(emptyList())
        override fun getUpcomingSubscriptions(date: java.time.LocalDate): Flow<List<io.github.hootkit_dev.hootbook.data.database.entity.SubscriptionEntity>> = flowOf(emptyList())
        override suspend fun getActiveSubscriptionByMerchant(merchantName: String): io.github.hootkit_dev.hootbook.data.database.entity.SubscriptionEntity? = null
        override suspend fun getHiddenSubscriptionByMerchant(merchantName: String): io.github.hootkit_dev.hootbook.data.database.entity.SubscriptionEntity? = null
        override suspend fun getSubscriptionByMerchantAmountAndDate(merchantName: String, amount: BigDecimal, paymentDate: java.time.LocalDate): io.github.hootkit_dev.hootbook.data.database.entity.SubscriptionEntity? = null
        override suspend fun getSubscriptionByMerchantAndAmount(merchantName: String, amount: BigDecimal): io.github.hootkit_dev.hootbook.data.database.entity.SubscriptionEntity? = null
        override suspend fun getSubscriptionById(id: Long): io.github.hootkit_dev.hootbook.data.database.entity.SubscriptionEntity? = null
        override suspend fun updateSubscription(subscription: io.github.hootkit_dev.hootbook.data.database.entity.SubscriptionEntity) = Unit
        override suspend fun updateSubscriptionState(id: Long, state: io.github.hootkit_dev.hootbook.data.database.entity.SubscriptionState) = Unit
        override suspend fun updateNextPaymentDate(id: Long, nextPaymentDate: java.time.LocalDate) = Unit
        override suspend fun updatePaymentStatus(id: Long, nextPaymentDate: java.time.LocalDate, lastPaidDate: java.time.LocalDate?) = Unit
        override suspend fun deleteSubscription(subscription: io.github.hootkit_dev.hootbook.data.database.entity.SubscriptionEntity) = Unit
        override suspend fun deleteSubscriptionById(id: Long) = Unit
        override suspend fun getSubscriptionsByStateList(state: io.github.hootkit_dev.hootbook.data.database.entity.SubscriptionState): List<io.github.hootkit_dev.hootbook.data.database.entity.SubscriptionEntity> = emptyList()
        override suspend fun deleteAllSubscriptions() = Unit
        override suspend fun renameBank(oldBankName: String, newBankName: String): Int = 0
    }

    private class FakeAccountBalanceDao : AccountBalanceDao() {
        var transactions = mutableListOf<TransactionEntity>()
        override suspend fun getTransactionDeletedState(id: Long): Boolean? = transactions.find { it.id == id }?.isDeleted
        override suspend fun setTransactionDeletedState(id: Long, deleted: Boolean) {
            val index = transactions.indexOfFirst { it.id == id }
            if (index >= 0) transactions[index] = transactions[index].copy(isDeleted = deleted)
        }
        override suspend fun hardDeleteLedgerTransaction(id: Long) { transactions.removeAll { it.id == id } }
        override suspend fun getBalancesForTransaction(id: Long): List<AccountBalanceEntity> =
            balances.values.flatten().filter { it.transactionId == id }

        private val balances = mutableMapOf<Pair<String, String>, MutableList<AccountBalanceEntity>>()
        private var nextId = 1L

        fun seedBalance(entity: AccountBalanceEntity) {
            val key = Pair(entity.bankName, entity.accountLast4)
            balances.getOrPut(key) { mutableListOf() }.add(
                entity.copy(id = nextId++)
            )
        }

        override suspend fun insertBalance(balance: AccountBalanceEntity): Long {
            val key = Pair(balance.bankName, balance.accountLast4)
            val id = nextId++
            balances.getOrPut(key) { mutableListOf() }.add(balance.copy(id = id))
            return id
        }

        override suspend fun getLatestBalance(bankName: String, accountLast4: String): AccountBalanceEntity? {
            val key = Pair(bankName, accountLast4)
            return balances[key]?.maxByOrNull { it.timestamp }
        }

        override suspend fun getAccountLast4sEndingWith(bankName: String, suffix: String): List<String> = emptyList()
        override suspend fun getLatestBalanceOnOrBefore(
            bankName: String,
            accountLast4: String,
            currency: String,
            timestamp: LocalDateTime
        ): AccountBalanceEntity? {
            val key = Pair(bankName, accountLast4)
            return balances[key]
                ?.filter { !it.timestamp.isAfter(timestamp) }
                ?.maxByOrNull { it.timestamp }
        }
        override suspend fun getBalancesAfterWithTransactions(
            bankName: String,
            accountLast4: String,
            currency: String,
            timestamp: LocalDateTime
        ): List<AccountBalanceTransactionInfo> = balances[bankName to accountLast4].orEmpty()
            .filter { it.timestamp > timestamp }.sortedBy { it.timestamp }.map { row ->
                val txn = transactions.find { it.id == row.transactionId }
                val type = if (txn?.transactionType == TransactionType.TRANSFER) {
                    if (txn.bankName == bankName && txn.accountNumber == accountLast4) TransactionType.EXPENSE else TransactionType.INCOME
                } else txn?.transactionType
                AccountBalanceTransactionInfo(row.id, row.balance, row.sourceType, row.isCreditCard,
                    row.transactionId, txn?.amount, type?.name, txn?.balanceAfter, txn?.isDeleted)
            }
        override fun getLatestBalanceFlow(bankName: String, accountLast4: String): Flow<AccountBalanceEntity?> = flowOf(null)
        override fun getAllLatestBalances(): Flow<List<AccountBalanceEntity>> = flowOf(emptyList())
        override fun getAllBalances(): Flow<List<AccountBalanceEntity>> = flowOf(emptyList())
        override suspend fun deleteAllBalances() = Unit
        override fun getCurrentMonthLatestBalances(): Flow<List<AccountBalanceEntity>> = flowOf(emptyList())
        override fun getTotalBalance(): Flow<BigDecimal?> = flowOf(BigDecimal.ZERO)
        override fun getBalanceHistory(bankName: String, accountLast4: String, startDate: LocalDateTime, endDate: LocalDateTime): Flow<List<AccountBalanceEntity>> = flowOf(emptyList())
        override fun getAccountCount(): Flow<Int> = flowOf(0)
        override suspend fun deleteOldBalances(beforeDate: LocalDateTime): Int = 0
        override suspend fun updateBalance(balance: AccountBalanceEntity) {
            for (list in balances.values) {
                val index = list.indexOfFirst { it.id == balance.id }
                if (index != -1) {
                    list[index] = balance
                    break
                }
            }
        }
        override suspend fun deleteBalance(balance: AccountBalanceEntity) = Unit
        override suspend fun getBalanceHistoryForAccount(bankName: String, accountLast4: String): List<AccountBalanceEntity> = balances[bankName to accountLast4].orEmpty()
        override suspend fun deleteBalanceById(id: Long) = Unit
        override suspend fun updateBalanceById(id: Long, newBalance: BigDecimal) {
            for (list in balances.values) {
                val index = list.indexOfFirst { it.id == id }
                if (index != -1) {
                    list[index] = list[index].copy(balance = newBalance)
                    break
                }
            }
        }
        override suspend fun getBalanceCountForAccount(bankName: String, accountLast4: String): Int = balances[Pair(bankName, accountLast4)]?.size ?: 0
        override suspend fun deleteAccount(bankName: String, accountLast4: String): Int {
            val key = Pair(bankName, accountLast4)
            val size = balances[key]?.size ?: 0
            balances.remove(key)
            return size
        }
        override suspend fun updateAccountBankName(oldBankName: String, accountLast4: String, newBankName: String): Int = 0
        override suspend fun getBalanceByTransactionId(transactionId: Long): AccountBalanceEntity? {
            for (list in balances.values) {
                list.find { it.transactionId == transactionId }?.let { return it }
            }
            return null
        }

        override suspend fun getBalanceById(id: Long): AccountBalanceEntity? {
            for (list in balances.values) {
                list.find { it.id == id }?.let { return it }
            }
            return null
        }



        // Account rows (tables these fakes don't model)
        override suspend fun insertBalanceRow(balance: AccountBalanceEntity): Long = insertBalance(balance)
        override suspend fun accountFor(name: String, last4: String): io.github.hootkit_dev.hootbook.data.database.entity.AccountEntity? = null
        override suspend fun insertAccountRow(account: io.github.hootkit_dev.hootbook.data.database.entity.AccountEntity): Long = 0
        override suspend fun insertCurrencyRow(currency: io.github.hootkit_dev.hootbook.data.database.entity.AccountCurrencyEntity) = Unit
        override suspend fun currenciesOf(accountId: Long): List<String> = emptyList()
        override fun observePocketBalances(): Flow<List<io.github.hootkit_dev.hootbook.data.database.dao.PocketBalance>> = flowOf(emptyList())
        override suspend fun getPocketBalances(): List<io.github.hootkit_dev.hootbook.data.database.dao.PocketBalance> = emptyList()
        override suspend fun updateAccountRow(account: io.github.hootkit_dev.hootbook.data.database.entity.AccountEntity) = Unit
        override suspend fun deleteAllBalanceRows() = Unit
        override suspend fun deleteAllAccountRows() = Unit
        override suspend fun linkTransactionAccounts() = Unit
        override suspend fun linkTransferTargets() = Unit
        override suspend fun linkTransferCurrencies() = Unit
        override fun observeAccountRows(): Flow<List<io.github.hootkit_dev.hootbook.data.database.entity.AccountEntity>> = flowOf(emptyList())
        override suspend fun deleteCurrencyRows(accountId: Long, currency: String) = Unit
        override suspend fun deleteCurrencyRow(accountId: Long, currency: String) = Unit
        override suspend fun deleteBalanceRowsOf(bankName: String, accountLast4: String): Int = 0
        override suspend fun deleteAccountRow(bankName: String, accountLast4: String) = Unit
        override suspend fun renameBalanceRows(oldBankName: String, accountLast4: String, newBankName: String): Int = 0
        override suspend fun renameAccountRow(oldBankName: String, accountLast4: String, newBankName: String) = Unit
        // Without account rows there is one currency, so every query sees the whole account
        override suspend fun recalculateBalancesAfter(
            bankName: String, accountLast4: String, timestamp: LocalDateTime, startingBalance: BigDecimal, currency: String?
        ) = super.recalculateBalancesAfter(bankName, accountLast4, timestamp, startingBalance, currency ?: "INR")

        override suspend fun getAccountByLast4(accountLast4: String): AccountBalanceEntity? = null

        override suspend fun getEarliestBalance(bankName: String, accountLast4: String, currency: String): AccountBalanceEntity? {
            return balances[Pair(bankName, accountLast4)]?.minByOrNull { it.timestamp }
        }
    }
}
