package io.github.hootkit_dev.hootbook.benchmark

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.hootkit.core.*
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.BuildConfig
import io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferencesStore
import java.math.BigDecimal
import java.time.LocalDateTime as JavaDateTime
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Benchmark-only isolated fixture. No Room/SQL/direct balance writes or user database access. */
@AndroidEntryPoint
class SeedActivity : ComponentActivity() {
    @Inject lateinit var session: CoreSession
    @Inject lateinit var uiPreferences: CoreUiPreferencesStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(BuildConfig.APPLICATION_ID.startsWith("io.github.hootkit_dev.hootbook.benchmark")) {
            "Benchmark fixture is restricted to an isolated benchmark applicationId"
        }
        val total = intent.getIntExtra("transactions", 3000).also { require(it in 0..100_000) }
        val thisMonth = intent.getIntExtra("thisMonth", 400).also { require(it in 0..100_000) }
        java.io.File(getExternalFilesDir(null), "benchmark-seeded").delete()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                seed(total, thisMonth)
                java.io.File(getExternalFilesDir(null), "benchmark-seeded").writeText("ok:core-v1")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                java.io.File(getExternalFilesDir(null), "benchmark-seed-failed").writeText("core seed failed")
                Log.e("HootbookBenchmarkSeed", "Core fixture seeding failed")
            } finally { finish() }
        }
    }

    private suspend fun seed(total: Int, thisMonth: Int) {
        val marker = getSharedPreferences("benchmark_seed", Context.MODE_PRIVATE)
        if (marker.getBoolean("core-seeded-v1", false)) return
        val core = session.awaitCore()
        check(core.accounts().use { it.overview().accounts.isEmpty() }) {
            "Benchmark fixture must start from empty isolated Core data; clear benchmark flavor data after an interrupted seed"
        }
        core.settings().use { it.setMainCurrency("CNY") }
        val now = JavaDateTime.now().withNano(0)
        val accountIds = core.accounts().use { accounts ->
            listOf(
                Triple("Benchmark CMB", "1001", AccountKind.BANK),
                Triple("Benchmark ICBC", "2002", AccountKind.BANK),
                Triple("Benchmark Wallet", null, AccountKind.WALLET),
                Triple("Benchmark Card", "4004", AccountKind.CREDIT_CARD),
            ).mapIndexed { index, row -> accounts.create(NewAccount(row.first, row.second, row.third, "CNY",
                BigDecimal.valueOf(50_000L + index * 12_345L), now.minusDays(400).toCore(),
                if (row.third == AccountKind.CREDIT_CARD) BigDecimal("100000") else null,
                null, null, null, null, null, emptyList())) }
        }
        core.settings().use { it.setDefaultAccount(accountIds.first()) }
        val categories = core.categories().use { it.list(null, true) }
        val expense = categories.filter { it.kind == CategoryKind.EXPENSE && !it.hidden }.map { it.id }
        val income = categories.filter { it.kind == CategoryKind.INCOME && !it.hidden }.map { it.id }
        check(expense.isNotEmpty() && income.isNotEmpty())
        val random = Random(42)
        val monthStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0)
        val minutesThisMonth = java.time.Duration.between(monthStart, now).toMinutes().coerceAtLeast(1)
        core.ledger().use { ledger ->
            repeat(total + thisMonth) { index ->
                val date = if (index < thisMonth) monthStart.plusMinutes(random.nextLong(minutesThisMonth))
                    else now.minusMinutes(random.nextLong(365L * 24 * 60))
                val isIncome = random.nextInt(10) == 0
                // Synthetic fixture generation only; never application financial calculation.
                val cents = random.nextLong(500, if (isIncome) 2_000_000 else 80_000)
                ledger.add(TransactionDraft(if (isIncome) TransactionKind.INCOME else TransactionKind.EXPENSE,
                    BigDecimal.valueOf(cents, 2), "CNY", date.toCore(), accountIds.random(random),
                    null, null, null, (if (isIncome) income else expense).random(random), null,
                    listOf("Fixture grocery", "Fixture transport", "Fixture salary", "Fixture subscription").random(random),
                    "benchmark-fixture-$index", null, emptyList()))
            }
        }
        uiPreferences.update { it.copy(onboardingFinished = true, notificationsEnabled = false, scanReminderEnabled = false) }
        check(marker.edit().putBoolean("core-seeded-v1", true).commit())
    }
}

private fun JavaDateTime.toCore() = LocalDateTime(year, monthValue.toUByte(), dayOfMonth.toUByte(),
    hour.toUByte(), minute.toUByte(), second.toUByte(), nano.toUInt())
