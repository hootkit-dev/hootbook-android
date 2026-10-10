package io.github.hootkit_dev.hootbook.tools

import com.google.gson.GsonBuilder
import io.github.hootkit_dev.hootbook.data.backup.AppPreferences
import io.github.hootkit_dev.hootbook.data.backup.BackupMetadata
import io.github.hootkit_dev.hootbook.data.backup.BackupStatistics
import io.github.hootkit_dev.hootbook.data.backup.BigDecimalTypeAdapter
import io.github.hootkit_dev.hootbook.data.backup.HootbookBackup
import io.github.hootkit_dev.hootbook.data.backup.DatabaseSnapshot
import io.github.hootkit_dev.hootbook.data.backup.DateRange
import io.github.hootkit_dev.hootbook.data.backup.DeveloperPreferences
import io.github.hootkit_dev.hootbook.data.backup.LocalDateTimeTypeAdapter
import io.github.hootkit_dev.hootbook.data.backup.LocalDateTypeAdapter
import io.github.hootkit_dev.hootbook.data.backup.PreferencesSnapshot
import io.github.hootkit_dev.hootbook.data.backup.ThemePreferences
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionType
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Writes an importable backup with the same data set as the benchmark SeedActivity
 * (4 CNY accounts, ~3,400 transactions, 400 in the current month), for testing
 * performance on a real device. Skipped unless HOOTBOOK_BENCHMARK_BACKUP names the output:
 *
 * HOOTBOOK_BENCHMARK_BACKUP=/tmp/hootbook-benchmark-backup.zip \
 *     ./gradlew :app:testStandardDebugUnitTest --tests '*BenchmarkBackupGenerator*'
 */
class BenchmarkBackupGenerator {

    @Test
    fun writeBackup() {
        val out = System.getenv("HOOTBOOK_BENCHMARK_BACKUP")
        assumeTrue("HOOTBOOK_BENCHMARK_BACKUP not set", !out.isNullOrBlank())

        val now = LocalDateTime.now().withNano(0)
        val accounts = ACCOUNTS.mapIndexed { i, (name, last4, isCreditCard) ->
            AccountBalanceEntity(
                bankName = name,
                accountLast4 = last4,
                balance = BigDecimal(50_000 + i * 12_345),
                timestamp = now.minusDays(400),
                sourceType = "MANUAL",
                currency = CURRENCY,
                isCreditCard = isCreditCard,
                createdAt = now,
            )
        }

        val random = Random(42)
        val monthStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0)
        val minutesThisMonth = Duration.between(monthStart, now).toMinutes().coerceAtLeast(1)
        val transactions = (0 until TOTAL + THIS_MONTH).map { i ->
            val dateTime = if (i < THIS_MONTH) {
                monthStart.plusMinutes(random.nextLong(minutesThisMonth))
            } else {
                now.minusMinutes(random.nextLong(365L * 24 * 60))
            }
            val income = random.nextInt(10) == 0
            val (bank, last4, _) = ACCOUNTS[random.nextInt(ACCOUNTS.size)]
            TransactionEntity(
                id = i + 1L,
                amount = BigDecimal(random.nextDouble(5.0, if (income) 20_000.0 else 800.0))
                    .setScale(2, RoundingMode.HALF_UP),
                merchantName = MERCHANTS[random.nextInt(MERCHANTS.size)],
                category = if (income) "Income" else EXPENSE_CATEGORIES.random(random),
                transactionType = if (income) TransactionType.INCOME else TransactionType.EXPENSE,
                dateTime = dateTime,
                bankName = bank,
                accountNumber = last4,
                transactionHash = "benchmark-seed-$i",
                currency = CURRENCY,
                createdAt = dateTime,
                updatedAt = dateTime,
            )
        }
        val sorted = transactions.sortedBy { it.dateTime }

        val backup = HootbookBackup(
            created = now.toString(),
            metadata = BackupMetadata(
                exportId = "benchmark-seed",
                appVersion = "benchmark",
                databaseVersion = 57, // what BackupExporter writes
                device = "BenchmarkBackupGenerator",
                androidVersion = 0,
                statistics = BackupStatistics(
                    totalTransactions = transactions.size,
                    totalCategories = 0,
                    totalCards = 0,
                    totalSubscriptions = 0,
                    dateRange = DateRange(sorted.first().dateTime.toString(), sorted.last().dateTime.toString()),
                ),
            ),
            database = DatabaseSnapshot(
                transactions = transactions,
                // Categories are matched by name against the defaults every install creates.
                categories = emptyList(),
                cards = emptyList(),
                accountBalances = accounts,
                subscriptions = emptyList(),
            ),
            preferences = PreferencesSnapshot(
                theme = ThemePreferences(isDarkThemeEnabled = null, isDynamicColorEnabled = true),
                developer = DeveloperPreferences(isDeveloperModeEnabled = false, systemPrompt = null),
                app = AppPreferences(
                    hasShownScanTutorial = true,
                    firstLaunchTime = null,
                    hasShownReviewPrompt = true,
                    lastReviewPromptTime = null,
                ),
            ),
        )

        // Same Gson setup as BackupExporter.
        val json = GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
            .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
            .registerTypeAdapter(BigDecimal::class.java, BigDecimalTypeAdapter())
            .create()
            .toJson(backup)
        ZipOutputStream(File(out).outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(json.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        println("Wrote ${transactions.size} transactions to $out")
    }

    private companion object {
        const val TOTAL = 3000
        const val THIS_MONTH = 400
        const val CURRENCY = "CNY"

        val ACCOUNTS = listOf(
            Triple("China Merchants Bank", "1001", false),
            Triple("ICBC", "2002", false),
            Triple("Alipay", "3003", false),
            Triple("CMB Credit Card", "4004", true),
        )

        val EXPENSE_CATEGORIES = listOf(
            "Food & Drinks", "Transport", "Shopping", "Groceries", "Home", "Entertainment",
            "Travel", "Medical", "Personal", "Fitness", "Bill", "Subscription",
        )

        val MERCHANTS = listOf(
            "Meituan", "Ele.me", "Starbucks", "Luckin Coffee", "FamilyMart", "Hema",
            "JD.com", "Taobao", "Pinduoduo", "Didi", "Metro", "China Railway",
            "Wanda Cinema", "Haidilao", "Xiaomi Store", "Apple", "Uniqlo", "IKEA",
            "China Mobile", "State Grid", "Pharmacy", "Gym", "Bookstore", "Salary",
        )
    }
}
