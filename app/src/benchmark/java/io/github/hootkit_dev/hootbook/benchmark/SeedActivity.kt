package io.github.hootkit_dev.hootbook.benchmark

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.core.content.edit
import io.github.hootkit_dev.hootbook.data.database.dao.AccountBalanceDao
import io.github.hootkit_dev.hootbook.data.database.dao.CategoryDao
import io.github.hootkit_dev.hootbook.data.database.dao.TransactionDao
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionType
import io.github.hootkit_dev.hootbook.data.preferences.UserPreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Seeds the benchmark build with a fixed data set and marks onboarding as done, so
 * Macrobenchmark lands on a populated Home screen.
 *
 * adb shell am start -W -n io.github.hootkit_dev.hootbook.benchmark/io.github.hootkit_dev.hootbook.benchmark.SeedActivity \
 *     --ei transactions 3000 --ei thisMonth 400
 *
 * When done it writes files/benchmark-seeded in external app storage, which the
 * benchmark polls for. Running it again is a no-op.
 */
@AndroidEntryPoint
class SeedActivity : ComponentActivity() {
    @Inject lateinit var transactionDao: TransactionDao
    @Inject lateinit var accountBalanceDao: AccountBalanceDao
    @Inject lateinit var categoryDao: CategoryDao
    @Inject lateinit var userPreferences: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val total = intent.getIntExtra(EXTRA_TRANSACTIONS, 3000)
        val thisMonth = intent.getIntExtra(EXTRA_THIS_MONTH, 400)
        runBlocking(Dispatchers.IO) { seed(total, thisMonth) }
        // The benchmark polls for this file (readable via adb) before launching the app.
        java.io.File(getExternalFilesDir(null), "benchmark-seeded").writeText("ok")
        finish()
    }

    private suspend fun seed(total: Int, thisMonth: Int) {
        val marker = getSharedPreferences(MARKER_PREFS, Context.MODE_PRIVATE)
        if (marker.getBoolean(MARKER_KEY, false)) {
            Log.i(TAG, "Already seeded")
            return
        }
        val started = System.nanoTime()

        // Default categories are inserted asynchronously when the database is first created.
        var waits = 0
        while (categoryDao.getCategoryCount() == 0 && waits++ < 50) delay(100)
        val categories = categoryDao.getAllCategories().first()
        val expenseCategories = categories.filter { !it.isIncome }.map { it.name }.ifEmpty { listOf("Others") }
        val incomeCategories = categories.filter { it.isIncome }.map { it.name }.ifEmpty { listOf("Salary") }

        val now = LocalDateTime.now()
        ACCOUNTS.forEachIndexed { i, account ->
            accountBalanceDao.insertBalance(
                AccountBalanceEntity(
                    bankName = account.first,
                    accountLast4 = account.second,
                    balance = BigDecimal(50_000 + i * 12_345),
                    timestamp = now.minusDays(400),
                    sourceType = "MANUAL",
                    currency = CURRENCY,
                    isCreditCard = account.third,
                )
            )
        }

        // A fixed seed keeps runs comparable. Transactions cover the last year, plus a
        // block inside the current month because the Transactions tab defaults to it.
        val random = Random(42)
        val monthStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
        val minutesThisMonth = java.time.Duration.between(monthStart, now).toMinutes().coerceAtLeast(1)
        val rows = (0 until total + thisMonth).map { i ->
            val dateTime = if (i < thisMonth) {
                monthStart.plusMinutes(random.nextLong(minutesThisMonth))
            } else {
                now.minusMinutes(random.nextLong(365L * 24 * 60))
            }
            val income = random.nextInt(10) == 0
            val account = ACCOUNTS[random.nextInt(ACCOUNTS.size)]
            TransactionEntity(
                amount = BigDecimal(random.nextDouble(5.0, if (income) 20_000.0 else 800.0))
                    .setScale(2, RoundingMode.HALF_UP),
                merchantName = MERCHANTS[random.nextInt(MERCHANTS.size)],
                category = if (income) incomeCategories.random(random) else expenseCategories.random(random),
                transactionType = if (income) TransactionType.INCOME else TransactionType.EXPENSE,
                dateTime = dateTime,
                bankName = account.first,
                accountNumber = account.second,
                transactionHash = "benchmark-seed-$i",
                currency = CURRENCY,
                createdAt = dateTime,
                updatedAt = dateTime,
            )
        }
        rows.chunked(500).forEach { transactionDao.insertTransactions(it) }

        getSharedPreferences("account_prefs", Context.MODE_PRIVATE).edit {
            putString("main_account", "${ACCOUNTS[0].first}_${ACCOUNTS[0].second}")
        }
        userPreferences.updateBaseCurrency(CURRENCY)
        userPreferences.markScanTutorialShown()
        marker.edit(commit = true) { putBoolean(MARKER_KEY, true) }

        Log.i(TAG, "Seeded ${rows.size} transactions in ${(System.nanoTime() - started) / 1_000_000} ms")
    }

    private companion object {
        const val TAG = "HootbookBenchmarkSeed"
        const val EXTRA_TRANSACTIONS = "transactions"
        const val EXTRA_THIS_MONTH = "thisMonth"
        const val MARKER_PREFS = "benchmark_seed"
        const val MARKER_KEY = "seeded"

        // One currency everywhere, so Home never needs an exchange-rate request.
        const val CURRENCY = "CNY"

        val ACCOUNTS = listOf(
            Triple("China Merchants Bank", "1001", false),
            Triple("ICBC", "2002", false),
            Triple("Alipay", "3003", false),
            Triple("CMB Credit Card", "4004", true),
        )

        val MERCHANTS = listOf(
            "Meituan", "Ele.me", "Starbucks", "Luckin Coffee", "FamilyMart", "Hema",
            "JD.com", "Taobao", "Pinduoduo", "Didi", "Metro", "China Railway",
            "Wanda Cinema", "Haidilao", "Xiaomi Store", "Apple", "Uniqlo", "IKEA",
            "China Mobile", "State Grid", "Pharmacy", "Gym", "Bookstore", "Salary",
        )
    }
}
