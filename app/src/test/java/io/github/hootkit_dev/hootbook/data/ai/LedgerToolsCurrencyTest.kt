package io.github.hootkit_dev.hootbook.data.ai

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.hootkit_dev.hootbook.data.currency.CurrencyConversionService
import io.github.hootkit_dev.hootbook.data.currency.ExchangeRateProvider
import io.github.hootkit_dev.hootbook.data.currency.ExchangeRateResponseWithMetadata
import io.github.hootkit_dev.hootbook.data.database.HootbookDatabase
import io.github.hootkit_dev.hootbook.data.database.SyncTriggers
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.data.database.entity.CategoryEntity
import io.github.hootkit_dev.hootbook.data.database.entity.ExchangeRateEntity
import io.github.hootkit_dev.hootbook.data.preferences.UserPreferencesRepository
import io.github.hootkit_dev.hootbook.data.repository.AccountBalanceRepository
import io.github.hootkit_dev.hootbook.data.repository.AccountRenamer
import io.github.hootkit_dev.hootbook.data.repository.CategoryRepository
import io.github.hootkit_dev.hootbook.data.repository.SubcategoryRepository
import io.github.hootkit_dev.hootbook.data.repository.SubscriptionRepository
import io.github.hootkit_dev.hootbook.data.repository.TransactionRepository
import io.github.hootkit_dev.hootbook.domain.usecase.AddTransactionUseCase
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The AI tools on accounts that hold several currencies, on a real database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LedgerToolsCurrencyTest {
    private lateinit var db: HootbookDatabase
    private lateinit var tools: LedgerTools
    private lateinit var balances: AccountBalanceRepository
    private val scope = CoroutineScope(SupervisorJob())
    private val start = LocalDateTime.of(2026, 9, 1, 9, 0)

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HootbookDatabase::class.java).addCallback(SyncTriggers.Callback).allowMainThreadQueries().build()
        balances = AccountBalanceRepository(db.accountBalanceDao(), context)
        val transactions = TransactionRepository(db.transactionDao(), balances)
        val offline = object : ExchangeRateProvider {
            override suspend fun fetchExchangeRate(fromCurrency: String, toCurrency: String): BigDecimal? = null
            override suspend fun fetchAllExchangeRates(baseCurrency: String): Map<String, BigDecimal>? = null
            override suspend fun fetchAllExchangeRatesWithMetadata(baseCurrency: String): ExchangeRateResponseWithMetadata? = null
            override fun getProviderName() = "offline"
            override suspend fun getSupportedCurrencies() = emptyList<String>()
            override suspend fun fetchAllCurrencies(): Map<String, String>? = null
        }
        tools = LedgerTools(
            transactions, balances,
            CategoryRepository(db.categoryDao(), context, scope),
            SubcategoryRepository(db.subcategoryDao(), db.categoryDao(), context, scope),
            AddTransactionUseCase(transactions, SubscriptionRepository(db.subscriptionDao()), balances),
            AccountRenamer(db, context),
            CurrencyConversionService(db.exchangeRateDao(), offline, UserPreferencesRepository(context)),
            db
        )
    }

    @After fun tearDown() {
        scope.cancel()
        db.close()
    }

    private suspend fun seed() {
        db.categoryDao().insertCategory(CategoryEntity(name = "Transfer", color = "#000000"))
        db.exchangeRateDao().insertExchangeRate(
            ExchangeRateEntity(
                fromCurrency = "USD", toCurrency = "HKD", rate = BigDecimal("7.8"), provider = "test",
                updatedAt = start, expiresAt = start.plusYears(10), isCustom = true
            )
        )
        fun row(bank: String, last4: String, balance: String, currency: String) = AccountBalanceEntity(
            bankName = bank, accountLast4 = last4, balance = BigDecimal(balance), timestamp = start,
            sourceType = "MANUAL", currency = currency
        )
        // A Hong Kong account holding HKD and USD, and a CNY one
        balances.insertBalance(row("HK Bank", "1111", "10000", "HKD"))
        balances.insertBalance(row("HK Bank", "1111", "100", "USD"))
        balances.insertBalance(row("CN Bank", "2222", "5000", "CNY"))
    }

    private fun call(name: String, json: String) = AiToolCall("c", name, Json.parseToJsonElement(json).jsonObject)

    private suspend fun pocket(last4: String, currency: String) = balances.pocketBalances()
        .first { p -> p.currency == currency && p.accountId == balances.account(if (last4 == "1111") "HK Bank" else "CN Bank", last4)!!.id }
        .balance.stripTrailingZeros().toPlainString()

    @Test fun theModelSeesEveryCurrencyAndTransfersBetweenThem() = runTest {
        seed()
        val context = tools.context()
        val ref = context.accountRefs.entries.first { it.value.accountLast4 == "1111" }.key
        assertTrue(tools.describe(context).contains("also holds USD balance 100"))

        val queue = mutableListOf<LedgerChange>()
        // HKD 780 into the same account's USD
        val result = tools.run(
            call(LedgerTools.ADD, """{"transactions":[{"date":"2026-09-02","amount":780,"currency":"HKD","type":"TRANSFER",
                "merchant":"FX","category":"Transfer","account":"$ref","to_account":"$ref","to_currency":"USD"}]}"""),
            context, queue
        )
        assertEquals("Queued 1 for the user's review.", result.content)
        tools.apply(queue)

        assertEquals("9220", pocket("1111", "HKD"))
        assertEquals("200", pocket("1111", "USD"))
    }

    @Test fun aCurrencyTheAccountLacksMustBeAddedFirst() = runTest {
        seed()
        val context = tools.context()
        val ref = context.accountRefs.entries.first { it.value.accountLast4 == "2222" }.key
        val queue = mutableListOf<LedgerChange>()
        val add = """{"transactions":[{"date":"2026-09-02","amount":50,"currency":"USD","type":"INCOME",
            "merchant":"Refund","category":"Transfer","account":"$ref"}]}"""

        assertTrue(tools.run(call(LedgerTools.ADD, add), context, queue).content.contains("add_currencies"))
        tools.run(call(LedgerTools.UPDATE_ACCOUNT, """{"account":"$ref","add_currencies":["USD"]}"""), context, queue)
        assertEquals("Queued 1 for the user's review.", tools.run(call(LedgerTools.ADD, add), context, queue).content)

        val applied = tools.apply(queue)
        assertEquals("50", pocket("2222", "USD"))
        assertEquals("5000", pocket("2222", "CNY"))

        tools.undo(applied)
        val cn = balances.account("CN Bank", "2222")!!
        assertEquals(listOf("CNY"), db.accountDao().getCurrencies(cn.id).map { it.currency })
        assertTrue(db.transactionDao().getAllTransactions().first().isEmpty())
    }

    @Test fun calibratesOneCurrencyOfAnAccount() = runTest {
        seed()
        val context = tools.context()
        val ref = context.accountRefs.entries.first { it.value.accountLast4 == "1111" }.key
        val queue = mutableListOf<LedgerChange>()
        tools.run(call(LedgerTools.SET_BALANCE, """{"account":"$ref","currency":"USD","balance":120}"""), context, queue)
        tools.apply(queue)

        assertEquals("120", pocket("1111", "USD"))
        assertEquals("10000", pocket("1111", "HKD"))
    }
}
